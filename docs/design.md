# Spring AI 知识库问答项目设计

## 需求理解

- 构建一个用于学习和二次开发的 Spring AI 知识库问答项目。
- 支持导入文本、向量检索，以及结合检索上下文生成答案。
- 使用本地 Ollama 的聊天模型和嵌入模型。
- 使用 PostgreSQL 保存业务数据，并使用 PgVector 保存向量。
- 支持多个知识库，导入与检索通过知识库 ID 隔离。
- 暂不实现认证、多租户、管理后台和流式响应。

## 假设

- 知识文本可能包含敏感信息，仓库只维护数据库结构，绝不保存或备份表数据。
- PostgreSQL 已创建三张业务表并启用 `vector` 扩展。
- Ollama 默认运行在 `http://localhost:11434`，且所需模型已在本地下载。
- 默认模型和基础 URL 可以通过配置覆盖。

## 方案比较

1. `SimpleVectorStore`：依赖少，但 JSON 文件不适合持续积累大量数据。
2. PGVector：业务数据与向量都可持久化，并支持数据库侧过滤，选用此方案。
3. Elasticsearch：检索能力丰富，但对本项目过重。

## 最终设计

文本导入后先切分为有重叠的片段，再通过 Spring AI 的嵌入模型写入
PgVector。提问时检索同一知识库中最相关的片段，将片段和问题组装为提示词，
再调用 Spring AI `ChatClient` 生成回答。

接口包括：

- `POST /api/knowledge/documents`：导入标题和文本内容。
- `POST /api/knowledge/files`：通过 `multipart/form-data` 上传纯文本文件并导入。
- `POST /api/knowledge/search`：通过 JSON Body 提交知识库 ID、查询文本和结果数量，
  查看相似度检索结果。
- `POST /api/chat`：执行检索增强问答。

代码按 Web、应用服务、领域端口和 Spring AI 适配器分层。核心业务通过端口隔离，
使单元测试不依赖真实网络和 Ollama 服务。

### 纯文本文件上传

文件上传接口接收 `knowledgeBaseId`、`title`、可选的 `sourceType` 和 `file`。
独立的纯文本读取器在 Web 请求线程中先读取文件，再复用现有异步文本导入流程，
避免异步任务访问已经失效的上传临时文件。

初版不根据扩展名过滤文件，而是严格按照 UTF-8 解码。空文件、仅含空白字符的文件、
非法 UTF-8 文件以及超过配置上限的文件返回 HTTP 400。Servlet 上传限制和应用读取
限制默认均为 10 MB。系统只持久化解码后的文本和分片，不保存本机路径或原始文件。

### PostgreSQL 持久化

业务数据分别保存到 `t_knowledge_base`、`t_knowledge_document` 和
`t_document_chunk`。Spring AI PgVector 使用独立的 `t_vector_store` 表保存向量，
`t_document_chunk.vector_document_id` 与向量文档 ID 对应。

导入和问答请求显式携带 `knowledgeBaseId`，检索请求可选择限定知识库。导入时先验证知识库存在，
再创建文档、切分文本、写入向量和批量保存片段。文档初始状态为 `PROCESSING`，
全部完成后更新为 `COMPLETED`。关系数据和 PgVector 使用同一数据源及事务。

文本默认按 500 个字符切分，相邻片段重叠 50 个字符。片段批量写入大小通过配置控制，
默认 500。向量 metadata 保存知识库 ID、文档 ID、标题和片段序号，用于检索时在
数据库侧过滤知识库范围。

每个知识库在 `t_knowledge_base.similarity_threshold` 中保存自己的最低相似度阈值，
默认值为 `0.5`。检索通过一次 SQL 将向量记录与知识库关联，并按照候选记录所属知识库的
阈值过滤。指定知识库和检索全部知识库时采用相同规则，阈值不作为客户端请求参数。

完整 PostgreSQL 初始化 SQL 统一维护在 `sql/init.sql`。脚本包括 PgVectorStore 所需扩展、
四张数据表、业务关联字段索引，以及使用余弦距离的 HNSW 向量索引。

