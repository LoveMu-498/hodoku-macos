# 云端开发与本地衔接

维护日期：2026-10-01。公开仓库：<https://github.com/LoveMu-498/hodoku-macos>。
云端以这个仓库的源码与 Git 历史为基础；本地未提交的修改不会自动出现在云端。
这份配置用于开发与验证，不推进应用版本，不生成 DMG，也不替换用户的已安装应用。

## 可以验证的范围

Linux + JDK 21 编译全部 Java 源码与测试，使用 `--release 8` 保持现有 Java API 编译约束。
构建包含界面资源与 COPYING 的独立开发 JAR；每个测试进程使用独立 home/data/tmp。
`core` 检查求解/链结构、解析、保存和回放；`swing` 在 Xvfb 虚拟屏幕运行选定的
窗口、标注、圈叉、回放查看器回归。具体清单以 `script/check_cloud.py` 为准。
该清单不是全部测试；修改其他功能时仍须补跑相应已有 probe。
`MacOSApplicationProbe` 的原生 Desktop 注册、`ReplaySharingProbe` 的 Cocoa 分享 helper
和 `GuiLocalizationProbe` 的 macOS 菜单约定留在 Mac 检查，不在云端假装模拟平台验收。

Xvfb 的 Swing 通过不证明 macOS 原生事件、手感、Aqua、签名、Mach-O 依赖、安装或
分享包可用。最终 macOS 成品按 [分发验证](distribution/README.md) 在 Mac 完成。
不得在 Linux 执行 macOS 组包/安装脚本，或把开发 JAR 当作正式 DMG 发布。

## 配置 ChatGPT / Codex 云端环境

按照 [OpenAI 当前云端环境说明](https://learn.chatgpt.com/docs/environments/cloud-environments)，
在新任务的 `Work in → Cloud → Select environment → Create environment`，或
`Settings → Codex Cloud → Environments` 创建环境。仅选择本公开仓库，不上传整个本地工作区。

| 设置 | 本项目建议 |
| --- | --- |
| Repository | `LoveMu-498/hodoku-macos` |
| 环境名称 | `HoDoKu macOS source development` |
| Privacy | `Only me` |
| Secrets | 无；编译/测试不需要 AI API key、个人 GitHub token 或用户配置 |
| Network | 安装阶段允许 Ubuntu 软件源；如用 Package managers 预设，确认 apt 可取所需包，之后可关闭任务网络 |
| Install script | 在仓库根目录运行 `bash script/setup_cloud.sh` |
| Start skill | 阅读根目录 `AGENTS.md` 和本文；检查任务分支/状态，使用隔离测试，遵守本次任务的提交/PR/合并授权 |

Install script 安装缺少的 JDK 21、Python 3、Git、Xvfb/xauth 和中英文字体，随后编译并运行
两个 Java 测试组及源码/隐私检查。在 Ubuntu/Debian 容器内需要 root 或免交互 sudo；不支持的
镜像会明确失败。已有 JDK 21 不替换。依赖下载仅来自发行版软件源，不拉取本地 macOS Java 大包。

检查准备任务的实际安装/测试结果，再保存并 **Publish** 环境；保存配置不等于已发布可复用环境。
修改安装脚本、依赖或启动约定后，按界面执行 Edit / Republish；已有任务不会自动变成新环境。
首次创建环境可能需要用户完成 ChatGPT 登录或 GitHub 仓库选择授权。仅公开仓库的访问已足够；
不要扩大到无关私有仓库。仓库内文件不能代替账号侧的环境发布。

## 开发和验证命令

```bash
# Ubuntu/Debian 新云端环境：装依赖并完成一次验证
bash script/setup_cloud.sh

# 已有 JDK 21 的环境：不安装任何依赖，编译并跑 core
python3 script/check_cloud.py --profile core

# 同次编译并检查 core + Swing
xvfb-run -a -s '-screen 0 1600x1200x24' python3 script/check_cloud.py --profile all

# 修改范围明确时补跑指定 Swing 检查；仍会编译完整源码并检查 JAR/语言资源
xvfb-run -a python3 script/check_cloud.py --profile swing --only ImmediateMappedClickProbe

python3 -m unittest discover -s test/packaging -p test_distribution_sources.py
python3 -m unittest discover -s test/packaging -p test_public_privacy.py
```

结果在每次独立的 `build/cloud/<run>/reports/results.json` 和对应日志中；JAR 在同一运行目录。
日志和测试产生的回放仅为隔离测试数据，不进 Git。`core` 在 Mac 上也可运行；这仍是源码测试，
不代表实际云端或打包成品验证。`test_bundle_audit.py` 使用 Mach-O/clang，应留在 macOS 验证。

`.github/workflows/cloud-checks.yml` 在 PR、main/codex 分支 push 或手动触发时运行上述 Linux 检查。
CI 仅有 `contents: read`，不使用仓库 secrets，不自动合并、不上传 JAR/日志、不发 Release。
第三方 Action 固定到 commit；更新时核对官方来源。失败时检查 job 日志和对应 probe，
不能硬编码结果、移除有效断言或以跳过失败作为通过。

## 云端 PR 与本地合并

1. 每次任务先检查当前分支、基线 commit 和工作区状态。在 `codex/<task>` 分支做范围明确的修改；
   不直接把并行任务叠在同一脏分支上。仅当当前用户明确要求时，才 commit/push/创建 PR。
2. 上传前复核 diff、选中文件和新增历史的密钥/隐私/许可；使用公开或 noreply 提交身份。
   自动扫描只覆盖已知模式，不替代对日志、截图、真实用户回放和说明文字的人工检查。
3. PR 说明写清行为、日期、云端测试和待做的 Mac 验收。版本号和 DMG/Release 仅在用户明确要求
   发行时推进；功能改动在 CHANGES.md 记录，基础设施改动可单独记录为开发流程。
4. CI 通过后 review diff。涉及 macOS 交互/包内容时先在独立本地 checkout 做相应 Mac 验证。
   用户明确要求合并该 PR 后再合并；没有合并授权时交付 PR 与验证结果，不自动发布。
5. 本地公开 checkout 必须先确认干净，再同步 main。示例仅供已获同步授权且干净的 checkout：

   ```bash
   git status --short
   git fetch origin
   git pull --ff-only origin main
   ```

   本地有未提交功能、分叉或冲突时停止自动 pull/覆盖，先比较分支和具体 diff；保留现有改动。
   用户的其他本地开发仓库需选择性移植已审阅改动，不能把它的私有历史推到公开 origin，
   也不能强制 reset、stash 或用公开整树覆盖。完成验证后记录已纳入的 PR/commit。

本地同步和 PR 合并权限均按当次用户要求判断，不把环境设置、一次发布或已有凭据当成永久授权。
云端只接收公开源码、合成 fixture 和必要文档；`.env`、访问令牌、AI key、个人设置/回放、
内部记录和备份均排除。继承的 GPL-3.0-or-later 和第三方归属保持不变，见
[UPSTREAM.md](../UPSTREAM.md)、[许可说明](../THIRD_PARTY_NOTICES.md) 与 [发布约定](open-source-release.md)。
