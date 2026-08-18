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

- **依赖最小集**：ktor-client-core + kotlinx-serialization-json + kotlinx-coroutines-core，engine 由消费方提供。加任何依赖前先过服务端仓 CLAUDE.md 的「依赖准入」判据。
- **注释准入**：「为什么」写进服务端仓的 docs，「是什么」靠命名，注释只留「反直觉」。四类允许（反直觉约束 / 外部契约 / 踩坑一行 + 日期 / 公共 API 的 KDoc）；禁止复述代码、抄设计论证、分节横幅；超过 3 行的解释改成一行指针。**边界**：本规则只适用于本仓与 eventbase，改消费方时沿用各自风格。
- **协议变更**：以服务端仓 `docs/protocol.md` 为准；那边变更会在本仓开跟进 issue，落地前不关。两仓独立版本线，tag 为裸版本号。
- **埋点绝不能成为业务的故障源**：上报失败一律吞掉，队列满按「丢最老」自愈，绝不抛给调用方。

## 当前状态

**建仓（2026-08-18），只有骨架与 README，实现未开始。** 落地顺序见服务端仓 README 的「状态」。
