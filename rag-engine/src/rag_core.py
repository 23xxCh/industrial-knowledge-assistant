"""
工业知识助手 - RAG 引擎
基于 hybrid-rag-tech-assistant 和 Industrial-RAG-Assistant 的思路

核心功能：
1. 文档摄取：PDF 解析 → 滑动窗口分块 → Dense/Sparse 双路索引
2. Hybrid 检索：Dense(语义) + Sparse(BM25) + RRF 融合
3. 多轮对话：上下文问题改写 + 引用溯源
4. 评估：Citation Rate / Precision@K / RAGAS
"""

import os
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional

# ============================================================
# 配置
# ============================================================

@dataclass
class RAGConfig:
    """RAG 引擎配置"""
    # 分块参数（参考 hybrid-rag-tech-assistant）
    chunk_size: int = 600          # 分块大小（字符数）
    chunk_overlap: int = 200       # 分块重叠
    
    # Dense 检索
    embedding_model: str = "text-embedding-3-small"  # 或 BGE-M3
    dense_top_k: int = 20
    
    # Sparse 检索（BM25）
    sparse_top_k: int = 20
    
    # RRF 融合
    rrf_k: int = 60                # RRF 常数
    dense_weight: float = 0.5      # Dense 权重
    sparse_weight: float = 0.5     # Sparse 权重
    fusion_top_k: int = 10         # 融合后返回 Top-K
    
    # LLM
    llm_model: str = "gpt-4o-mini"  # 或 DeepSeek / Qwen
    temperature: float = 0.0        # 确定性输出
    
    # 存储
    chroma_persist_dir: str = "./chroma_db"
    
    # 评估
    eval_output_dir: str = "../evaluation/results"


# ============================================================
# 文档摄取 Pipeline
# ============================================================

@dataclass
class DocumentChunk:
    """文档分块"""
    doc_id: str
    source_file: str
    page_number: int
    section_title: str
    content: str
    chunk_type: str  # text / table / parameter / fault_code / sop_step
    metadata: dict = field(default_factory=dict)


def extract_text_from_pdf(pdf_path: str) -> list[dict]:
    """
    从 PDF 提取文本和表格
    返回: [{"page": 1, "text": "...", "tables": [...]}]
    """
    try:
        from pypdf import PdfReader
        import pdfplumber
    except ImportError:
        raise ImportError("请安装依赖: pip install pypdf pdfplumber")
    
    pages = []
    
    # pypdf 提取文本
    reader = PdfReader(pdf_path)
    for i, page in enumerate(reader.pages):
        text = page.extract_text() or ""
        pages.append({"page": i + 1, "text": text, "tables": []})
    
    # pdfplumber 提取表格
    with pdfplumber.open(pdf_path) as pdf:
        for i, page in enumerate(pdf.pages):
            tables = page.extract_tables()
            if tables and i < len(pages):
                pages[i]["tables"] = tables
    
    return pages


def sliding_window_chunk(text: str, chunk_size: int = 600, overlap: int = 200) -> list[str]:
    """
    滑动窗口分块（参考 hybrid-rag-tech-assistant）
    chunk_size=600, overlap=200
    """
    chunks = []
    step = chunk_size - overlap
    
    for i in range(0, len(text), step):
        end = min(i + chunk_size, len(text))
        chunk = text[i:end]
        if chunk.strip():
            chunks.append(chunk)
        if end == len(text):
            break
    
    return chunks


def ingest_pdf(pdf_path: str, config: RAGConfig) -> list[DocumentChunk]:
    """
    摄取单个 PDF 文件
    PDF → 提取文本/表格 → 滑动窗口分块 → 返回分块列表
    """
    file_name = Path(pdf_path).name
    pages = extract_text_from_pdf(pdf_path)
    
    all_chunks = []
    
    for page_data in pages:
        page_num = page_data["page"]
        text = page_data["text"]
        
        # 文本分块
        text_chunks = sliding_window_chunk(text, config.chunk_size, config.chunk_overlap)
        for i, chunk_text in enumerate(text_chunks):
            chunk = DocumentChunk(
                doc_id=f"{file_name}_p{page_num}_c{i}",
                source_file=file_name,
                page_number=page_num,
                section_title=f"第{page_num}页",
                content=chunk_text,
                chunk_type="text",
                metadata={"chunk_index": i}
            )
            all_chunks.append(chunk)
        
        # 表格分块
        for t_idx, table in enumerate(page_data["tables"]):
            table_text = "\n".join([
                " | ".join([str(cell) if cell else "" for cell in row])
                for row in table if row
            ])
            if table_text.strip():
                chunk = DocumentChunk(
                    doc_id=f"{file_name}_p{page_num}_t{t_idx}",
                    source_file=file_name,
                    page_number=page_num,
                    section_title=f"第{page_num}页-表格{t_idx+1}",
                    content=table_text,
                    chunk_type="table",
                    metadata={"table_index": t_idx}
                )
                all_chunks.append(chunk)
    
    return all_chunks