## 错误处理

- 请求字段为空时返回 HTTP 400。
- 上传文件为空、不是合法 UTF-8 或超过大小上限时返回 HTTP 400。
- 模型或向量服务调用失败时返回 HTTP 502。
- 没有命中知识时仍调用模型，但明确要求模型说明知识库信息不足。

## 测试策略

- 单元测试覆盖文本分段、知识导入、检索和 RAG 上下文组装。
- Web 测试覆盖参数校验及主要响应结构。
- Maven 测试和打包作为最终验证。

## 父子知识库设计

### 需求与约束

- 知识库使用无限层级树结构，预计总量在几百个以内、通常不超过 10 层。
- 检索某个知识库时包含当前节点及其全部后代，不包含父级和其他分支。
- 不传 `knowledgeBaseId` 时维持全知识库检索。
- 每条向量使用其所属知识库自己的 `similarity_threshold`。
- 父知识库存在子节点时禁止删除。
- 当前通过 DBeaver 或 SQL 设置父级，暂不增加知识库管理 API。
- 仓库只记录数据库结构和行为设计，不记录任何真实表数据。

### 数据结构

`t_knowledge_base` 增加可空的 `parent_id`，通过自关联外键指向
`t_knowledge_base.id`。根节点的 `parent_id` 为 `NULL`。外键使用
`ON DELETE RESTRICT`，并为 `parent_id` 建立普通索引。

数据库增加父级关系校验触发器。在新增知识库或修改 `parent_id` 前，触发器递归查询
目标父级的祖先；如果祖先中包含当前知识库，则拒绝操作。自引用检查约束负责拦截最直接的
循环，触发器负责拦截跨多层循环。现有知识库迁移后保持 `parent_id = NULL`，不会改变原有
层级和检索范围。

### 检索流程

仓储层使用 PostgreSQL `WITH RECURSIVE` 计算当前知识库及全部后代，再与
`t_vector_store` 和 `t_knowledge_base` 关联。递归集合使用 `UNION` 去重，即使数据库中
意外存在历史循环数据，检索也能停止。问题文本只生成一次 Embedding，范围计算、各知识库
独立阈值过滤、相似度排序和全局 `limit` 均在一条 SQL 中完成。

查询父节点覆盖整棵子树；查询中间节点只覆盖该节点的子树；查询叶子节点只覆盖自身。
不存在的知识库 ID 返回空结果。层级关系只改变检索范围，不需要重新生成已有向量。

### 测试范围

- 覆盖根节点、中间节点、叶子节点和不指定知识库的检索范围。
- 验证父级和兄弟节点不会进入子树检索结果。
- 验证不同知识库仍分别使用自己的相似度阈值。
- 验证自引用、跨层循环和删除带子节点父库均被数据库拒绝。
- 测试只使用虚构数据，不读取或固化本地真实表数据。

## 统一审计字段与软删除设计

### 字段规范

`t_knowledge_base`、`t_knowledge_document`、`t_document_chunk` 和
`t_vector_store` 统一包含以下字段：

```sql
create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
deleted INTEGER NOT NULL DEFAULT 0
```

`deleted` 通过检查约束限定为 `0` 或 `1`。三张已有业务表的 `created_at` 迁移为
`create_time` 并保留原值；向量表直接新增三个字段。迁移必须可重复执行，且只包含结构变更，
不得包含真实数据或固定业务 ID。

### 自动更新时间

PostgreSQL 提供一个通用更新时间函数，四张表分别注册 `BEFORE UPDATE` 触发器。任何记录更新
都会由数据库将 `update_time` 设置为当前时间，从而覆盖业务 SQL 和 Spring AI 对向量表的写入，
无需在 Java 中重复维护时间字段。

### 软删除规则

