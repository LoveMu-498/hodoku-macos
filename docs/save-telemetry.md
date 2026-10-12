# 保存与关闭数据采集

本页说明空闲预存、设置提前提交和紧凑会话编码的测量方法。
汇总结果与实际验收边界见 [保存性能分析](save-performance.md)。原始本机记录不随源码上传。

## 三个不同的终点

- **保存工作完成**：回放、工作会话、设置及关闭检查点的当前写入流程结束。
- **窗口消失**：系统窗口列表不再观察到该实例的可见窗口。采样结果不能代替屏幕像素首帧。
- **进程终止**：另一个进程收到该目标 PID 的内核 `NOTE_EXIT` 通知。这里记录的是通知到达时间，
  调度延迟可能使其晚于内核实际终止时刻；JVM 退出钩子不能独自证明进程终止。

阶段有包含和交叠关系，不把所有阶段耗时相加。跨线程等待的 CPU 为 `null`；
同线程 CPU 只覆盖该线程，不包含 JVM 的 JIT、GC 或其他线程，也不能把 wall−CPU 直接称作磁盘等待。

## 应用内记录

数据目录下 `diagnostics/save-trace-%u-%g.jsonl`，默认开启，JVM 参数
`-Dhodoku.save.trace=false` 可关闭。正常数据目录是
`~/Library/Application Support/HoDoKu`；验证使用独立 home/data/tmp。

每次操作使用 `run_id + trace_id` 关联，保存尝试带 `attempt`，失败后选择重试会在同一
退出请求中递增。主动题目保存、配置实际写入、原生退出与窗口关闭都有各自的操作名称。
文件选择和覆盖确认发生在实际保存之前，不计入 `puzzle_save`。
窗口关闭计时从应用进入退出处理开始；原生退出从 Java 收到系统回调开始，均不覆盖此前的用户输入投递延迟。

| 记录 | 含义 |
| --- | --- |
| `ui.quit_dispatch_wait` | 系统退出回调转入 EDT 的等待 |
| `ui.progress_construct` / `ui.first_progress_paint` | 加载窗创建 / 从建立等待到首次 paint 回调结束；两者重叠 |
| `ui.capture_queue_wait` / `ui.capture` | 捕获准备任务等待 / 获取当前工作状态 |
| `session.queue_wait` / `save.work` | 保存线程排队 / 后台保存总工作 |
| `quit.replay` / `replay.queue_wait` / `replay.drain` | 退出提交回放 / 串行写入队列等待 / 当前待写项处理 |
| `replay.compare_digest` / `replay.encode_write` / `replay.flush_sync` | 磁盘摘要检查 / 回放编码写入 / 缓冲刷新及 FD 同步 |
| `quit.session` / `quit.settings` | 原生工作会话 / 配置保存 |
| `file.encode_write` / `file.validate_new` | 临时文件编码写入 / 完整新文件校验 |
| `file.backup.copy_hash` / `file.backup.validate` / `file.backup.replace` | 备份复制摘要 / 必要旧文件校验 / 备份替换 |
| `file.replace` | 新文件替换；不能单凭此事件声称断电持久性 |
| `quit.clean_checkpoint` / `checkpoint.queue_wait` / `checkpoint.write` | 干净关闭标记总过程 / 队列 / 写入 |
| `ui.worker_done_queue_wait` / `ui.worker_done` | 后台完成后的界面回调等待 / 完成回调 |
| `quit.failure_dialog_wait` | 等待用户选择；不算磁盘工作时间 |
| `jvm.shutdown_hook` / `telemetry.flush` | 本采集退出钩子 / 阶段日志刷新，钩子总等待预算约 100 ms |

普通操作结束结果、失败阶段、异常类型（含有界 cause/suppressed 类型链）、目标路径、
重复退出、重试／取消／仍然退出选择分别记录。详细原始错误内容仍由现有保存失败界面提供；
阶段日志不记录题目、批注正文或原始异常消息，不上传。
原生 `performQuit()` 可能直接终止 JVM，使 `quit.native_response` 只有开始事件；保留该事实，
不会补造“已返回”事件。真实外部终止通知和退出钩子结尾一同解释这种未返回阶段。

日志由一个低优先级 daemon 工作线程异步写入，队列 256，每实例三份 1 MiB 轮换文件。
并存实例使用不同 `%u` 文件，不是全机器共用 3 MiB 上限。排队溢出记录 `dropped_events`；
日志目的地不可写时告警一次，实际保存继续。丢行、轮换丢失、缺少结束事件都不能视为完整采集。
不逐笔记录日常操作；既有 120 ms 自动回放写入未建立单独追踪，退出等待覆盖其队列耗时。

## 独立观察与复现

`script/observe_save_exit.swift` 是本地采集辅助程序，不装入 app、不自动随 app 启动。
它只观察由采集脚本新启动的测试 PID，以 kqueue 等待终止，并每 100 ms 检查该进程
可见的普通窗口；不输出其他应用的名称或窗口元数据。记录采样开销、最大单次采样、最后
采样状态，并用 macOS `net.sourceforge.hodoku / SaveLifecycle` 分类记录终止事件。
采样不是硬实时；主机繁忙、窗口服务及调度可使窗口观察延迟超过 100 ms。

```sh
mkdir -p build/save-observer
xcrun swiftc script/observe_save_exit.swift -o build/save-observer/observe_save_exit
python3 script/collect_save_fixture.py \
  --app /absolute/path/HoDoKu.app \
  --test-classes /absolute/path/compiled-test-classes \
  --observer build/save-observer/observe_save_exit \
  --output build/new-save-collection
```

