<div align="center">

# 工业知识助手（Industrial Knowledge Assistant）

**基于 RAG 架构的制造业智能知识问答系统**

[![License](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.4-42b883.svg)](https://vuejs.org/)
[![Python](https://img.shields.io/badge/Python-3.12-blue.svg)](https://python.org/)

</div>

---

## 📖 项目简介

工业知识助手是一个面向制造业的智能知识问答系统，基于 RAG（检索增强生成）架构，将设备手册、工艺参数、SOP 文档等工业知识数字化，让一线员工用自然语言快速获取所需信息。

### 核心场景

- 🔧 **设备手册问答**：查询设备操作规程、故障排查指南
- 📊 **工艺参数查询**：查询温度、压力、速度等工艺参数
- 📋 **SOP 指引**：标准作业流程查询与步骤指引
- ⚡ **故障诊断**：根据故障现象推荐可能原因与处理方案

### ✨ 核心亮点

- 🏭 **工业场景特化**：针对设备手册、工艺文档、SOP 的专用解析 Pipeline
- 🔍 **Hybrid RRF 检索**：Dense（语义） + Sparse（BM25 关键词）+ RRF 融合，技术文档检索更精准
- 📝 **引用溯源**：每个回答附带 `[source: 文件名, p.页码]` 引用标记
- 🤖 **Agent 扩展**：支持调用设备参数 API、实时状态查询等外部工具
- 💬 **结构化 QA + 非结构化文档**：同时支持预设 QA 对和文档检索
- 📊 **检索评估**：内置 Citation Rate、Precision@K 等自动化评估指标
- 🐳 **容器化部署**：Docker Compose 一键部署

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────┐
│                    用户层                             │
│  Web 聊天界面 / 企业微信机器人 / API 接口             │
└─────────────────────┬───────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────┐
│              应用层（Spring Boot）                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐             │
│  │ Chat API │ │ File API │ │ QA API   │             │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘             │
│       │            │            │                    │
│  ┌────▼────────────▼────────────▼────┐              │
│  │         RAG Pipeline              │              │
│  │  ┌─────────┐  ┌──────────────┐   │              │
│  │  │ Query   │  │ Hybrid RRF   │   │              │
│  │  │Rewriter │  │ Retriever    │   │              │
│  │  └─────────┘  │(Dense+Sparse)│   │              │
│  │               └──────┬───────┘   │              │
│  │  ┌───────────────────▼────────┐  │              │
│  │  │  Rerank + Citation Builder │  │              │
│  │  └───────────────────┬────────┘  │              │
│  │  ┌───────────────────▼────────┐  │              │
│  │  │  LLM Answer Generator     │  │              │
│  │  └────────────────────────────┘  │              │
│  └──────────────────────────────────┘              │
│       │                                            │
│  ┌────▼────────────────────────────┐              │
│  │     Agent Tools Layer           │              │
│  │  设备参数API / SCADA / MES      │              │
│  └─────────────────────────────────┘              │
└─────────────────────┬───────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────┐
│              数据层                                   │
│  PostgreSQL(pgvector) │ Redis │ MinIO │ ChromaDB    │
└─────────────────────────────────────────────────────┘
```

---

## 🆚 相比原版 wudao-kms 的改进

| 特性 | wudao-kms | Industrial Knowledge Assistant |
|------|-----------|-------------------------------|
| 检索方式 | 语义 + 全文 + 混合 | **Dense + Sparse(BM25) + RRF 融合** |
| 引用溯源 | 基础引用 | **[source: file, p.N] 精确到页码** |
| 文档类型 | 通用文档 | **设备手册/工艺参数/SOP 专用解析** |
| 知识形式 | 仅非结构化文档 | **结构化 QA 对 + 非结构化文档** |
| Agent 能力 | 基础助手 | **设备参数查询 / SCADA / MES 集成** |
| 评估体系 | 无 | **Citation Rate / Precision@K / RAGAS** |
| 对话能力 | 多轮对话 | **+ 上下文问题改写（Question Rewriting）** |

---

## 📂 项目结构

```
industrial-knowledge-assistant/
├── kms-server/                    # Java 后端（基于 wudao-kms）
│   └── src/main/java/com/wudao/kms/
│       ├── industrial/            # 🏭 工业特化模块（新增）
│       │   ├── parser/            # 工业文档专用解析器
│       │   ├── qa/                # 结构化 QA 知识库
│       │   ├── agent/             # 工业 Agent 工具
│       │   └── eval/              # 检索评估框架
│       ├── retrieval/             # 🔍 Hybrid RRF 检索（新增）
│       │   ├── dense/             # Dense 向量检索
│       │   ├── sparse/            # BM25 稀疏检索
│       │   └── fusion/            # RRF 融合策略
│       └── citation/              # 📝 引用溯源构建器（新增）
├── rag-engine/                    # 🐍 Python RAG 引擎（新增）
│   ├── ingest.py                  # 文档摄取 Pipeline
│   ├── query.py                   # Hybrid 检索 + 生成
│   ├── chat.py                    # 多轮对话 + 问题改写
│   ├── evaluate.py                # 评估脚本
│   └── requirements.txt
├── web/                           # 前端（Vue 3）
├── docker/                        # Docker 部署
├── evaluation/                    # 📊 评估数据集与结果（新增）
│   ├── test_set.json              # 测试问答对
│   └── results/                   # 评估结果 CSV
└── README.md
```

---

## 🚀 快速开始

### 环境要求

- **Java**：JDK 21+
- **Python**：3.12+
- **数据库**：PostgreSQL 17+（pgvector 扩展）
- **缓存**：Redis 7+
- **存储**：MinIO
- **容器**：Docker & Docker Compose

### 方式一：Docker Compose 部署（推荐）

```bash
# 1. 启动基础服务
cd docker
docker-compose -f docker-compose-base.yaml up -d

# 2. 初始化数据库
psql -h localhost -p 5432 -U postgres -f kms.sql

# 3. 配置环境变量
cp .env.example .env
# 编辑 .env 填入 API Key

# 4. 构建并启动
cd .. && mvn clean package -DskipTests
cd docker && docker-compose -f docker-compose-service.yml up -d

# 5. 启动 RAG 引擎
cd rag-engine && pip install -r requirements.txt
python ingest.py  # 摄取文档
python chat.py    # 启动对话服务

# 6. 启动前端
cd web && npm install && npm run dev
```

### 方式二：本地开发

```bash
# 后端
mvn clean install -DskipTests
cd kms-server && mvn spring-boot:run

# RAG 引擎
cd rag-engine && uv sync
python -m src.ingest

# 前端
cd web && pnpm install && pnpm run dev
```

---

## 📝 工业文档解析 Pipeline

针对制造业文档的特殊格式，提供专用解析器：

### 设备手册解析
- PDF 表格提取（故障代码表、参数规格表）
- 图片中的技术图示识别
- 章节结构自动识别（设备概述 → 安装 → 操作 → 维护 → 故障排除）

### 工艺参数解析
- Excel 工艺参数表结构化提取
- 参数单位自动识别（温度°C、压力MPa、速度m/min）
- 参数范围与约束条件提取

### SOP 文档解析
- Word/PDF SOP 步骤序列提取
- 检查项与判定标准结构化
- 关联设备与工位信息

---

## 🔍 Hybrid RRF 检索策略

```python
# 核心思路（参考 hybrid-rag-tech-assistant）
# Dense: 语义相似度（理解"温度过高"≈"超温"）
# Sparse: BM25 关键词匹配（精确匹配"30XA"、"报警代码E05"）
# RRF: Reciprocal Rank Fusion 融合两路结果

def reciprocal_rank_fusion(results_lists, k=60):
    """RRF 融合多路检索结果"""
    fused_scores = {}
    for results in results_lists:
        for rank, doc in enumerate(results):
            doc_id = doc.metadata["doc_id"]
            fused_scores[doc_id] = fused_scores.get(doc_id, 0) + 1 / (k + rank + 1)
    return sorted(fused_scores.items(), key=lambda x: x[1], reverse=True)
```

**为什么用 Hybrid？**
- Dense 擅长语义理解（"温度过高" 能匹配到 "超温报警"）
- Sparse 擅长精确匹配（设备型号 "30XA"、报警代码 "E05"、具体数值 "80°C"）
- 技术文档两者都重要，RRF 融合后效果优于任何单一方法

---

## 📊 评估框架

内置自动化评估指标：

| 指标 | 说明 |
|------|------|
| **Citation Rate** | 回答中有引用来源的比例 |
| **Precision@K** | Top-K 检索结果中相关文档的比例 |
| **Answer Relevancy** | 回答与问题的相关性（RAGAS） |
| **Faithfulness** | 回答是否忠于检索到的文档（RAGAS） |

```bash
# 运行评估
cd rag-engine
python evaluate.py --test-set ../evaluation/test_set.json
```

---

## 📄 许可证

基于 AGPL-3.0 开源协议。

---

## 🙏 致谢

- [wudao-tech/wudao-kms](https://github.com/wudao-tech/wudao-kms) — 基础架构
- [Galina-Blokh/hybrid-rag-tech-assistant](https://github.com/Galina-Blokh/hybrid-rag-tech-assistant) — Hybrid RRF 检索策略
- [Agezyq/Industrial-RAG-Assistant-QA-Enhanced-](https://github.com/Agezyq/Industrial-RAG-Assistant-QA-Enhanced-) — 结构化 QA + 文档混合