- `0` 表示正常，`1` 表示已删除。
- 知识库存在未删除直接子库时，数据库拒绝将父库标记为删除。
- 知识库存在性、递归范围和向量检索只使用未删除记录。
- 向量检索同时验证向量、所属知识库和所属文档均未删除。
- 文档软删除后保留向量数据，但向量不再进入检索，便于以后恢复。
- 本次不增加删除与恢复 API，删除状态暂时通过数据库工具管理。

`deleted` 的取值区分度低，不单独建立普通索引。业务关联索引和 HNSW 向量索引使用
`WHERE deleted = 0` 的部分索引，使索引与主要查询条件一致。

### 测试与隐私

- 验证初始化和迁移脚本包含四张表字段、约束、索引和更新时间触发器。
- 验证 Java 查询均包含相应的 `deleted = 0` 条件。
- 在本地 PostgreSQL 事务中验证自动更新时间与父库软删除限制，使用虚构负数 ID并回滚。
- 刷新 schema-only 备份并扫描 SQL，确保不包含任何真实表数据。

## 结构化与语义分块设计

### 目标与约束

- 替换固定字符窗口切分，避免不同模块内容进入同一个 Chunk。
- 输入兼容 Markdown 与无标题普通文本，文件通常小于 100 KB。
- Markdown 标题是强边界；标题章节过长或文本无标题时，才检测段落语义断层。
- Chunk 目标为 300～1200 字符，优先保持标题、自然段、完整句组和代码块完整。
- 每个 Chunk 添加完整标题路径，例如 `运动 > 有氧运动 > 跑步`。
- Embedding 服务不可用时导入失败，不静默降级为固定字符分块。
- 新策略只影响后续导入；已有文档需要重新导入才能更新分块与向量。

### 解析和分块流程

`MarkdownSectionParser` 识别代码块之外的 ATX 标题 `# / ## / ###`，构建章节及完整标题路径。
没有标题的文本作为匿名章节。标题是强制边界，不允许跨章节合并内容。只有标题没有正文的章节
不生成 Chunk。

`ParagraphSplitter` 按空行保留自然段落；单个段落超过最大长度时，优先按照中英文句末标记
拆为完整句组。Markdown 代码块作为不可从代码行中间拆开的整体单元。

短小且结构清晰的标题章节直接生成 Chunk。匿名章节或超过 1200 字符的长章节交给
`SemanticBoundaryDetector`：使用 `EmbeddingModel.embed(List<String>)` 批量生成段落向量，
计算相邻段落的余弦距离，并以当前章节距离分布的第 75 百分位作为候选断层。使用严格大于
判断，避免距离相同时强制制造边界。

`TextChunker` 按以下规则组合段落：不足 300 字符时优先继续合并；达到最小长度且
遇到语义断层时结束；加入下一段会超过 1200 字符时在段落边界结束。单个完整句组允许小于
最小长度。最终标题路径作为正文前缀参与向量化。

### 组件与配置

- `TextChunker`：协调结构解析、语义边界和最终聚合的业务组件。
- `MarkdownSectionParser`：结构解析与标题路径维护。
- `ParagraphSplitter`：自然段、完整句组和代码块处理。
- `SemanticBoundaryDetector`：批量向量化、余弦距离和动态分位点计算。

建议配置：

```properties
app.knowledge.chunk-min-size=300
app.knowledge.chunk-max-size=1200
app.knowledge.semantic-break-percentile=0.75
app.knowledge.semantic-batch-size=32
```

原 `chunk-size` 和 `overlap-size` 配置不再使用。长章节会先生成段落向量用于判断边界，最终
Chunk 再由 PgVectorStore 向量化用于检索。由于段落和最终 Chunk 内容不同，初版接受两次
Embedding 调用，以保持 Spring AI 写入流程简单可靠。

### 测试与隐私

- 使用虚构 Markdown、普通文本和固定假向量验证标题边界、动态语义断层及大小范围。
- 验证代码块中的 `#` 不被识别为标题，代码块不会从代码行中间拆开。
- 验证短标题章节不调用语义 Embedding，长章节采用批量调用而非逐段请求。
- 验证相近段落合并、不同主题切开，且最终 Chunk 始终包含完整标题路径。
- 验证 Embedding 异常导致导入失败，现有导入事务和检索测试继续通过。
- 测试、文档和日志不得包含任何真实知识库内容。

