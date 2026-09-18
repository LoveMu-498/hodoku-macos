# 第三方许可与源码提供

整理日期：2026-09-18。此清单解释已识别组件；不把不同组件改成同一许可证。

| 组件 | 声明与处理 |
| --- | --- |
| HoDoKu / PseudoFish 衍生应用 | GPL-3.0-or-later；完整 GPLv3 文本在根目录 COPYING，原版权及贡献说明保留 |
| FileDrop | `src/sudoku/FileDrop.java` 保留 Robert Harder 的 Public Domain 声明和后续作者说明 |
| Fowler 求解代码来源 | `src/generator/SudokuGenerator.java` 的现有来源/许可使用说明保留 |
| Eclipse Temurin / OpenJDK | GPLv2 with Classpath Exception，另含独立第三方许可；实际 app 的 `Contents/runtime/Contents/Home/legal/` 保留原始法律文件 |
| Java 内含 FreeType、HarfBuzz、PNG/JPEG、LCMS 等 | 以 runtime legal 下实际 notices 为准；不因为随 Java 打包就统一改成 GPLv3 |
| macOS 系统音效 | 运行时读取 `/System/Library/Sounds`，源码仓库与分发包不复制这些系统音效 |

依据：[Adoptium FAQ](https://adoptium.net/docs/faq)、[GPLv3](https://www.gnu.org/licenses/gpl-3.0.html)。
Classpath Exception 不免除分发 Java 本身时的许可和对应源码义务。

## Java 材料与仓库分离

普通 Git 仓库不纳入 Java 大文件，只保存版本、校验和、下载/组包脚本及说明。
本地完整分享包将 app（内置 Java）、应用源码、匹配的 JDK 源码/构建脚本材料放在一起。
GitHub 发布含 Java 的 DMG 时，Java 源码材料可以作为同次 Release 的单独附件，下载入口应
明确说明匹配版本和附件名；不能只上传 DMG 或只放上游首页链接。

官方版本入口：[Temurin 21.0.12.1+1](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1)。
组包时应验证官方二进制与源码元数据中的版本/SCM、二进制校验值，以及对应构建脚本 commit。
完整源码归档应含 HotSpot 原生源码、Java 源码和构建文件，不能用 JDK `lib/src.zip` 代替。
保存官方声明不等于完成所有权利审查；来源和署名见 UPSTREAM.md。
