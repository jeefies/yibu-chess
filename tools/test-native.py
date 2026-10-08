#!/usr/bin/env python3
"""Exercise the exact JNI bridge on the host JVM; Android ABI is built separately."""
import os
from pathlib import Path
import subprocess
import argparse

parser = argparse.ArgumentParser()
parser.add_argument("--review-performance", action="store_true", help="Run only the changed review-resource/cache checks")
args = parser.parse_args()

root = Path(__file__).resolve().parent.parent
sf = root / "vendor/stockfish/src"
out = root / "artifacts/native-probe"
out.mkdir(parents=True, exist_ok=True)
jdk = Path(os.environ.get("JAVA_HOME", "/workspace/android-toolchain/jdk17"))
sources = sorted(p for p in sf.rglob("*.cpp") if p.name != "main.cpp")
lib = out / "libyibu_stockfish_host.so"
subprocess.run(["g++", "-O2", "-std=c++17", "-pthread", "-fPIC", "-shared",
    "-DNNUE_EMBEDDING_OFF", "-DNDEBUG", "-DUSE_SSE2", "-DIS_64BIT",
    f"-I{sf}", f"-I{jdk / 'include'}", f"-I{jdk / 'include/linux'}",
    str(root / "app/src/main/cpp/bridge.cpp"), *map(str, sources), "-o", str(lib)], check=True)
subprocess.run([str(jdk / "bin/javac"), "-d", str(out), str(root / "tools/native-probe/NativeBridge.java")], check=True)
subprocess.run([str(jdk / "bin/java"), f"-Dprobe.library={lib}", "-cp", str(out),
    "cn.yibu.chess.engine.NativeBridge", str(root / "app/src/main/assets/networks"),
    *(["--review-performance"] if args.review_performance else [])], check=True)
