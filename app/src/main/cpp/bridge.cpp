#include <jni.h>
#include <atomic>
#include <map>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>
#include "bitboard.h"
#include "engine.h"
#include "position.h"
#include "uci.h"

namespace {
std::unique_ptr<Stockfish::Engine> owned;
std::atomic<Stockfish::Engine*> active{nullptr};
std::mutex access;
std::string utf(JNIEnv* env, jstring value) {
    if (!value) return "";
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
void option(const std::string& name, const std::string& value) {
    std::istringstream input("name " + name + " value " + value);
    owned->get_options().setoption(input);
}
std::vector<std::string> words(const std::string& text) {
    std::vector<std::string> result;
    std::istringstream in(text);
    for (std::string s; in >> s;) result.push_back(s);
    return result;
}
void fail(JNIEnv* env, const std::exception& e) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
}
}

extern "C" JNIEXPORT void JNICALL
Java_cn_yibu_chess_engine_NativeBridge_initialize(JNIEnv* env, jclass, jstring directory) {
    std::lock_guard<std::mutex> lock(access);
    if (owned) return;
    try {
        Stockfish::Bitboards::init();
        Stockfish::Position::init();
        const auto dir = utf(env, directory);
        owned = std::make_unique<Stockfish::Engine>(dir + "/stockfish");
        option("EvalFile", dir + "/nn-1c0000000000.nnue");
        option("EvalFileSmall", dir + "/nn-37f18f62d772.nnue");
        option("NumaPolicy", "none");
        option("UCI_ShowWDL", "true");
        option("UCI_LimitStrength", "false");
        active.store(owned.get());
    } catch (const std::exception& e) { owned.reset(); fail(env, e); }
}

extern "C" JNIEXPORT jstring JNICALL
Java_cn_yibu_chess_engine_NativeBridge_search(JNIEnv* env, jclass, jstring history,
        jint timeMs, jint depth, jint multiPV, jint skill, jint threads, jint hash, jstring restricted) {
    std::lock_guard<std::mutex> lock(access);
    try {
        if (!owned) throw std::runtime_error("Stockfish 尚未初始化");
        option("Threads", std::to_string(threads));
        option("Hash", std::to_string(hash));
        option("MultiPV", std::to_string(multiPV));
        option("Skill Level", std::to_string(skill));
        option("UCI_LimitStrength", "false");
        owned->search_clear();
        owned->set_position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", words(utf(env, history)));
        std::map<int, std::map<int, std::string>> snapshots;
        std::string best;
        owned->set_on_update_full([&](const auto& info) {
            if (!info.bound.empty()) return; // never grade a score bound as an exact evaluation
            std::ostringstream line;
            line << "info depth " << info.depth << " multipv " << info.multiPV
                 << " score " << Stockfish::UCIEngine::format_score(info.score)
                 << " wdl " << info.wdl << " nodes " << info.nodes << " pv " << info.pv;
            snapshots[info.depth][info.multiPV] = line.str();
            while (snapshots.size() > 3) snapshots.erase(snapshots.begin());
        });
        owned->set_on_bestmove([&](std::string_view move, std::string_view) { best = move; });
        Stockfish::Search::LimitsType limits;
        limits.startTime = Stockfish::now();
        limits.movetime = timeMs;
        if (depth > 0) limits.depth = depth;
        limits.searchmoves = words(utf(env, restricted));
        owned->go(limits);
        owned->wait_for_search_finished();
        std::ostringstream output;
        for (const auto& [d, rows] : snapshots)
            for (const auto& [pv, line] : rows) output << line << '\n';
        output << "bestmove " << best << '\n';
        // callbacks must never retain references to stack data after this request
        owned->set_on_update_full([](const auto&) {});
        owned->set_on_bestmove([](std::string_view, std::string_view) {});
        return env->NewStringUTF(output.str().c_str());
    } catch (const std::exception& e) { fail(env, e); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_cn_yibu_chess_engine_NativeBridge_stop(JNIEnv*, jclass) {
    if (auto* engine = active.load()) engine->stop();
}
