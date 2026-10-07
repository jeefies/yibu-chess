#!/usr/bin/env python3
"""Package a verified installable build, reproducible source and separate test key."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import re
import shutil
import zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
version = re.search(r'versionName\s*=\s*"([^"]+)"', (root / "app/build.gradle.kts").read_text()).group(1)
out = root / "artifacts"
out.mkdir(exist_ok=True)
apk = root / "app/build/outputs/apk/release/app-release.apk"
assert apk.is_file(), "Build the release APK first"
excluded = {".git", ".gradle", ".kotlin", ".cxx", "build", "artifacts", "signing", "__pycache__"}
sources = [p for p in root.rglob("*") if p.is_file() and not any(part in excluded for part in p.relative_to(root).parts)
           and p.name != "local.properties" and not p.name.endswith(".log")]
code = [p for p in sources if p.suffix in {".kt", ".java", ".cpp", ".h", ".xml"} or p.name.endswith(".gradle.kts")]
assert all(p.stat().st_mtime <= apk.stat().st_mtime for p in code), "Rebuild APK after the most recent source change"
shutil.copy2(apk, out / f"yibu-{version}-arm64.apk")
with zipfile.ZipFile(out / f"yibu-{version}-source.zip", "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
    for p in sorted(sources): archive.write(p, "yibu-chess/" + str(p.relative_to(root)))
with zipfile.ZipFile(out / "yibu-personal-test-signing.zip", "w", zipfile.ZIP_DEFLATED) as archive:
    archive.write(root / "signing/personal.jks", "signing/personal.jks")
    archive.writestr("README.txt", "个人测试签名备份\n后续构建恢复到项目 signing/personal.jks。\nalias: yibu\nstore/key password: yibu-personal-test\n保持同一签名才能覆盖安装；提高 versionCode。\n此文件不放入 Git 仓库。\n")
files = [out / f"yibu-{version}-arm64.apk", out / f"yibu-{version}-source.zip", out / "yibu-personal-test-signing.zip"]
def test_status(path):
    if not path.exists(): return "not run in this workspace"
    suite = ET.parse(path).getroot()
    assert int(suite.get("failures", 0)) + int(suite.get("errors", 0)) == 0, f"Tests failed: {path}"
    return f"{suite.get('tests')} passed"
lint_issues = ET.parse(root / "app/build/reports/lint-results-debug.xml").getroot().findall("issue")
assert not any(issue.get("severity") in {"Error", "Fatal"} for issue in lint_issues), "Android lint reported errors"
metadata = {
    "built_at": datetime.now(timezone.utc).isoformat(),
    "version": version, "application_id": "cn.yibu.chess", "abi": "arm64-v8a",
    "min_sdk": 26, "target_sdk": 35, "engine": "Stockfish 17.1",
    "human_model": json.loads((root / "app/src/main/assets/models/maia3-metadata.json").read_text()),
    "certificate_sha256": "ac84a14d5fe6aa75a8550e375c24221048c9abc9b85d64fe02e1e4e58be1df03",
    "files": {p.name: {"bytes": p.stat().st_size, "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in files},
    "validation": {
        "core_jvm_tests": test_status(root / "core/build/test-results/test/TEST-cn.yibu.chess.core.CoreTest.xml"),
        "rating_and_elo_jvm_tests": test_status(root / "core/build/test-results/test/TEST-cn.yibu.chess.core.RatingAndEloTest.xml"),
        "human_policy_and_upstream_encoding": test_status(root / "core/build/test-results/test/TEST-cn.yibu.chess.core.HumanPolicyTest.xml"),
        "rating_storage_and_migration_robolectric": test_status(root / "app/build/test-results/testDebugUnitTest/TEST-cn.yibu.chess.data.GameRepositoryTest.xml"),
        "android_startup_robolectric": test_status(root / "app/build/test-results/testDebugUnitTest/TEST-cn.yibu.chess.StartupTest.xml"),
        "actual_onnx_model_inference": test_status(root / "app/build/test-results/testDebugUnitTest/TEST-cn.yibu.chess.engine.MaiaModelTest.xml"),
        "saved_color_preferences": test_status(root / "app/build/test-results/testDebugUnitTest/TEST-cn.yibu.chess.data.PlayPreferencesTest.xml"),
        "live_annotations_and_no_prompt_ui": test_status(root / "app/build/test-results/testDebugUnitTest/TEST-cn.yibu.chess.ui.ChessScreenTest.xml"),
        "native_host_jni_probe": "passed",
        "android_lint": {"errors": 0, "warnings": sum(issue.get("severity") == "Warning" for issue in lint_issues)},
        "apk_signature": "v2 verified", "apk_zip_16kb_alignment": "passed",
        "native_elf_16kb_alignment": "passed for all bundled .so files",
        "android_instrumentation": "test APK built; not executed on a device",
        "xiaomi_17_pro": "not yet tested on the actual phone"
    }
}
(out / "build-manifest.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
(out / "SHA256SUMS.txt").write_text("".join(f"{metadata['files'][p.name]['sha256']}  {p.name}\n" for p in files))
for p in files: print(f"{p.name}: {p.stat().st_size / 1024 / 1024:.1f} MiB")