## 决策记录

- 初版选择 `SimpleVectorStore` 展示 RAG 流程；持久化版本改为 PostgreSQL + PgVector。
- 选择 Spring AI 2.0.0 和 Spring Boot 4.1.0，因为这是当前兼容的稳定版本。
- 核心服务依赖自定义端口，以避免业务逻辑与具体 AI SDK 强耦合。
- 只实现必要的三个接口，保持示例清晰并遵守 YAGNI。
- 导入和问答通过 Spring `@Async` 执行，避免长时间占用 Servlet 请求线程。
- 将模型提供方从 OpenAI 切换为本地 Ollama，避免 API Key 和外部模型服务依赖。
- 选择 `qwen3:8b` 负责问答；它不提供 embedding 能力，因此使用
  `qwen3-embedding:0.6b` 负责知识向量化。
- Embedding 的备选方案为 `nomic-embed-text`；最终选择 Qwen3 Embedding，
  以保持本地模型系列一致。
- REST API 使用统一的 `code`、`message`、`data` 响应信封，业务结果只放在
  `data` 中；成功码为 `0`，失败码与 HTTP 状态码保持一致。
- 选择 PostgreSQL 保存长期业务数据，并使用 Spring AI `PgVectorStore` 取代
  `SimpleVectorStore` 的本地 JSON 文件。
- 向量表命名为 `t_vector_store`，满足现有 `t_` 表名前缀规范；不修改已有三张表。
- 导入、检索和问答接口显式接收 `knowledgeBaseId`，避免跨知识库检索。
- 文档片段使用配置化批次写入，默认批次大小为 500。
- 知识检索使用 POST，并将 `knowledgeBaseId`、`query`、`limit` 放入 JSON Body，
  便于客户端统一提交结构化查询条件；旧 GET 接口不再保留。
- 检索请求中的 `knowledgeBaseId` 为可选字段：传入时在 PgVector metadata 上限定知识库，
  不传时不添加向量过滤条件并检索全部知识库。当前项目没有权限体系，因此全库检索不会
  进行用户级数据隔离；引入认证后需要在仓储层补充可访问知识库范围过滤。
- 检索请求中的 `limit` 为可选字段；接入 Cohere Rerank 后默认返回 5 条，允许范围为 1～20。
- 文档导入 API 和 `t_knowledge_document` 均不保留 `sourcePath/source_path`，避免持久化
  不可迁移的电脑文件路径。最终表结构统一以 `sql/init.sql` 为准。
- 文件导入采用独立纯文本读取器并复用文本导入用例；读取在进入异步流程之前完成，
  初版严格支持 UTF-8 纯文本，默认最大 10 MB。
- 分块默认范围为 300～1200 个字符，不再使用固定字符窗口和重叠量；配置变更只影响
  新导入的文档。
- 向量检索阈值持久化在知识库表中，默认 `0.5`；不同知识库可独立设置。检索全部知识库时
  在一条 SQL 中按每条候选向量所属知识库的阈值过滤，避免 Java 侧广查后再过滤。
- 项目仓库只记录数据库结构，不保存、导出或提交任何真实表数据；针对本地数据的配置调整
  只直接作用于用户授权的本地数据库。
- 父子知识库采用邻接表方案；相较物化路径和闭包表，它在当前几百个知识库规模下结构更简单，
  移动节点只需调整直接父级，递归查询成本可接受。
- 父库检索覆盖当前节点及全部后代，但不向上检索父级；每条结果仍使用所属知识库自己的阈值。
- 父级关系目前通过数据库工具维护，因此使用 PostgreSQL 触发器而不是仅依赖 Java 代码防止
  多层循环；未来增加管理 API 时再补充应用层友好校验。
- 四张表统一使用 `create_time`、`update_time` 和整数型 `deleted`；相较引入 ORM 审计框架，
  PostgreSQL 默认值和通用触发器更适合当前 `JdbcTemplate + Spring AI PgVectorStore` 架构。
