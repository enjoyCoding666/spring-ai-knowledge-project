# Spring AI 知识库问答 

这是一个小但完整的 Spring AI RAG ，支持文本知识导入、向量检索和基于知识库的 AI 问答。

## 技术栈

- Java 17
- Spring Boot 4.1.0
- Spring AI 2.0.0
- Ollama 本地模型：`qwen3:8b` 与 `qwen3-embedding:0.6b`
- DeepSeek 云端模型：`deepseek-chat`（独立对话接口，可选）
- PostgreSQL + PgVector 持久化

## 运行

### 环境准备

准备 Java 17+、Maven、PostgreSQL 和已启动的 Ollama。首次运行前准备两个本地模型：

### Ollama 模型

本地大模型管理框架Ollama及模型,详情见： https://blog.csdn.net/sinat_32502451/article/details/163534744

```bash
ollama pull qwen3:8b
ollama pull qwen3-embedding:0.6b
```

### PostgreSQL 与 PgVector

Docker安装 PostgreSQL和 pgVector:
```
docker run -d --name pgvector -e POSTGRES_PASSWORD=你的密码 -p 5432:5432 -v pgvector_data:/var/lib/postgresql/data pgvector/pgvector:pg18
```

PostgreSQL 需要安装并启用 `vector` 扩展：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 数据库初始化

项目的完整数据库初始化脚本位于 [`sql/init.sql`](sql/init.sql)，其中记录了：

- `vector`、`hstore`、`uuid-ossp` 扩展
- `t_knowledge_base` 知识库表
- `t_knowledge_document` 文档表
- `t_document_chunk` 文档分块表
- `t_vector_store` Spring AI 向量表
- 业务查询索引和 HNSW 余弦距离索引

新数据库可以一次性执行：

```bash
psql -U postgres -d postgres -f sql/init.sql
```

已有数据库请按顺序执行仅包含 DDL 的迁移脚本：

- [`sql/migrate_add_similarity_threshold.sql`](sql/migrate_add_similarity_threshold.sql)
- [`sql/migrate_add_knowledge_hierarchy.sql`](sql/migrate_add_knowledge_hierarchy.sql)
- [`sql/migrate_add_audit_fields.sql`](sql/migrate_add_audit_fields.sql)

项目使用 `t_knowledge_base`、`t_knowledge_document`、`t_document_chunk`
三张业务表。Spring AI 默认也会自动初始化向量表 `t_vector_store`，向量维度为 1024；
初始化脚本和 Spring AI 的建表操作均可重复执行。

仓库中的 SQL 只维护表结构，不记录或备份任何表数据。知识库可以通过
`POST /api/knowledge/bases` 接口创建，也可以继续使用数据库工具直接插入。

### 数据库连接与项目启动

确认 Ollama 默认地址 `http://localhost:11434` 可访问后配置数据库并启动项目：

```bash
export POSTGRES_URL="jdbc:postgresql://localhost:5432/postgres"
export POSTGRES_USERNAME="postgres"
export POSTGRES_PASSWORD="你的数据库密码"
mvn spring-boot:run
```

### Ollama 运行配置

默认聊天温度为 `0.7`。如需覆盖本地地址或模型，可设置：

```bash
export OLLAMA_BASE_URL="http://localhost:11434"
export OLLAMA_CHAT_MODEL="qwen3:8b"
export OLLAMA_EMBEDDING_MODEL="qwen3-embedding:0.6b"
export OLLAMA_TEMPERATURE="0.7"
```

### 私密 Key 配置

`.env.local` 位于项目根目录，完整路径是
`spring-ai-knowledge-project/.env.local`。不需要上传到 Git 的隐私 Key 可以放在这里面。

如需启用 Cohere 精排或 DeepSeek 对话，在未被 Git 跟踪的 `.env.local` 中配置：

```bash
COHERE_API_KEY="你的 Cohere API Key"
DEEPSEEK_API_KEY="你的 DeepSeek API Key"
```

Cohere 精排模型统一配置在 `application.properties` 中，当前使用
`rerank-v4.0-fast`。

启动前加载本地变量：

```bash
set -a
source .env.local
set +a
mvn spring-boot:run
```

配置 Key 后，PgVector 默认先召回 30 条候选，再由 Cohere 精排。未配置 Key或 Cohere
调用失败时自动使用 PgVector 结果；日志不会记录查询、知识正文或 API Key。

DeepSeek 通过 Spring AI 的 `spring-ai-starter-model-deepseek` 接入，`DEEPSEEK_API_KEY`
通过环境变量注入，不会写入仓库或日志。可通过 `DEEPSEEK_BASE_URL`、
`DEEPSEEK_CHAT_MODEL`（默认 `deepseek-chat`）和 `DEEPSEEK_TEMPERATURE` 覆盖默认配置。

### 运行参数说明