测试类由同一固定完整 JDK 编译；脚本使用所给 app 的内置 Java 和 JAR。
输出目录须全新，依次测量三次未完成及三次完成后关闭；全部保留，逐次完整读回盘面、
9600 个精确双精度坐标、1201／1202 个合法回放帧。

`collect_save_timings.py` 也可包装一个已准备好隔离环境的测试命令；它不会为任意命令
自动隔离配置。成品实际 launcher 的隔离原生 Quit 采集入口是
`verify_distribution.py --save-observer <helper>`。

```sh
python3 script/summarize_save_trace.py /isolated/data/diagnostics \
  --external /collection/external.jsonl --output /collection/summary.json
```

JSON 汇总保留每个阶段的起止、wall/CPU、结果、目标、异常类型与未结束阶段，不求嵌套阶段总和。
指定外部日志时只关联该目标 PID，防止从复制的种子数据中混入旧运行记录。
应用内耗时使用单调时钟；外部对齐使用 wall clock（应用约 1 ms 量化），同时保留外部
单调时钟与对齐偏移。时钟变化、通知调度延迟和观察器开销都限制跨进程计时精度。

本轮汇总见 [保存性能分析](save-performance.md)；原始诊断与用户状态仅保留本机。

## 空闲提交与关闭跳过（2026-10-10）

新增 `session_idle_capture`、`session_idle` 与 `settings_idle` 操作。`session.capture_detached`
测量稳定快照，`session.writer_wait` / `settings.writer_wait` 测量串行写入等待，
`session.compare` 为内容比较，`session.disk_digest` / `settings.disk_digest` 为对已验证字节的摘要核对。
`session.skip_unchanged` / `settings.skip_unchanged` 表示内容与当前磁盘摘要都匹配，未再编码；
`skip_stale_snapshot` 表示更新序号已提交，旧任务不能覆写。

会话静置至少5秒，后台捕获尝试至少间隔30秒；设置连续操作合并1秒，实际后台提交至少间隔5秒。
确认设置与退出不等待这段合并时间。后台失败保留未提交状态，底栏提供详情；退出照常补存/重试。
计时由回放checkpoint持续记录，会话无其他变更时忽略单独用时差异；回放退出保存失败时，
会话执行严格用时比较以保留补恢复兜底。

模态对话框期间不自动捕获。若原生退出发生在模态后台计算中，原进度窗口提示等待，
`quit.wait_for_computation` 记录真实等待；EDT仍处理绘制。计算完成且调用方应用结果后才捕获。
这段计算等待不属于文件编码变慢，也没有人为延迟普通退出。

工作会话v2只改变本机 `last-session.xml` 的表达：完整保留double位与元数据，
重复历史引用共享精确坐标；原生数值数组用有版本、类型和长度检查的二进制载荷编码，
省去逐元素XML节点。继续读v1；v2需要新版读取器。普通题目XML不变。
仅保存优化相对当时 telemetry 开发基线不再改变 `.hrep`；整个 2.3.4 候选相对公开 2.3.3
已经将 Mac 原生尾由 HMX3 升至 HMX5/6，HRPL 公共前缀保持 v3，见 [升级说明](releases/2.3.4.md)。
设置仍是小文件整体原子保存；回放内容变化时仍整体写 `.hrep`，不是增量追加日志。

完成通知直接由有界保存线程投递EDT，避开随包JDK `SwingWorker.done()` 的约33ms合并定时器。
`ui.worker_done_queue_wait` 仍测真实EDT调度等待；不是删除指标或更改首次paint计时。

真实配对采集：`script/compare_save_lifecycle.py` 使用同一夹具字节码交替启动固定旧、新成品，
每个新进程独立home/data/tmp，并保留外部退出通知、字段/坐标/历史/存档点读回、UI ping。
测试有界超时仅终止它自己新建的测试PID；超时/读回失败不计为优化样本。

## 正确性前置检查与探针合同

`compare_save_lifecycle.py` 每次先执行摘要回归、候选的哈希/编解码检查、真实分栏重启恢复、
20层历史会话关闭与完整读回，再检查旧基线的轻量关闭与读回。任一步失败就退出，
`comparison.json` 中保存失败阶段，`samples` 为空；预检耗时不参与性能中位数。
通过后才开始 `--cases` / `--count` 指定的交替采样。历史性能案例仍可用
`--candidate-only-cases history:20 history-idle:20` 明确排除已知无法完成的旧基线。

原生及构建探针统一由 `script/verify_release_features.py` 执行。`--profile build` 使用选定
完整JDK与编译classpath；`--profile native` / 默认 `release` 使用成品JAR和随包Java。
`--profile save-smoke` 可单独执行候选保存检查：

```sh
python3 script/verify_release_features.py \
  --app /absolute/path/HoDoKu.app --test-classes /absolute/path/compiled-test-classes \
  --profile save-smoke --report-dir build/new-save-smoke
```

每个探针使用独立home/data/tmp；需要先写后读的探针按登记参数，在同一隔离目录的两个新JVM执行。
失败的写入阶段不会继续读回；`--keep-going` 只允许继续独立探针。默认每阶段180秒，
`--timeout` 可覆盖；超时先TERM、最多等待5秒再KILL自己的进程组。Robot清理器只记录并释放
本探针按下的键/按钮，异常的finally和TERM的shutdown hook均会执行清理；强制KILL无法执行Java清理钩子。
日志、阶段参数、退出状态和隔离目录保存在报告内，便于核对失败。

旧 `AnnotationDeletionMouseProbe`、`TplsABEraseProbe` 和 `TplsInputMappingProbe` 已退役，
统一入口会在启动任何JVM前拒绝选择。便携保存检查进入云端CORE，Python门禁回归进入CI；
实际可见分栏恢复与Robot输入检查保留在macOS桌面验证。
