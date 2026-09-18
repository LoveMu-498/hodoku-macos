# macOS 独立分享包

运行 `bash script/package_share.sh`。脚本在项目 `build/toolchains` 缓存官方 Temurin
21.0.12.1+1 ARM64 压缩包并校验固定 SHA-256，不安装系统 Java，不修改系统 Java 选择。
需要 macOS Apple Silicon、Python 3.9+、Xcode Command Line Tools，以及可用的桌面会话。
首次下载约 200 MB。官方发行入口：https://adoptium.net/installation

输出在独立的 `dist/share-时间-进程号/`，包含 DMG、SHA256SUMS.txt 和 reports。
每次构建对应当前工作区，包括未提交的源码；不会替换 `/Applications/HoDoKu.app`。
DMG 包含应用、Applications 入口、中文安装说明、GPL 许可和对应源码 tar.gz。
默认还包含 `Java对应源码/`：固定版本的完整 OpenJDK 源码、对应 Temurin 构建脚本、
官方二进制/源码元数据、许可与校验值。首次额外下载约 110 MiB，材料缓存在 build 下。

## GitHub 与本地分开准备

- `python3 script/prepare_github_source.py`：导出可审阅的源码候选归档；没有 Java 大文件、
  app/DMG、备份或 Git 历史，不会提交/上传。不是完整凭据审计或自动许可放行。
- `bash script/package_share.sh --local`（默认）：本地完整 DMG，Java 源码材料也在里面。
- `bash script/package_share.sh --github-release`：准备 DMG、HoDoKu 源码归档、单独的 Java
  对应源码附件、SHA256SUMS 和 Release 草稿；从不上传。DMG 中保留 Java 运行时，
  并写明对应源码附件名，实际发布须保持这些附件同时可取得。

组包会核对 Java 二进制与源码的官方版本/SCM/构建 commit 及固定 SHA-256，检查归档包含
HotSpot、Java 类与构建文件。当前材料只匹配固定的 Temurin 21.0.12.1+1 macOS aarch64；
换 JDK 必须更新并核对源码材料，不能绕过失败。未独立重建上游 Java，不宣称法律审查覆盖全部权利。
完整公开约定见 [发布约定](../open-source-release.md)，来源说明见 [UPSTREAM](../../UPSTREAM.md)。

也可提供已验证的自包含 JDK：

```sh
HODOKU_JAVA_HOME=/path/to/jdk/Contents/Home bash script/package_share.sh
```

普通构建可用同一 JDK：

```sh
HODOKU_JAVA_HOME="$(bash script/prepare_distribution_jdk.sh)" bash script/build_and_run.sh --build-only
```

所有构建在打包前扫描 JDK、完成后扫描整个 app。检查 Mach-O 架构、非系统绝对
依赖、RPATH、相对链接目标、软链接、最低系统声明、关键资源、个人配置混入、
签名完整性。JVM 动态模块的相对链接检查包含 launcher/libjli/libjvm 的继承搜索路径；
静态扫描不能替代动态加载验证。Java legal 目录由 jlink 保留。
保留 runtime 的 bin/java，便于诊断；接收者不需要安装 Java。

分享脚本挂载最终 DMG，复制到含空格和中文的新路径，再次验签和审计，并以
隔离的 home/data/tmp、只有系统工具的 PATH 启动实际 app，检查 CLI 求解、
Aqua/字体加载、原生 Quit 保存及重新启动。测试不接触用户现有设置与会话。
仅改变 PATH 并不等于移除本机 Homebrew；完整性结论同时依赖 Mach-O 静态检查。
源码行为测试继续由原构建脚本执行，完整交互回归仍可用 `--verify` 单独运行。

该流程目前产出 Apple Silicon 包，不声称支持 Intel。安装说明的 macOS 11 下限
必须与 audit 报告的 minimum_macos 一致；升级 JDK 后如变更，应同步调整说明。
尚未进行 Developer ID 签名或 Apple 公证，不保证下载后的首次启动没有系统提示。
签名完整性与 Gatekeeper 信任是不同检查。实际 M1 和旧 macOS 验收需接收者测试。
