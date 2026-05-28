"""
检索 + 生成模块
Hybrid 检索：Dense(Chroma) + Sparse(BM25) + RRF 融合
问题改写 + LLM 生成 + 引用溯源
"""

import os
import logging
from typing import Optional, Generator
from dataclasses import dataclass, field

log = logging.getLogger(__name__)

from dotenv import load_dotenv

load_dotenv()

from .rag_core import (
    RAGConfig,
    RetrievalResult,
    reciprocal_rank_fusion,
    ChatSession,
    rewrite_question,
    INDUSTRIAL_SYSTEM_PROMPT,
)

from .ingest import EmbeddingGenerator, ChromaStore, BM25Index


# ============================================================
# Hybrid 检索器
# ============================================================

class HybridRetriever:
    """Dense + Sparse + RRF 融合检索，支持 BM25-only 降级模式"""

    def __init__(self, config: RAGConfig = None):
        self.config = config or RAGConfig()
        self.bm25_index = BM25Index()
        self.bm25_index.load()
        # Embedding 和 Chroma 可选（无 API Key 时降级为 BM25-only）
        self._embedding_gen = None
        self._chroma_store = None
        self._has_dense = False
        try:
            api_key = os.getenv("OPENAI_API_KEY", "")
            if api_key and not api_key.startswith("sk-151"):
                self._embedding_gen = EmbeddingGenerator(model=self.config.embedding_model)
                self._chroma_store = ChromaStore(persist_dir=self.config.chroma_persist_dir)
                self._has_dense = True
                log.info("[RAG] Hybrid mode: Dense + Sparse + RRF")
            else:
                log.info("[RAG] BM25-only mode (no valid embedding API key)")
        except Exception as e:
            log.warning(f"[RAG] Dense unavailable, falling back to BM25-only: {e}")

    def retrieve(self, query: str) -> list[RetrievalResult]:
        """执行检索（Hybrid 或 BM25-only）"""
        dense_results = []
        sparse_results = []

        # Dense 检索（可选）
        if self._has_dense and self._embedding_gen and self._chroma_store:
            try:
                query_embedding = self._embedding_gen.embed_query(query)
                dense_docs = self._chroma_store.query(query_embedding, top_k=self.config.dense_top_k)
                dense_results = [
                    RetrievalResult(
                        doc_id=doc["doc_id"],
                        content=doc["content"],
                        score=1.0 - doc["distance"],
                        rank=i + 1,
                        source="dense",
                        metadata=doc["metadata"],
                    )
                    for i, doc in enumerate(dense_docs)
                ]
            except Exception as e:
                log.warning(f"Dense retrieval failed: {e}")

        # Sparse 检索 (BM25)
        sparse_docs = self.bm25_index.query(query, top_k=self.config.sparse_top_k)
        sparse_results = [
            RetrievalResult(
                doc_id=doc["doc_id"],
                content=doc["content"],
                score=doc["score"],
                rank=i + 1,
                source="sparse",
                metadata=doc["metadata"],
            )
            for i, doc in enumerate(sparse_docs)
        ]

        # 如果只有一路，直接返回
        if not dense_results:
            return sparse_results[:self.config.fusion_top_k]
        if not sparse_results:
            return dense_results[:self.config.fusion_top_k]

        # RRF 融合
        fused = reciprocal_rank_fusion(
            [dense_results, sparse_results],
            k=self.config.rrf_k,
            weights=[self.config.dense_weight, self.config.sparse_weight],
        )

        return fused[:self.config.fusion_top_k]


# ============================================================
# LLM 生成器
# ============================================================

class LLMGenerator:
    """LLM 生成器，支持 OpenAI 兼容接口"""

    def __init__(self, model: str = None, api_key: str = None, base_url: str = None, temperature: float = None):
        self.model = model or os.getenv("LLM_MODEL", "gpt-4o-mini")
        self.api_key = api_key or os.getenv("OPENAI_API_KEY", "")
        self.base_url = base_url or os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
        self.temperature = temperature if temperature is not None else float(os.getenv("LLM_TEMPERATURE", "0.0"))
        self._client = None

    @property
    def client(self):
        if self._client is None:
            from openai import OpenAI
            self._client = OpenAI(api_key=self.api_key, base_url=self.base_url)
        return self._client

    def generate(self, prompt: str, system_prompt: str = None) -> str:
        """生成回答"""
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})

        response = self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=self.temperature,
        )
        return response.choices[0].message.content

    def generate_stream(self, prompt: str, system_prompt: str = None):
        """流式生成回答"""
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})

        stream = self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=self.temperature,
            stream=True,
        )
        for chunk in stream:
            if chunk.choices and chunk.choices[0].delta.content:
                yield chunk.choices[0].delta.content

    def rewrite_query(self, question: str, session: ChatSession) -> str:
        """问题改写：追问 → 独立问题"""
        if not session.history:
            return question

        chat_history = session.get_chat_history(max_turns=5)
        prompt = f"""Given the following conversation and a follow up question, 
rephrase the follow up question to be a standalone question.

Chat History:
{chat_history}
Follow Up Input: {question}
Standalone question:"""

        try:
            rewritten = self.generate(prompt)
            return rewritten.strip()
        except Exception:
            return question