- 所有读取路径显式过滤 `deleted = 0`，向量检索额外使用 `EXISTS` 验证所属文档状态，避免
  增加多表主查询 JOIN 或在 Java 中进行宽查后过滤。
- 软删除不级联修改子树或向量数据；父库存在有效子库时禁止删除，文档删除后通过查询过滤
  隐藏其向量，以保留简单的恢复能力。
- 文本分块采用“Markdown 标题优先、长章节和匿名文本语义补充”的混合方案；相较固定字符窗口，
  它能保证模块边界，相较所有章节强制语义分析则减少不必要的 Embedding 调用。
- 语义断层使用章节内相邻段落余弦距离的第 75 百分位动态判断，不设置固定距离阈值，以适应
  不同写作风格和主题密度。
- Chunk 不再机械重叠；通过完整标题路径、自然段边界和 300～1200 字符范围平衡上下文完整性
  与检索精度。Embedding 不可用时选择失败而不是静默降级，避免生成不可预期的低质量向量。

## Cohere Rerank 设计

### 需求与边界

- `/api/knowledge/search` 和 AI 问答共用同一套精排流程。
- PgVector 负责粗召回、知识库范围、父子知识库、软删除和相似度阈值过滤。
- Cohere 对粗召回候选进行重排，默认模型为 `rerank-v4.0-fast`。
- 允许将查询以及最多 30 个候选 Chunk 的标题和正文发送给 Cohere 云端 API。
- API Key、查询和知识正文不得写入日志、文档、测试或代码仓库。
- Cohere 不可用或未配置时自动降级为 PgVector 原始结果，不影响搜索与问答可用性。
- 不修改数据库结构，不保存 Cohere 请求或响应。

### 检索流程

`KnowledgeSearchService` 是统一检索编排层。它先调用 `KnowledgeDao.search` 获取最多
30 条候选，再调用 `Reranker` 完成精排。`CohereReranker` 是 `Reranker` 的基础设施实现，
负责构造 SDK 请求、验证响应索引，并将 Cohere `relevanceScore` 映射回 `SearchHit.score`。

搜索接口默认最终返回 5 条，显式传入 `limit` 时仍使用请求值，允许范围保持 1～20。AI 问答
最终保留 4 个上下文 Chunk。粗召回数量独立配置为 30，确保精排有足够候选集。

候选为空时不调用 Cohere。API Key 为空时使用透传实现。Cohere 认证、限流、网络、超时或响应
校验失败统一转换为 `RerankingException`，由 `KnowledgeSearchService` 捕获后降级。降级日志使用
英文且只记录非敏感的异常类型或状态。正常重排结果返回 Cohere 分数，降级结果保留 PgVector
相似度分数。

搜索结果的每个 `SearchHit` 增加 `scoreSource` 字段，取值为 `COHERE` 或 `PGVECTOR`。
`/api/knowledge/search` 同时通过 `X-Rerank-Source` 响应头返回本次结果来源；没有候选结果时
返回 `PGVECTOR`，因为此时不会调用 Cohere。该字段是新增字段，不改变原有 `code/message/data`
响应结构。

### 配置

```properties
app.knowledge.rerank.api-key=${COHERE_API_KEY:}
app.knowledge.rerank.model=${COHERE_RERANK_MODEL:rerank-v4.0-fast}
app.knowledge.rerank.candidate-limit=${COHERE_RERANK_CANDIDATE_LIMIT:30}
```

Cohere Java SDK 固定为 `1.10.1`，避免 SDK 更新造成不可控的兼容性变化。

### 测试策略

- 使用虚构候选和假 Reranker 验证粗召回数量、最终数量、排序和分数映射。
- 验证空候选不调用 Cohere，API Key 缺失时使用 PgVector 结果。
- 验证 Cohere 异常和无效响应索引触发降级，不泄露查询及候选内容。
- 验证搜索接口和 AI 问答均通过 `KnowledgeSearchService` 检索。
- 单元测试不连接 Cohere，也不读取或写入本地知识库表数据。

