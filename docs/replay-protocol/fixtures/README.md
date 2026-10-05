# 共同容器最低合成样例

这三份是纯合成 HRPL 数据，以 Base64 文本交付，避免私人回放进入仓库。不是新版 Windows 写出的实证，也不覆盖全部扩展语义。

- core-v3：两帧，真实初始盘面与手动填入 R1C3=4，保留未知可选行；预期见 expected.json。
- unsupported-version：版本99，必须明确拒绝。
- truncated-v3：盘面负载截断，必须明确拒绝。

在 Windows PowerShell 中可将样例解码到自己的隔离测试目录：

```powershell
$bytes = [Convert]::FromBase64String((Get-Content -LiteralPath .\core-v3.hrep.b64 -Raw))
[IO.File]::WriteAllBytes((Join-Path $PWD 'synthetic-core-v3.hrep'), $bytes)
Get-FileHash -LiteralPath .\synthetic-core-v3.hrep -Algorithm SHA256
```

用当前 Windows 的真正读写器核验读取、另存和共同字段；显示检查另在真实桌面执行。Mac/Windows 原版 codec 在 Mac 主机上的测试不代替 Windows 环境与通道验收。PowerShell 命令也须由 Windows 方实际执行。