# ============================================================
# RAG 查询引擎
# ============================================================

class RAGEngine:
    """完整 RAG 引擎：检索 + 改写 + 生成 + 引用"""

    def __init__(self, config: RAGConfig = None):
        self.config = config or RAGConfig()
        self.retriever = HybridRetriever(self.config)
        self.llm = LLMGenerator(
            model=self.config.llm_model,
            temperature=self.config.temperature,
        )
        self.sessions: dict[str, ChatSession] = {}

    def get_session(self, session_id: str) -> ChatSession:
        """获取或创建会话"""
        if session_id not in self.sessions:
            self.sessions[session_id] = ChatSession(session_id=session_id)
        return self.sessions[session_id]

    def _build_context(self, results: list[RetrievalResult]) -> str:
        """将检索结果拼接为上下文"""
        context_parts = []
        for i, r in enumerate(results, 1):
            source = r.metadata.get("source_file", "未知")
            page = r.metadata.get("page_number", "?")
            context_parts.append(
                f"[文档{i}] 来源: {source}, 第{page}页\n{r.content}"
            )
        return "\n\n".join(context_parts)

    def query(
        self,
        question: str,
        session_id: str = "default",
        stream: bool = False,
    ) -> dict | Generator:
        """
        执行 RAG 查询

        Returns:
            {
                "answer": str,
                "sources": list[dict],
                "rewritten_question": str,
                "session_id": str,
            }
        """
        session = self.get_session(session_id)

        # 问题改写
        rewritten = self.llm.rewrite_query(question, session)

        # 检索
        results = self.retriever.retrieve(rewritten)

        # 构建上下文
        context = self._build_context(results)

        # 生成 prompt
        user_prompt = INDUSTRIAL_SYSTEM_PROMPT.format(
            context=context,
            question=rewritten,
        )

        if stream:
            return self._stream_response(user_prompt, results, rewritten, session, question)

        # 生成回答
        answer = self.llm.generate(user_prompt, system_prompt="你是一个专业的工业知识助手。")

        # 更新会话历史
        session.add_message("user", question)
        session.add_message("assistant", answer)

        # 构建来源信息
        sources = []
        for r in results:
            sources.append({
                "doc_id": r.doc_id,
                "source_file": r.metadata.get("source_file", ""),
                "page_number": r.metadata.get("page_number", ""),
                "score": round(r.score, 4),
                "content_preview": r.content[:200],
            })

        return {
            "answer": answer,
            "sources": sources,
            "rewritten_question": rewritten,
            "session_id": session_id,
        }

    def _stream_response(self, user_prompt, results, rewritten, session, original_question):
        """流式响应生成器"""
        sources = []
        for r in results:
            sources.append({
                "doc_id": r.doc_id,
                "source_file": r.metadata.get("source_file", ""),
                "page_number": r.metadata.get("page_number", ""),
                "score": round(r.score, 4),
                "content_preview": r.content[:200],
            })

        # 先发送元数据
        yield {
            "type": "metadata",
            "sources": sources,
            "rewritten_question": rewritten,
            "session_id": session.session_id,
        }

        # 流式生成
        full_answer = []
        for token in self.llm.generate_stream(user_prompt, system_prompt="你是一个专业的工业知识助手。"):
            full_answer.append(token)
            yield {"type": "token", "content": token}

        # 更新会话
        complete_answer = "".join(full_answer)
        session.add_message("user", original_question)
        session.add_message("assistant", complete_answer)

        yield {"type": "done", "answer": complete_answer}





# ============================================================
# CLI 入口
# ============================================================

if __name__ == "__main__":
    print("🔍 工业知识助手 - RAG 查询引擎")
    print("=" * 50)

    engine = RAGEngine()
    print(f"模型: {engine.config.llm_model}")
    print(f"Embedding: {engine.config.embedding_model}")
    print(f"Chroma 文档数: {engine.retriever.chroma_store.count()}")
    print(f"BM25 文档数: {engine.retriever.bm25_index.count()}")
    print()

    session_id = "cli_session"
    print("输入问题进行查询，输入 'quit' 退出\n")

    while True:
        question = input("❓ 问题: ").strip()
        if question.lower() in ("quit", "exit", "q"):
            break
        if not question:
            continue

        print("\n🔍 检索中...")
        result = engine.query(question, session_id=session_id)

        print(f"\n📝 改写后问题: {result['rewritten_question']}")
        print(f"\n💡 回答:\n{result['answer']}")
        print(f"\n📚 来源 ({len(result['sources'])} 个):")
        for s in result["sources"]:
            print(f"  - {s['source_file']} p.{s['page_number']} (score: {s['score']})")
        print()
