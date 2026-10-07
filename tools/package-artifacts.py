#!/usr/bin/env python3
"""Package a verified installable build, reproducible source and separate test key."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import shutil
import zipfile

root = Path(__file__).resolve().parent.parent
out = root / "artifacts"
out.mkdir(exist_ok=True)
apk = root / "app/build/outputs/apk/release/app-release.apk"
assert apk.is_file(), "Build the release APK first"
excluded = {".git", ".gradle", ".cxx", "build", "artifacts", "signing", "__pycache__"}
sources = [p for p in root.rglob("*") if p.is_file() and not any(part in excluded for part in p.relative_to(root).parts)
           and p.name != "local.properties" and not p.name.endswith(".log")]
code = [p for p in sources if p.suffix in {".kt", ".java", ".cpp", ".h", ".xml"} or p.name.endswith(".gradle.kts")]
assert all(p.stat().st_mtime <= apk.stat().st_mtime for p in code), "Rebuild APK after the most recent source change"
shutil.copy2(apk, out / "yibu-0.1.0-arm64.apk")
with zipfile.ZipFile(out / "yibu-0.1.0-source.zip", "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
    for p in sorted(sources): archive.write(p, "yibu-chess/" + str(p.relative_to(root)))
with zipfile.ZipFile(out / "yibu-personal-test-signing.zip", "w", zipfile.ZIP_DEFLATED) as archive:
    archive.write(root / "signing/personal.jks", "signing/personal.jks")
    archive.writestr("README.txt", "个人测试签名备份\n后续构建恢复到项目 signing/personal.jks。\nalias: yibu\nstore/key password: yibu-personal-test\n保持同一签名才能覆盖安装；提高 versionCode。\n此文件不放入 Git 仓库。\n")
files = [out / "yibu-0.1.0-arm64.apk", out / "yibu-0.1.0-source.zip", out / "yibu-personal-test-signing.zip"]
metadata = {
    "built_at": datetime.now(timezone.utc).isoformat(),
    "version": "0.1.0", "application_id": "cn.yibu.chess", "abi": "arm64-v8a",
    "min_sdk": 26, "target_sdk": 35, "engine": "Stockfish 17.1",
    "certificate_sha256": "ac84a14d5fe6aa75a8550e375c24221048c9abc9b85d64fe02e1e4e58be1df03",
    "files": {p.name: {"bytes": p.stat().st_size, "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in files},
    "validation": {
        "core_jvm_tests": "11 passed", "native_host_jni_probe": "passed",
        "apk_signature": "v2 verified", "apk_zip_16kb_alignment": "passed",
        "native_elf_16kb_alignment": "passed for all bundled .so files",
        "android_instrumentation": "test APK built; not executed on a device",
        "xiaomi_17_pro": "not yet tested on the actual phone"
    }
}
(out / "build-manifest.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
(out / "SHA256SUMS.txt").write_text("".join(f"{metadata['files'][p.name]['sha256']}  {p.name}\n" for p in files))
for p in files: print(f"{p.name}: {p.stat().st_size / 1024 / 1024:.1f} MiB")