### 决策记录

- 选择独立 `KnowledgeSearchService`，未选择 Repository 装饰器或在两个业务服务中重复调用，
  原因是统一检索规则并隔离 Cohere SDK。
- 选择 `rerank-v4.0-fast` 作为默认模型，未继续使用 `rerank-v3.5`，原因是前者是更新的多语言
  低延迟模型；模型名称仍允许通过环境变量覆盖。
- 选择粗召回 30 条、搜索默认返回 5 条，兼顾精排质量、外部调用成本和响应冗余。
- 第一版不增加 Cohere 分数阈值，也不合并相邻 Chunk；先依赖排序和 `topN` 控制结果数量，避免
  在缺少真实评分分布时引入未经校准的阈值。
- 选择 Cohere 故障时降级而不是返回 502，优先保证搜索与问答可用性。
- 正常精排时 `score` 表示 Cohere 相关度，降级时表示 PgVector 相似度；不新增数据库字段。

## Ollama Function Calling 教学示例设计

### 理解摘要与边界

- 在当前项目中增加独立的 Ollama + `qwen3:8b` Function Calling 教学接口。
- 使用天气查询场景，让模型从自然语言中判断是否调用 Java 天气工具并提取城市参数。
- 天气结果来自本地模拟数据，不访问外部 API、不引入新的密钥或数据库表。
- 响应除模型最终答案外，还展示工具是否被调用、工具名称、参数及执行结果。
- 示例不接入 PgVector、Cohere、DeepSeek 或知识库数据，不改变现有 RAG 调用链。
- 代码和 README 使用较详细的中文注释与说明，重点解释 Function Calling 理论流程。

假设该接口仅用于单机学习和低并发演示，采用同步调用；模拟天气不要求生产级准确性或持久化。
每次请求创建独立工具对象，避免工具调用轨迹在并发请求之间共享。日志不记录用户问题、工具参数
或工具结果。

### 方案选择

采用 Spring AI 推荐的声明式 `@Tool` 方案。`WeatherTools.getWeather` 使用 `@Tool` 描述工具用途，
城市参数使用 `@ToolParam` 描述。请求时通过 `ChatClient.tools(weatherTools)` 注册工具，Spring AI
根据方法签名生成 JSON Schema，并负责处理模型的工具调用请求和执行 Java 方法。

未采用手动 `MethodToolCallback`，因为它需要显式维护工具元数据和调用适配，教学样板代码较多；
未采用手动控制两轮模型请求与 Tool Response 协议，因为它更适合作为理解基础抽象后的进阶示例。

### 接口与组件

接口定义：

```http
POST /api/ollama/function-calling/weather
Content-Type: application/json
```

请求只包含必填的 `message`。响应继续使用 `code/message/data` 信封，`data` 包含 `answer`、
`toolCalled`、`toolName`、`toolArguments` 和 `toolResult`。当模型没有调用工具时，
`toolCalled` 为 `false`，其余工具轨迹字段为 `null`。

- `WeatherFunctionCallingController`：校验请求并返回统一响应。
- `WeatherFunctionCallingService`：使用绑定到 `OllamaChatModel` 的 `ChatClient` 发起调用。
- `WeatherTools`：声明天气工具、查询模拟数据并记录本次调用轨迹。
- `WeatherDataProvider`：维护只读的虚构城市天气映射。
- 请求、响应、工具结果和调用轨迹使用独立 record 表达，避免无结构 Map 扩散到业务代码。

### 调用流程

1. Controller 将用户自然语言交给 `WeatherFunctionCallingService`。
2. Service 为本次请求创建新的 `WeatherTools`，并通过 `.tools(weatherTools)` 提供给 Qwen。
3. Spring AI 把工具名称、描述和输入 JSON Schema 随提示词发送给 Ollama。
4. Qwen 判断天气问题需要工具，返回 `getWeather` 工具调用和城市参数；模型本身不会执行 Java。
5. Spring AI 在应用进程中调用 `WeatherTools.getWeather`，取得本地模拟结果。
6. Spring AI 将工具结果回传 Qwen，Qwen 基于结果生成自然语言最终答案。
7. Service 同时读取本次工具对象记录的调用轨迹，组装可观察的教学响应。