服务默认端口为 `8082`。文档采用“Markdown 标题优先、长章节和无标题文本语义补充”的
混合分块方式，Chunk 目标范围默认为 300～1200 个字符。可通过
`KNOWLEDGE_CHUNK_MIN_SIZE`、`KNOWLEDGE_CHUNK_MAX_SIZE`、
`KNOWLEDGE_SEMANTIC_BREAK_PERCENTILE` 和 `KNOWLEDGE_SEMANTIC_BATCH_SIZE` 调整。
分块批量写入大小默认为 500，可通过 `KNOWLEDGE_BATCH_SIZE` 调整。纯文本文件默认最大为 10 MB，可通过
`KNOWLEDGE_MAX_FILE_SIZE` 调整应用读取上限；如需超过 10 MB，还需要同步调整
`spring.servlet.multipart.max-file-size` 和 `spring.servlet.multipart.max-request-size`。
向量检索的最低相似度阈值保存在 `t_knowledge_base.similarity_threshold`，默认值为
`0.5`。不同知识库可以设置不同阈值；低于对应知识库阈值的结果不会返回。
知识库通过 `t_knowledge_base.parent_id` 建立无限层级父子关系。使用 DBeaver 等数据库工具
设置直接父级后，检索某个知识库会自动覆盖当前知识库及其全部下级；不包含其父级和其他分支。
数据库约束会阻止循环关系，以及删除仍然拥有子节点的父知识库。
四张表统一使用 `create_time`、`update_time` 和 `deleted`。数据库触发器自动维护更新时间；
`deleted` 使用 `0` 表示正常、`1` 表示已删除，知识检索只返回未删除的数据。
分块参数只影响新导入的文档，已经写入 PgVector 的文档需要重新导入才会采用新参数。

## API 示例

### 1. 创建知识库

```bash
curl -X POST http://localhost:8082/api/knowledge/bases \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Spring AI 知识库",
    "description": "Spring AI 相关文档",
    "parentId": null,
    "similarityThreshold": 0.5
  }'
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "knowledgeBaseId": 1
  }
}
```

`name` 必填且不超过 200 字符。`description`、`parentId`、`similarityThreshold` 可选；
`similarityThreshold` 缺省时使用 0.5，`parentId` 指向的父知识库必须存在。创建子知识库时
传入父知识库 ID 即可建立层级关系。

### 2. 导入知识

```bash
curl -X POST http://localhost:8082/api/knowledge/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "knowledgeBaseId": 1,
    "title": "Spring AI 简介",
    "sourceType": "TEXT",
    "content": "Spring AI 为 Java 应用提供统一的模型、Embedding 和向量数据库抽象。"
  }'
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "documentId": 1,
    "chunkCount": 1
  }
}
```

### 3. 上传纯文本文件

接口使用 `multipart/form-data`，目前严格支持 UTF-8 纯文本。文件内容会保存到数据库，
不会保存原文件路径，因此更换电脑不会影响已导入的数据。

```bash
curl -X POST http://localhost:8082/api/knowledge/files \
  -F 'knowledgeBaseId=1' \
  -F 'title=Spring AI 文档' \
  -F 'sourceType=TEXT' \
  -F 'file=@/你的绝对路径/spring-ai.txt'
```

在 Postman 中选择 `POST` 和上述 URL，在 Body 中选择 `form-data`，添加：

- `knowledgeBaseId`：类型 Text，例如 `1`
- `title`：类型 Text，例如 `Spring AI 文档`
- `sourceType`：类型 Text，可省略，默认按 `TEXT` 处理
- `file`：类型 File，然后选择本地 UTF-8 文本文件

响应格式与 `/api/knowledge/documents` 相同。空文件、仅含空白的文件、非法 UTF-8
文件或超过大小限制的文件会返回 HTTP 400。

### 4. 检索知识

```bash
curl -X POST 'http://localhost:8082/api/knowledge/search' \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "Spring AI"
  }'
```

不传 `knowledgeBaseId` 时检索全部知识库；如果只需检索指定知识库，可以增加：

```json
{
  "knowledgeBaseId": 1,
  "query": "Spring AI",
  "limit": 5
}
```

`limit` 可以省略，默认返回精排后的 5 条，允许范围为 1～20。正常精排时 `score` 是
Cohere 相关度分数；降级时 `score` 保留为 PgVector 相似度分数。

每条结果通过 `scoreSource` 明确标识评分来源，搜索响应头也会返回
`X-Rerank-Source`。例如：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "title": "Spring AI",
      "content": "示例知识内容",
      "score": 0.91,
      "scoreSource": "COHERE"
    }
  ]
}
```

`COHERE` 表示 Cohere 精排结果，`PGVECTOR` 表示未启用 Cohere、发生降级或没有可精排候选。

### 5. 知识库问答

```bash
curl -X POST http://localhost:8082/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"knowledgeBaseId":1,"question":"Spring AI 能做什么？"}'
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "answer": "Spring AI 为 Java 应用提供统一的 AI 模型与向量检索抽象。",
    "sources": ["Spring AI 简介"]
  }
}
```

### 6. DeepSeek 对话

```bash
curl -X POST http://localhost:8082/api/deepseek/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"介绍一下 Spring AI"}'
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "answer": "Spring AI 是 Spring 生态中面向 AI 应用开发的框架……"
  }
}
```

需要先在 `.env.local` 中配置 `DEEPSEEK_API_KEY`。该接口直接调用 DeepSeek 模型，
与知识库检索无关。

所有接口统一返回 `code`、`message`、`data`。成功时 `code` 为 `0`；失败时
`code` 与 HTTP 状态码一致，`data` 为 `null`。

## 项目结构

```text
src/main/java/com/example/knowledge
├── application     # 文本分段、知识导入和 RAG 用例
├── config          # Spring Bean 装配
├── domain          # 领域数据结构
├── infrastructure  # Ollama、JDBC 和 PgVector 适配器
├── port            # 核心业务端口
└── web             # REST API、DTO 和异常处理
```

## 测试

```bash
mvn test
```

详细设计和决策记录见 [`docs/design.md`](docs/design.md)。
