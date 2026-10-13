# 赛事回放共同协议入口

## 2026-10-13 当前集中适配入口

Mac 已完成统一帧的实际应用适配与本机验证。当前完整候选为 [HRPL-FRAMEWORK-20261013-M2](candidates/HRPL-FRAMEWORK-20261013-M2/README.md)，包含完整合同、独立验证器、实际 Mac 应用导出与显示证据，以及 [Windows 集中适配任务](candidates/HRPL-FRAMEWORK-20261013-M2/WINDOWS-ADAPTATION.md)。推进顺序为 Mac 完整适配 → Windows 集中适配/报告全部具体冲突 → 集中修订与双端验收。普通技术修复直接在 PR 处理；仅确需牺牲原生功能时请求维护者确认。

Windows M2 应用与最终双端往返仍待执行，旧 W1/O11 回执不等于新版通过。以下初版文档保留作历史基线；当前候选按上述目录中四合同与 manifest 的精确哈希识别。

日期：2026-10-05。首版协议候选：`HRPL-COMMON-1.0`，尚未双方确认。

**状态：倡议与草稿候选，等待 Windows 开发方确认；尚未成为主分支正式协议，也未取得双方流程验收。** Mac 项目已落实修改前检查协议的本地工作规则，不等于 Windows 已采纳规范。

- [交给 Windows 开发方的倡议书](INITIATIVE.md)：双方责任、实际往返验收、GitHub 失败时的替代路径和必须回传的确认项。
- [共同协议候选规范](SPEC.md)：共同格式、语义、版本与兼容等级；现有实现事实和新要求明确分开。
- [两至三次交接收敛规则](CONVERGENCE.md)：同一入口、具体默认方案、准确版本接受与真正阻碍。
- [Windows 方 AI 执行提示词](WINDOWS-TASK.md)：第二轮直接返回 ACK、完整反提案或 BLOCK。
- [修订提案模板](CHANGE-PROPOSAL.md)：Windows 与 Mac 均使用同一模板。
- [Windows 接收与落实回执](WINDOWS-RECEIPT.md)：首次实际验证的必填结果；可直接编辑后离线回传。
- [本端能力与待确认项](CAPABILITIES.md)：静态事实与实际验收分别记录，Windows 新版由对方补填。
- [交接包校验说明](VERIFY.md)：没有 GitHub 或 Python 时仍可校验文件。

暂定正式主入口为 [LoveMu-498/hodoku-macos](https://github.com/LoveMu-498/hodoku-macos) 的 `docs/replay-protocol/`。发布后以固定 commit 和协议文件 SHA-256 引用，不能只引用会移动的 main。

同一个提案无论经 PR、离线补丁或未来镜像传递，都使用同一个提案编号与基线哈希。离线包是某一版本的传输副本，不形成另一套权威。双方确认和实测结果放 `acknowledgements/`；该目录在有真实回执时才创建。

历史实现说明见 [Mac 格式与私有扩展](../replay-file-format.md)。它描述 Mac 实现，不能替代双方确认的共同协议。此倡议不会自动改变现有文件读取、写出或旧格式支持范围。