# ============================================================
# Hybrid 检索（Dense + Sparse + RRF）
# ============================================================

@dataclass
class RetrievalResult:
    """检索结果"""
    doc_id: str
    content: str
    score: float
    rank: int
    source: str  # "dense" 或 "sparse"
    metadata: dict = field(default_factory=dict)


def reciprocal_rank_fusion(
    results_lists: list[list[RetrievalResult]],
    k: int = 60,
    weights: list[float] = None
) -> list[RetrievalResult]:
    """
    RRF (Reciprocal Rank Fusion) 融合多路检索结果
    
    公式：RRF_score(d) = Σ weight_i / (k + rank_i(d))
    
    参考 hybrid-rag-tech-assistant 的实现
    """
    if weights is None:
        weights = [1.0] * len(results_lists)
    
    fused_scores: dict[str, float] = {}
    doc_info: dict[str, RetrievalResult] = {}
    
    for results, weight in zip(results_lists, weights):
        for rank, result in enumerate(results):
            doc_id = result.doc_id
            contribution = weight / (k + rank + 1)
            fused_scores[doc_id] = fused_scores.get(doc_id, 0) + contribution
            if doc_id not in doc_info:
                doc_info[doc_id] = result
    
    # 按融合分数排序
    sorted_docs = sorted(fused_scores.items(), key=lambda x: x[1], reverse=True)
    
    return [
        RetrievalResult(
            doc_id=doc_id,
            content=doc_info[doc_id].content,
            score=score,
            rank=i + 1,
            source="fusion",
            metadata=doc_info[doc_id].metadata
        )
        for i, (doc_id, score) in enumerate(sorted_docs)
    ]


# ============================================================
# 对话管理（多轮对话 + 问题改写）
# ============================================================

CONDENSE_PROMPT = """Given the following conversation and a follow up question, 
rephrase the follow up question to be a standalone question.

Chat History:
{chat_history}
Follow Up Input: {question}
Standalone question:"""


@dataclass
class ChatSession:
    """对话会话"""
    session_id: str
    history: list[dict] = field(default_factory=list)  # [{"role": "user/assistant", "content": "..."}]
    
    def add_message(self, role: str, content: str):
        self.history.append({"role": role, "content": content})
    
    def get_chat_history(self, max_turns: int = 5) -> str:
        """获取最近 N 轮对话历史"""
        recent = self.history[-max_turns * 2:] if self.history else []
        return "\n".join([f"{m['role']}: {m['content']}" for m in recent])


def rewrite_question(question: str, session: ChatSession, llm_func=None) -> str:
    """
    问题改写：将追问改写为独立问题
    例如："那温度呢？" → "设备A的温度参数是多少？"
    
    参考 hybrid-rag-tech-assistant 的 CONDENSE_PROMPT
    """
    if not session.history:
        return question  # 首轮对话，无需改写
    
    chat_history = session.get_chat_history()
    prompt = CONDENSE_PROMPT.format(chat_history=chat_history, question=question)
    
    if llm_func:
        return llm_func(prompt)
    
    # 降级：直接返回原问题
    return question


# ============================================================
# 评估框架
# ============================================================

@dataclass
class EvalResult:
    """单条评估结果"""
    question: str
    expected_answer: str
    actual_answer: str
    retrieved_docs: list[str]
    citation_rate: float
    precision_at_k: float


