# SuperMASProject 架构图

这些图根据课程项目说明设计。当前项目没有 Java 实现，因此图表示建议架构，不是对现有代码的逆向分析。图中采用英文类名和标签，便于后续映射到 Java 类与英文报告。

## 图表

| 图表 | 可编辑源文件 | 矢量预览 | 位图预览 |
| --- | --- | --- | --- |
| UML 类图 | [class-diagram.puml](class-diagram.puml) | [SVG](class-diagram.svg) | [PNG](class-diagram.png) |
| 邮件处理时序图 | [sequence-diagram.puml](sequence-diagram.puml) | [SVG](sequence-diagram.svg) | [PNG](sequence-diagram.png) |
| 智能体组织图 | [agent-organization.puml](agent-organization.puml) | [SVG](agent-organization.svg) | [PNG](agent-organization.png) |

SVG 可无损缩放，适合报告排版；PNG 便于直接查看。三张图共用相同的角色、职责和审批约束。

## 设计选择

- **智能体**：`Agent` 定义统一消息处理入口。`RoutingAgent` 负责选择和协调，`SpecializedAgent` 负责组装上下文并生成回复。学生、教师、行政、科研项目、通用五类角色用独立配置实例表达，每个实例拥有自己的 `AgentConfig`，不要求每个角色都写一个子类。
- **路由**：`RoutingStrategy` 是可替换策略，第一版采用 `RuleBasedRouting`。综合发件人、身份、主题、正文及线程元数据；多候选按评分与配置优先级选择，低置信度或无匹配时交给 GenericAgent。保存候选、选择结果和理由。具体评分规则与阈值在实现和实验时确定。
- **知识边界**：共享 `ContextProvider` 不代表共享全部资料。请求必须携带智能体标识与允许的来源；历史读取也按线程和智能体权限筛选。`ContextBundle` 最终包含当前邮件、相关知识、允许的历史及用户反馈，并受上下文预算约束。
- **本地模型**：`LocalLlmClient` 隔离推理引擎；`OllamaClient` 是建议适配器。所有角色可共享同一个本地模型，不需要每个智能体独立部署模型。更换引擎时新增适配器，更换兼容模型时修改配置。
- **用户审核**：`ReviewService` 维护草案修订与状态，通过 `DraftRepository` 持久化。拒绝及反馈触发重新生成；编辑后的最终文本必须再次明确批准。`EmailGateway` 只发送已批准文本，开发与验收可以先使用模拟适配器。
- **会话记忆**：区分收到的邮件、生成草案、用户反馈与实际发送邮件。只有发送网关确认成功才记为已发送；生成或发送失败均向用户展示可恢复错误。发送超时等结果未知情况，实际接入邮箱时需要查询消息状态、防重复发送，不能直接盲目重试。
- **设计模式**：Strategy 对应路由策略；Adapter 对应本地模型与邮箱适配；Repository 对应草案持久化；RoutingAgent 承担协调职责。注册表用于配置和查找智能体。是否另加 Factory/Observer 等模式取决于实际需求。

类图省略 `AgentRequest`、`AgentResult`、`DraftStatus`、`SendResult` 等辅助类型和 UI 类，保持核心依赖可读。`ConversationMemory`、`ContextProvider`、`DraftRepository`、`EmailGateway` 的具体实现可后续添加，例如 SQLite 持久化、文件上下文和模拟邮件网关。

## 与课程要求的对应

| 课程要求 | 图中位置 |
| --- | --- |
| 路由智能体 + 多个专业智能体 | 类图的 Agent framework；组织图五个角色 |
| 独立提示词及独立上下文 | AgentConfig；组织图各角色的来源权限 |
| 本地 LLM 及模型可替换 | LocalLlmClient / OllamaClient / 本地推理节点 |
| 讨论线程与记忆 | ConversationMemory；时序图历史读取与实际发送记录 |
| 用户接受、修改、拒绝及反馈重生成 | ReviewService；时序图循环与审批分支 |
| 歧义与无匹配邮件 | RoutingDecision；路由排名及 GenericAgent 兜底 |
| 可扩展的 Java 框架 | AgentRegistry 与策略、上下文、记忆、模型接口 |

配置实例方案、评分与兜底规则、SQLite/Ollama 选择，以及错误处理细节均为本设计的建议，不是课程指定的唯一实现方式。RAG、critic agent、协同生成等属于可选扩展，本图暂不引入。

## 本地重渲染

需要 Java 和 PlantUML JAR。项目本次使用的工具位于 `tmp/tools/plantuml.jar`，图表在本地渲染，不向远程图表服务上传邮件或项目资料。

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\docs\uml\render.ps1
```

也可以指定自己的 PlantUML 工具路径：

```powershell
.\docs\uml\render.ps1 -PlantUmlJar 'C:\tools\plantuml.jar'
```

脚本只重生成图表文件，不下载工具。类图和组织图使用 Smetana 布局，无需另装 Graphviz。
