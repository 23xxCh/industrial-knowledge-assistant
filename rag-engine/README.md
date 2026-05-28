# 工业知识助手 - Python RAG 引擎

基于 Hybrid RAG (Dense + Sparse + RRF) 的工业知识问答系统。

## 快速开始

### 1. 安装依赖

```bash
cd rag-engine
pip install -r requirements.txt
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填入 API Key
```

### 3. 摄取文档

```bash
# 摄取单个文件
python src/ingest.py /path/to/document.pdf

# 摄取整个目录
python src/ingest.py /path/to/docs/
```

### 4. 启动服务

```bash
python src/chat.py
# 服务启动在 http://0.0.0.0:8000
# API 文档: http://0.0.0.0:8000/docs
```

### 5. 对话查询

```bash
# CLI 交互
python src/query.py

# HTTP API
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "设备报警E05怎么处理？", "stream": false}'
```

### 6. 运行评估

```bash
python src/evaluate.py --test-set ../evaluation/test_set.json
```

## 架构

```
ingest.py   ──→ PDF解析 → 分块 → Embedding → Chroma + BM25
query.py    ──→ 问题改写 → Dense+Sparse检索 → RRF融合 → LLM生成
chat.py     ──→ FastAPI HTTP 服务 (SSE 流式)
evaluate.py ──→ Citation Rate + Precision@K 评估
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat` | 对话（支持 `stream: true` SSE 流式） |
| POST | `/ingest` | 上传文档并摄取 |
| POST | `/ingest/directory` | 摄取整个目录 |
| GET  | `/health` | 健康检查 |
| GET  | `/sessions/{id}/history` | 获取会话历史 |
| DELETE | `/sessions/{id}` | 清除会话 |

## 引用格式

回答中的事实性陈述标注来源：`[source: 文件名, p.页码]`
