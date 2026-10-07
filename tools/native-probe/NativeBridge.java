package cn.yibu.chess.engine;

public final class NativeBridge {
    static { System.load(System.getProperty("probe.library")); }
    public static native void initialize(String directory);
    public static native String search(String history, int ms, int depth, int multiPv, int skill, int threads, int hash, String restricted);
    public static native void stop();
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        initialize(args[0]);
        String root = search("", 500, 10, 3, 20, 1, 64, "");
        check(root.contains("multipv 3") && root.contains("wdl "), "missing MultiPV or WDL");
        check(root.matches("(?s).*bestmove [a-h][1-8][a-h][1-8].*"), "missing legal bestmove");
        String forced = search("", 500, 10, 1, 20, 1, 64, "a2a3");
        check(forced.contains("bestmove a2a3"), "searchmoves not enforced");
        String black = search("e2e4", 300, 9, 1, 20, 2, 128, "");
        check(black.contains("wdl ") && !black.contains("bestmove e2e4"), "black history not applied");
        String castle = search("e2e4 e7e5 g1f3 b8c6 f1c4 g8f6", 250, 8, 1, 20, 1, 64, "e1g1");
        check(castle.contains("bestmove e1g1"), "castling request failed");
        String ep = search("e2e4 a7a6 e4e5 d7d5", 250, 8, 1, 20, 1, 64, "e5d6");
        check(ep.contains("bestmove e5d6"), "en passant request failed");
        long start = System.nanoTime();
        Thread computation = new Thread(() -> search("", 10000, 0, 3, 20, 2, 128, ""));
        computation.start();
        Thread.sleep(200);
        stop();
        computation.join(2500);
        check(!computation.isAlive(), "native cancellation timed out");
        check((System.nanoTime() - start) < 3_000_000_000L, "slow native stop");
        String resumed = search("e2e4 e7e5", 200, 8, 1, 0, 1, 64, "");
        check(resumed.contains("bestmove "), "engine could not resume after stop");
        System.out.println("Native JNI probe passed: offline NNUE, MultiPV/WDL, restricted move, history, castling, en passant, two threads, cancellation and resume.");
        System.out.println(root.lines().filter(s -> s.startsWith("bestmove")).findFirst().orElse(""));
    }
}