def compute_citation_rate(answer: str, source_files: list[str]) -> float:
    """
    计算引用率：回答中有引用来源的陈述比例
    """
    import re
    
    # 提取引用
    citations = re.findall(r'\[source:\s*(.+?),\s*p\.\d+\]', answer)
    
    if not citations:
        return 0.0
    
    # 验证引用是否来自真实文档
    valid_citations = 0
    for cite in citations:
        for source in source_files:
            if cite.strip() in source or source in cite.strip():
                valid_citations += 1
                break
    
    # 计算引用密度（每 100 字的引用数）
    answer_length = len(answer)
    if answer_length == 0:
        return 0.0
    
    citation_density = (valid_citations / answer_length) * 100
    
    # 归一化到 0-1（假设每 100 字 3 个引用为满分）
    return min(citation_density / 3.0, 1.0)


def compute_precision_at_k(retrieved: list[str], relevant: list[str], k: int = 5) -> float:
    """
    计算 Precision@K：Top-K 检索结果中相关文档的比例
    """
    top_k = retrieved[:k]
    hits = sum(1 for doc in top_k if doc in relevant)
    return hits / k if k > 0 else 0.0


def run_evaluation(
    test_set: list[dict],
    rag_func,
    config: RAGConfig
) -> list[EvalResult]:
    """
    运行评估
    
    test_set: [{"question": "...", "expected_answer": "...", "relevant_docs": [...]}]
    rag_func: RAG 查询函数 (question) -> (answer, retrieved_docs)
    """
    results = []
    
    for item in test_set:
        question = item["question"]
        expected = item["expected_answer"]
        relevant_docs = item.get("relevant_docs", [])
        
        # 执行 RAG 查询
        answer, retrieved_docs = rag_func(question)
        
        # 计算指标
        source_files = [d.get("source_file", "") for d in retrieved_docs]
        citation_rate = compute_citation_rate(answer, source_files)
        precision = compute_precision_at_k(
            [d.get("doc_id", "") for d in retrieved_docs],
            relevant_docs,
            k=5
        )
        
        results.append(EvalResult(
            question=question,
            expected_answer=expected,
            actual_answer=answer,
            retrieved_docs=[d.get("doc_id", "") for d in retrieved_docs],
            citation_rate=citation_rate,
            precision_at_k=precision
        ))
    
    return results


def export_eval_results(results: list[EvalResult], output_path: str):
    """导出评估结果为 CSV"""
    import csv
    
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    
    with open(output_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow([
            'question', 'expected_answer', 'actual_answer',
            'retrieved_docs', 'citation_rate', 'precision_at_k'
        ])
        
        for r in results:
            writer.writerow([
                r.question,
                r.expected_answer,
                r.actual_answer,
                "|".join(r.retrieved_docs),
                f"{r.citation_rate:.3f}",
                f"{r.precision_at_k:.3f}"
            ])
    
    # 输出汇总
    avg_citation = sum(r.citation_rate for r in results) / len(results) if results else 0
    avg_precision = sum(r.precision_at_k for r in results) / len(results) if results else 0
    
    print(f"\n📊 评估结果汇总:")
    print(f"   总问题数: {len(results)}")
    print(f"   平均引用率: {avg_citation:.3f}")
    print(f"   平均 Precision@5: {avg_precision:.3f}")
    print(f"   结果已导出: {output_path}")


# ============================================================
# System Prompt 模板
# ============================================================

INDUSTRIAL_SYSTEM_PROMPT = """你是一个工业知识助手，专门帮助制造业一线员工解答设备操作、工艺参数、故障排查等问题。

## 回答规则

1. **基于知识库回答**：只使用检索到的文档内容回答问题，不要编造信息
2. **引用来源**：为每个事实性陈述标注来源，格式：[source: 文件名, p.页码]
3. **结构化回答**：
   - 先给出直接答案
   - 再列出详细步骤或参数
   - 最后标注安全提示（如有）
4. **不确定时说明**：如果知识库中没有相关信息，明确说明"根据现有知识库未找到相关信息"

## 引用示例

设备最高工作温度为 85°C [source: 操作手册v2.pdf, p.12]，超过此温度会触发超温报警 [source: 故障排除指南.pdf, p.34]。

## 检索到的文档内容

{context}

## 用户问题

{question}
"""


if __name__ == "__main__":
    print("🏭 工业知识助手 RAG 引擎")
    print("=" * 50)
    config = RAGConfig()
    print(f"分块大小: {config.chunk_size}, 重叠: {config.chunk_overlap}")
    print(f"Embedding: {config.embedding_model}")
    print(f"LLM: {config.llm_model}")
    print(f"RRF k={config.rrf_k}, Dense权重={config.dense_weight}, Sparse权重={config.sparse_weight}")
