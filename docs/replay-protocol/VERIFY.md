# 离线交接包校验

解压后在包根目录取得 manifest.json。清单声明包编号、协议 ID、每个文件的 SHA-256；SPEC.md 的哈希是后续修订的基线。哈希只验证完整性与基线一致，不代表发送方身份或双方接受规范；应通过已约定的可信文件渠道取得。

Windows 可在现有 PowerShell 窗口执行下列命令，不用安装 Python，不更改执行策略。先进入解压目录：

```powershell
$manifest = Get-Content -LiteralPath .\manifest.json -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($file in $manifest.files.PSObject.Properties) {
  $actual = (Get-FileHash -LiteralPath $file.Name -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($actual -ne $file.Value) { throw "Checksum mismatch: $($file.Name)" }
}
"PASS: manifest hashes matched"
Get-FileHash -LiteralPath .\docs\replay-protocol\SPEC.md -Algorithm SHA256
```

本命令尚未在 Windows 主机执行，需在回执报告实际结果。若命令失败，回传错误并用已安装的 SHA-256 工具校验，不靠文件名判断版本。

manifest 不包含自己的哈希；整包 ZIP 的 SHA-256 由交付方在包外提供。修改规范后不得重用旧基线哈希；请将修改放入 changes.patch 或提案的前／后文字，保留收到的原始基线。
