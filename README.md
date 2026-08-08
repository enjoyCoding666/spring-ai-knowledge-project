# Spring AI 知识库问答 

这是一个小但完整的 Spring AI RAG ，支持文本知识导入、向量检索和基于知识库的 AI 问答。

## 技术栈

- Java 17
- Spring Boot 4.1.0
- Spring AI 2.0.0
- Ollama 本地模型：`qwen3:8b` 与 `qwen3-embedding:0.6b`
- PostgreSQL + PgVector 持久化

## 运行

准备 Java 17+、Maven、PostgreSQL 和已启动的 Ollama。首次运行前准备两个本地模型：

本地大模型管理框架Ollama及模型,详情见： https://blog.csdn.net/sinat_32502451/article/details/163534744

```bash
ollama pull qwen3:8b
ollama pull qwen3-embedding:0.6b
```

PostgreSQL 需要安装并启用 `vector` 扩展：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

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

启动前请通过自己的数据库管理流程创建至少一个知识库，并保存它的 ID。仓库中的 SQL
只维护表结构，不记录或备份任何表数据。

确认 Ollama 默认地址 `http://localhost:11434` 可访问后配置数据库并启动项目：

```bash
export POSTGRES_URL="jdbc:postgresql://localhost:5432/postgres"
export POSTGRES_USERNAME="postgres"
export POSTGRES_PASSWORD="你的数据库密码"
mvn spring-boot:run
```

默认聊天温度为 `0.7`。如需覆盖本地地址或模型，可设置：

```bash
export OLLAMA_BASE_URL="http://localhost:11434"
export OLLAMA_CHAT_MODEL="qwen3:8b"
export OLLAMA_EMBEDDING_MODEL="qwen3-embedding:0.6b"
export OLLAMA_TEMPERATURE="0.7"
```

服务默认端口为 `8082`。文档分块大小默认为 500 个字符，相邻分块重叠 50 个字符，
可分别通过 `KNOWLEDGE_CHUNK_SIZE` 和 `KNOWLEDGE_OVERLAP_SIZE` 调整。分块批量写入
大小默认为 500，可通过 `KNOWLEDGE_BATCH_SIZE` 调整。纯文本文件默认最大为 10 MB，可通过
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

### 1. 导入知识

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

### 2. 上传纯文本文件

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

### 3. 检索知识

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
  "limit": 10
}
```

`limit` 可以省略，默认返回最相关的 10 条，允许范围为 1～20。

### 4. 知识库问答

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
