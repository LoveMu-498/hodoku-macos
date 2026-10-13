# 赛事回放共同协议入口

## 2026-10-13 当前协议与验收状态

**M2 协议已由双方接受；Mac 已完成实施与本端验收，本次 Mac 任务完成。** 当前共同版本为 [HRPL-FRAMEWORK-20261013-M2](candidates/HRPL-FRAMEWORK-20261013-M2/README.md)，四份合同固定在提交 `9b6ec58c47024cfaf8a96dbaa678fc6ba2448ae1`。Windows 已给出同哈希 ACK，Mac 接受完全相同字节，不要求再次技术表态。

最新证据和责任边界见 [双端确认与 Mac 任务完成](acknowledgements/M2-20261013.md)。原候选包和历史矩阵保持原字节；Windows 按已确认协议自行完成其实现符合性验收，真实窗口等未执行项仍如实保留，不作为 Mac 结案前置条件。Mac 定时跟进已暂停；普通本端修复直接处理，只有具体兼容缺陷或必要协议变更才重新提出问题，确需牺牲原生功能时请求维护者确认。

以下初版文档保留作历史基线，不覆盖上述准确共同版本及最新验收记录。

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
