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
- 检索请求中的 `limit` 为可选字段，默认返回 10 条，允许范围为 1～20。
- 文档导入 API 和 `t_knowledge_document` 均不保留 `sourcePath/source_path`，避免持久化
  不可迁移的电脑文件路径。已有数据库通过 `sql/migrate_remove_source_path.sql` 删除该列。
- 文件导入采用独立纯文本读取器并复用文本导入用例；读取在进入异步流程之前完成，
  初版严格支持 UTF-8 纯文本，默认最大 10 MB。
- 针对环境配置和短条目类知识，将默认分块大小调整为 500 个字符、重叠量调整为
  50 个字符；配置变更只影响新导入的文档。
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