未知城市返回明确的“暂无模拟天气数据”，不伪造温度。非天气问题允许 Qwen 不调用工具。
空消息由 Bean Validation 返回 HTTP 400；Ollama 不可用或模型调用失败时沿用统一 AI 服务异常响应。

### 测试策略

- 先为 `WeatherDataProvider` 编写已知城市和未知城市的失败测试，再实现最小模拟数据查询。
- 使用可控的 ChatModel/端口测试 Service 是否注册工具并保留真实调用轨迹，不连接外部服务。
- 使用 MockMvc 验证接口路径、请求校验及统一的 `code/message/data` 响应结构。
- 验证非天气问题没有工具调用时返回 `toolCalled=false`。
- 最后通过本地 Ollama 和 `qwen3:8b` 使用虚构天气问题完成集成验证。
- 测试、文档和日志不得包含真实知识库内容、API Key 或其他隐私数据。

### 决策记录

- 选择 `@Tool + @ToolParam`，因为这是 Spring AI 当前推荐且最适合入门理解的声明式方案。
- 选择本地模拟天气而非第三方天气 API，使示例不受网络、额度和密钥配置影响。
- 在响应中暴露非敏感的工具调用轨迹，帮助学习者区分模型决策、Java 执行和模型总结三个阶段。
- 每个请求创建独立 `WeatherTools`，未使用单例可变调用状态，避免并发请求相互污染。
- 第一版只提供一个只读天气工具，不加入计算器、多工具选择、流式响应或持久化，遵守 YAGNI。

## 按功能整理 Service 与 Web 包结构

### 理解摘要与边界

- 将当前职责混杂的 `application` 包改为按功能划分的 `service` 包。
- RAG、DeepSeek 和 Function Calling 分别使用独立的 Service 子包。
- Web 层的 Controller 与所属请求、响应 DTO 按相同功能拆分。
- 数据库访问抽象放入独立 `dao` 包，模型与外部服务抽象放入独立 `thirdparty` 包。
- `domain`、`infrastructure` 和 `config` 暂不按功能继续拆分。
- 所有 HTTP URL、请求参数、响应格式、数据库访问和模型调用行为保持不变。

本次工作是代码组织重构，不新增业务功能，不改变性能、容量、异步、安全或可用性策略。
测试目录同步采用功能分包。公共响应对象由各功能共享，避免复制；全局异常处理继续作用于所有接口。

### 最终目录设计

```text
com.example.knowledge
├── service
│   ├── rag
│   ├── deepseek
│   └── functioncalling
├── web
│   ├── common
│   ├── rag
│   ├── deepseek
│   ├── functioncalling
│   └── GlobalExceptionHandler.java
├── dao
│   └── KnowledgeDao.java
├── thirdparty
│   ├── LanguageModel.java
│   ├── Reranker.java
│   └── DeepSeekChatClient.java
├── domain
├── infrastructure
└── config
```

RAG 的知识库管理、导入、文件读取、分块、检索、Rerank 编排和问答类全部进入
`service.rag`。DeepSeek 对话编排进入 `service.deepseek`。天气工具调用的 UseCase、Service 和
模拟数据提供器进入 `service.functioncalling`。

`KnowledgeRepository` 重命名为 `KnowledgeDao`，其 JDBC 实现重命名为 `JdbcKnowledgeDao`。
`DeepSeekChatPort` 重命名为 `DeepSeekChatClient`，其 Spring AI 实现重命名为
`SpringAiDeepSeekChatClient`。`LanguageModel` 与 `Reranker` 仅迁移到 `thirdparty`，名称保持不变。
基础设施实现仍保留在 `infrastructure` 包。

### 迁移与验证策略

