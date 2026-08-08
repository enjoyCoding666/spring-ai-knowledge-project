# Spring AI 知识库问答项目设计

## 需求理解

- 构建一个用于学习和二次开发的 Spring AI 知识库问答项目。
- 支持导入文本、向量检索，以及结合检索上下文生成答案。
- 使用本地 Ollama 的聊天模型和嵌入模型。
- 使用 PostgreSQL 保存业务数据，并使用 PgVector 保存向量。
- 支持多个知识库，导入与检索通过知识库 ID 隔离。
- 暂不实现认证、多租户、管理后台和流式响应。

## 假设

- 知识文本不包含敏感信息。
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

导入、检索和问答请求都显式携带 `knowledgeBaseId`。导入时先验证知识库存在，
再创建文档、切分文本、写入向量和批量保存片段。文档初始状态为 `PROCESSING`，
全部完成后更新为 `COMPLETED`。关系数据和 PgVector 使用同一数据源及事务。

文本默认按 500 个字符切分，相邻片段重叠 50 个字符。片段批量写入大小通过配置控制，
默认 500。向量 metadata 保存知识库 ID、文档 ID、标题和片段序号，用于检索时在
数据库侧过滤知识库范围。

向量检索统一设置最低相似度阈值，默认值为 `0.5`。低于阈值的候选片段不会返回，
以避免仅设置 `topK` 时把明显不相关的最近邻结果补足到响应中。阈值通过应用配置和
环境变量覆盖，知识检索与 RAG 问答共用同一个服务端阈值，不增加请求参数。

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
- 向量检索使用服务端可配置的最低相似度阈值，默认 `0.5`；未选择由客户端逐次传入，
  避免不同接口或调用方采用不一致的相关性标准。
