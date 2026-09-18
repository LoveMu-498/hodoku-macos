# 来源、归属与维护关系

核查/整理日期：2026-09-18。本项目为非官方 macOS 修改版，不表示原作者或其他维护者背书。

| 来源 | 本项目关系 | 许可/归属依据 |
| --- | --- | --- |
| [原版 HoDoKu](https://hodoku.sourceforge.net/en/index.php) | 基础求解器、生成器、界面及资源 | Bernhard Hobiger；官网声明 GPLv3，继承源码头声明第 3 版或后续版本 |
| [PseudoFish/Hodoku](https://github.com/PseudoFish/Hodoku) | 本地 2.3.0 基线继承此分支的修改；不是全部由本项目原创 | Main.java 等保留 PseudoFish 2019–20 及原作者声明，GPL-3.0-or-later |
| 本项目 macOS 修改 | 中文与原生操作、持久化、标注与画链、打包修复等 | 修改范围与日期见 CHANGES.md；继承声明保留，新增文件按其实际声明处理 |
| [wyzelli/Hodoku2](https://github.com/wyzelli/Hodoku2) | 2026-09-18 开始下载研究；没有因此自动成为本项目代码依赖 | 后续引用/移植需记录具体文件、固定 commit 和对应许可，不能凭项目级徽章替代逐项检查 |

## 具体署名

- 保留 Bernhard Hobiger、PseudoFish 的既有版权声明。
- 保留程序关于窗口已有的贡献者信息，包括 ddyer、ccv、Glen/Glenn Fowler、Robert Harder。
- `src/generator/SudokuGenerator.java` 已注明 Glenn Fowler 的代码来源及作者许可使用说明；保留原文，不扩大为对其他来源的授权。
- `src/sudoku/FileDrop.java` 声明由 Robert Harder 释放到 Public Domain，并记录 Nathan Blomquist 的修改；保留这些声明。
- 本项目维护者身份及后续贡献者署名按实际贡献补充，不把全部继承代码改成维护者个人版权。

## 材料边界

官网软件为 GPLv3，但[网页页脚](https://hodoku.sourceforge.net/en/index.php)对网站材料声明 GNU FDLv1.3。
今后复制教程文本或网页图片，先检查材料自身许可；优先链接原文，不能直接统一标成软件 GPL。
本地 `src/help/keyboard*.html` 已有软件 GPL 声明，应保留。已检查的 70 个 `src/img` 文件与
本地 2.3.0 基线逐字节一致；这是来源证据，不是对所有素材权利的独立认证。

## 维护关系

联系上游可以帮助核实出处、协作与贡献，但不代替许可证，也不把未回复等同于授权或认可。
本项目目前没有宣称得到上游正式合作/背书。
