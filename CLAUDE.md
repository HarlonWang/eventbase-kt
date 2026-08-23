# eventbase-kt

[eventbase](https://github.com/HarlonWang/eventbase) 的 Kotlin Multiplatform 客户端库：强类型事件、离线队列、批量上报。

**开工前先读 README.md**（接入方视角的完整用法），以及服务端仓的 `docs/protocol.md`——**协议唯一权威只住服务端仓，本仓不留副本**。

## 关联仓库

| 路径 | 角色 |
|---|---|
| `/Users/wanghl/eventbase` | **服务端仓 + 协议权威**：`docs/protocol.md` / `docs/telemetry-design.md`（含事件词汇） |
| `/Users/wanghl/TrendingProjects/TrendingAI` | 首个消费方：约 130 个调用点按新词汇重构，替换 Aptabase |
| `/Users/wanghl/loginbase-kt` | 邻居：同一套 CI 与发布链路；**本库不依赖它，它也不依赖本库** |

## 铁律

- **依赖准入**：common 只有 ktor-client-core + kotlinx-serialization-json + kotlinx-coroutines-core，engine 由消费方提供；androidMain 另有 `androidx.lifecycle:lifecycle-process`。加任何依赖前先过服务端仓 CLAUDE.md 的「依赖准入」判据。
  - `lifecycle-process` 的准入结论（2026-08-19，**判据③不满足的明示例外，已拍板接受**）：判据①②④过——Google 维护的 AndroidX 事实标准、Maven Central 签名发布、无安装脚本；**判据③（传递依赖 ≤ 2）不满足**，实际拖进 `androidx.annotation`、`lifecycle-runtime`（→`lifecycle-common`）、`androidx.startup:startup-runtime`、`kotlin-stdlib` 四项。
    - 其中 `startup-runtime` 会通过 `ProcessLifecycleInitializer` 走 `InitializationProvider` 自动初始化；**消费方若移除该 provider，`ProcessLifecycleOwner.get()` 会抛**——`detachLifecycle`/attach 两处已用 runCatching + 主线程 post 兜住。
    - 记为例外而非改判据的理由：自己数 Activity 数不出配置变更（旋转时 started 计数归零再加一，必然切出假会话），`ProcessLifecycleOwner` 内置的 700ms 去抖是唯一正确口径来源，自己实现等于复刻它。
- **注释准入**：注释规范已全局化（2026-08-23 起适用于全部仓库），判据以 `~/.claude/CLAUDE.md`「代码注释规范」为准；「为什么」仍写进服务端仓的 docs。
- **协议变更**：以服务端仓 `docs/protocol.md` 为准；那边变更会在本仓开跟进 issue，落地前不关。两仓独立版本线，tag 为裸版本号。
- **埋点绝不能成为业务的故障源**：上报失败一律吞掉，队列满按「丢最老」自愈，绝不抛给调用方。

## 当前状态

**核心与生命周期已实现，45 个测试（Android host）+ iOS 两 target 编译通过**（2026-08-19）。
首个消费方 TrendingAI 已接入（composite build，`local.properties` 的 `eventbase-kt.dir`）；
坐标到项目路径的映射是给消费方的契约，写在 `gradle/composite-substitutions`，**改模块名要同步改它**。
落地顺序见服务端仓 README 的「状态」。

**0.1.0 已发布 Maven Central**（2026-08-20）。发版靠打裸版本号 tag 触发 `publish.yml`：
测试 → `publishAndReleaseToMavenCentral` → 建 Release。**Maven Central 的版本发出去就删不掉也覆盖不了**，
发错只能发下一个版本号，故 tag 前务必确认 workflow 里的任务名在本仓真实存在（0.1.0 前就踩过一次：
publish.yml 抄自 loginbase-kt，带着本仓没有的 `:browser` 模块）。