迁移以功能为单位进行：先更新测试的目标包并确认编译失败，再移动生产类、修改 package 和 import，
随后运行该功能的针对性测试。三个功能迁移完成后删除空的 `application` 和 `port` 包，运行全部测试。
最后检查旧包名和旧类名没有残留，并通过本地接口冒烟测试确认 URL 与响应兼容。

此次迁移不读写知识库表数据，文档、测试及日志只使用虚构内容，不记录 API Key 或本地隐私配置。

### 决策记录

- 选择直接迁移现有类，而不是增加包装 Service，避免无业务价值的重复层级。
- 选择 `service/<feature>` 与 `web/<feature>`，让学习者能够按功能快速定位入口和编排逻辑。
- 不将数据库及第三方抽象放入 `service`，避免业务实现与依赖边界混在同一目录。
- 数据库访问抽象命名为 `dao`，第三方模型及外部服务抽象命名为 `thirdparty`，符合本项目约定。
- 本次不进一步拆分 `domain`、`infrastructure` 和 `config`，控制重构范围和回归风险。
- 保持所有接口契约与业务行为不变，将测试结果作为迁移正确性的主要判据。

## 全项目改用 Autowired 字段注入

### 理解摘要与边界

- Controller、Service、DAO 实现和模型客户端实现统一使用 `@Autowired` 字段注入。
- 删除仅用于 Spring 依赖注入的构造方法，依赖字段不再声明为 `final`。
- 业务类通过 `@Service`、`@Repository` 或 `@Component` 交由组件扫描发现。
- 配置参数使用 `@Value` 字段注入，配置类只保留必须显式创建或条件选择的 Bean。
- 异常、DTO、领域对象和纯算法内部传值构造器不属于 Spring 依赖注入，不做机械修改。
- HTTP 接口、模型调用、数据库访问、隐私规则及降级行为保持不变。

### 组件装配设计

RAG、DeepSeek 和 Function Calling 的编排类使用 `@Service`。`JdbcKnowledgeDao` 使用
`@Repository`，Spring AI 模型客户端实现和模拟数据提供器使用 `@Component`。Controller 保持
`@RestController`，并将原有构造器依赖改为 `@Autowired` 字段。

Ollama 与 DeepSeek 分别创建具名 `ChatClient` Bean。使用方通过 `@Autowired` 配合
`@Qualifier` 选择对应客户端，避免同类型 Bean 冲突。Cohere Reranker 仍由配置类根据 API Key
选择真实实现或透传实现，因为这是运行期配置决策，不适合同时注册两个候选组件。

`WeatherTools` 保存单次工具调用轨迹，因此声明为 prototype Bean。Function Calling Service 注入
`ObjectProvider<WeatherTools>`，每次请求获取新实例，确保并发请求之间不会共享可变状态。

### 配置值与测试

分块大小、语义断层、批量写入、文件大小和候选数量等参数使用 `@Value` 注入。需要基于这些参数
初始化内部算法对象的组件在 `@PostConstruct` 阶段完成初始化。单元测试使用 Spring
`ReflectionTestUtils` 注入假实现和测试参数，不为了测试增加生产环境 setter。

迁移按功能分批进行，每批先更新测试装配并确认旧实现失败，再修改生产组件并恢复通过。最后运行
全部测试和真实 Spring Boot 启动验证。测试及日志不得包含数据库表数据或任何 API Key。

### 决策记录

- 选择组件扫描加字段注入，直接满足项目统一使用经典 `@Autowired` 的要求。
- 未选择在现有 `@Bean` 工厂对象上混用字段注入，避免装配来源分散和重复 Bean。
- 未选择 `@Autowired` 构造器，因为用户明确希望去掉当前构造器注入写法。
- 保留少量工厂 Bean，用于具名 `ChatClient` 和条件化 Reranker，这些属于对象创建策略而非业务类注入。
- Function Calling 工具采用 prototype 作用域，保留原有每请求独立调用轨迹的线程安全设计。
- 纯数据和纯算法构造器不改为字段注入，避免把不属于 Spring 容器的对象强行组件化。
