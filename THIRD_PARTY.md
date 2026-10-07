# 开源组件与版本

应用源码使用 GPL-3.0-or-later。仅个人自用无需公开源码；分发时需要遵守相关许可证。

## Stockfish

- 上游：https://github.com/official-stockfish/Stockfish
- 版本：17.1，标签 sf_17.1
- 提交：03e27488f3d21d8ff4dbf3065603afa21dbd0ef3
- 许可证：GPL-3.0-or-later，全文见 vendor/stockfish/Copying.txt。
- vendor/stockfish 保留完整上游源码。应用自有 JNI 包装在 app/src/main/cpp/bridge.cpp。
- NNUE 不编译进 .so，由 assets/networks 打包并复制到应用私有目录；native 使用 NNUE_EMBEDDING_OFF。
- 本地权重：nn-1c0000000000.nnue、nn-37f18f62d772.nnue，从官方 Fishtest 下载。
- SHA-256：
  - 1c0000000000a67d629999d932d0c373f7450ce43cd12d0562868f4eaf9ae2ad
  - 37f18f62d772f3107e1d6aaca3898c130c3c86f2ab63e6555fbbca20635a899d

## chesslib

- 上游：https://github.com/bhlangonijr/chesslib
- 版本：1.3.7
- 提交：2e9b4e1a71a84d4fe147e762cf39c82a8cd4c251
- 许可证：Apache-2.0，全文见 vendor/chesslib/LICENSE。
- 上游源码未修改。应用单独实现保守的死局子力判断，避免把“不能强制将杀”误当作“不可能将杀”。

## 其他依赖

- AndroidX Compose / Activity / Lifecycle / Room：Apache-2.0
- Apache Commons Lang 3.18.0：Apache-2.0
- Kotlin / kotlinx.coroutines / kotlinx.serialization：Apache-2.0
- Gradle Wrapper：Apache-2.0
- 原生 C++ 标准库：Android NDK libc++，Apache-2.0 WITH LLVM-exception。

精确版本由 Gradle 文件固定。测试签名文件、构建缓存和本机 SDK 不属于源码包。
