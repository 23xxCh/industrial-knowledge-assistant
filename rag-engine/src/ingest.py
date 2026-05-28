"""
文档摄取 Pipeline
PDF → 提取文本/表格 → 滑动窗口分块 → Dense Embedding → Chroma + BM25 索引
"""

import os
import json
import hashlib
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv

load_dotenv()

from .rag_core import (
    RAGConfig,
    DocumentChunk,
    extract_text_from_pdf,
    sliding_window_chunk,
    ingest_pdf,
)


# ============================================================
# Embedding 生成
# ============================================================

class EmbeddingGenerator:
    """Embedding 生成器，支持 OpenAI 兼容接口"""

    def __init__(self, model: str = None, api_key: str = None, base_url: str = None):
        self.model = model or os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")
        self.api_key = api_key or os.getenv("OPENAI_API_KEY", "")
        self.base_url = base_url or os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
        self._client = None

    @property
    def client(self):
        if self._client is None:
            from openai import OpenAI
            self._client = OpenAI(api_key=self.api_key, base_url=self.base_url)
        return self._client

    def embed_texts(self, texts: list[str], batch_size: int = 100) -> list[list[float]]:
        """批量生成 Embedding，自动分批处理"""
        all_embeddings = []
        for i in range(0, len(texts), batch_size):
            batch = texts[i:i + batch_size]
            response = self.client.embeddings.create(
                model=self.model,
                input=batch,
            )
            all_embeddings.extend([item.embedding for item in response.data])
        return all_embeddings

    def embed_query(self, text: str) -> list[float]:
        """单条查询 Embedding"""
        return self.embed_texts([text])[0]


# ============================================================
# Chroma 向量数据库管理
# ============================================================

class ChromaStore:
    """Chroma 向量数据库封装"""

    def __init__(self, persist_dir: str = None, collection_name: str = "industrial_docs"):
        import chromadb

        self.persist_dir = persist_dir or os.getenv("CHROMA_PERSIST_DIR", "./chroma_db")
        self.collection_name = collection_name
        self.client = chromadb.PersistentClient(path=self.persist_dir)
        self.collection = self.client.get_or_create_collection(
            name=self.collection_name,
            metadata={"hnsw:space": "cosine"},
        )

    def add_documents(
        self,
        chunks: list[DocumentChunk],
        embeddings: list[list[float]],
    ):
        """将分块及其 Embedding 存入 Chroma"""
        ids = [chunk.doc_id for chunk in chunks]
        documents = [chunk.content for chunk in chunks]
        metadatas = [
            {
                "source_file": chunk.source_file,
                "page_number": chunk.page_number,
                "section_title": chunk.section_title,
                "chunk_type": chunk.chunk_type,
                **{k: str(v) for k, v in chunk.metadata.items()},
            }
            for chunk in chunks
        ]

        # Chroma 有 batch 上限，分批写入
        batch_size = 500
        for i in range(0, len(ids), batch_size):
            end = min(i + batch_size, len(ids))
            self.collection.upsert(
                ids=ids[i:end],
                embeddings=embeddings[i:end],
                documents=documents[i:end],
                metadatas=metadatas[i:end],
            )

    def query(self, query_embedding: list[float], top_k: int = 20) -> list[dict]:
        """向量相似度检索"""
        results = self.collection.query(
            query_embeddings=[query_embedding],
            n_results=top_k,
            include=["documents", "metadatas", "distances"],
        )
        docs = []
        if results and results["ids"] and results["ids"][0]:
            for i in range(len(results["ids"][0])):
                docs.append({
                    "doc_id": results["ids"][0][i],
                    "content": results["documents"][0][i],
                    "metadata": results["metadatas"][0][i],
                    "distance": results["distances"][0][i],
                })
        return docs

    def count(self) -> int:
        return self.collection.count()

    def delete_all(self):
        """清空集合"""
        self.client.delete_collection(self.collection_name)
        self.collection = self.client.get_or_create_collection(
            name=self.collection_name,
            metadata={"hnsw:space": "cosine"},
        )


# ============================================================
# BM25 索引管理
# ============================================================

class BM25Index:
    """BM25 稀疏检索索引"""

    def __init__(self, persist_path: str = None):
        self.persist_path = persist_path or os.getenv("BM25_PERSIST_PATH", "./bm25_index.json")
        self.corpus: list[dict] = []  # [{"doc_id": ..., "content": ..., "metadata": ...}]
        self.bm25 = None
        self._tokenized_corpus: list[list[str]] = []

    def _tokenize(self, text: str) -> list[str]:
        """中英文混合分词：按字符 + 空格切分"""
        import re
        # 英文按空格切，中文按字切
        tokens = []
        for segment in re.findall(r'[a-zA-Z0-9]+|[\u4e00-\u9fff]', text.lower()):
            tokens.append(segment)
        return tokens

    def build(self, chunks: list[DocumentChunk]):
        """从分块列表构建 BM25 索引"""
        from rank_bm25 import BM25Okapi

        self.corpus = [
            {
                "doc_id": chunk.doc_id,
                "content": chunk.content,
                "metadata": {
                    "source_file": chunk.source_file,
                    "page_number": chunk.page_number,
                    "section_title": chunk.section_title,
                    "chunk_type": chunk.chunk_type,
                    **{k: str(v) for k, v in chunk.metadata.items()},
                },
            }
            for chunk in chunks
        ]

        self._tokenized_corpus = [self._tokenize(doc["content"]) for doc in self.corpus]
        self.bm25 = BM25Okapi(self._tokenized_corpus)

    def query(self, query: str, top_k: int = 20) -> list[dict]:
        """BM25 检索"""
        if self.bm25 is None:
            return []

        tokenized_query = self._tokenize(query)
        scores = self.bm25.get_scores(tokenized_query)

        # 按分数排序取 top_k
        ranked_indices = sorted(range(len(scores)), key=lambda i: scores[i], reverse=True)[:top_k]

        results = []
        for idx in ranked_indices:
            if scores[idx] > 0:
                results.append({
                    "doc_id": self.corpus[idx]["doc_id"],
                    "content": self.corpus[idx]["content"],
                    "metadata": self.corpus[idx]["metadata"],
                    "score": float(scores[idx]),
                })
        return results

    def save(self):
        """持久化 BM25 索引到 JSON"""
        data = {"corpus": self.corpus}
        os.makedirs(os.path.dirname(self.persist_path) or ".", exist_ok=True)
        with open(self.persist_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

    def load(self) -> bool:
        """从 JSON 加载 BM25 索引"""
        if not os.path.exists(self.persist_path):
            return False
        with open(self.persist_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.corpus = data["corpus"]
        self._tokenized_corpus = [self._tokenize(doc["content"]) for doc in self.corpus]

        from rank_bm25 import BM25Okapi
        self.bm25 = BM25Okapi(self._tokenized_corpus)
        return True

    def count(self) -> int:
        return len(self.corpus)


# ============================================================
# 摄取 Pipeline
# ============================================================

class IngestPipeline:
    """完整摄取 Pipeline：PDF → 分块 → Embedding → Chroma + BM25"""

    def __init__(self, config: RAGConfig = None):
        self.config = config or RAGConfig()
        self.embedding_gen = EmbeddingGenerator(
            model=self.config.embedding_model,
        )
        self.chroma_store = ChromaStore(
            persist_dir=self.config.chroma_persist_dir,
        )
        self.bm25_index = BM25Index()

    def ingest_file(self, file_path: str) -> int:
        """摄取单个文件，返回分块数量"""
        path = Path(file_path)
        if not path.exists():
            raise FileNotFoundError(f"文件不存在: {file_path}")

        suffix = path.suffix.lower()
        if suffix == ".pdf":
            chunks = ingest_pdf(file_path, self.config)
        elif suffix in (".txt", ".md"):
            text = path.read_text(encoding="utf-8")
            text_chunks = sliding_window_chunk(text, self.config.chunk_size, self.config.chunk_overlap)
            chunks = [
                DocumentChunk(
                    doc_id=f"{path.name}_c{i}",
                    source_file=path.name,
                    page_number=1,
                    section_title=path.stem,
                    content=ct,
                    chunk_type="text",
                    metadata={"chunk_index": i},
                )
                for i, ct in enumerate(text_chunks)
            ]
        else:
            print(f"⚠️ 跳过不支持的文件格式: {suffix}")
            return 0

        if not chunks:
            print(f"⚠️ 文件未提取到内容: {path.name}")
            return 0

        # 生成 Embedding
        print(f"  📐 生成 Embedding ({len(chunks)} 个分块)...")
        texts = [c.content for c in chunks]
        embeddings = self.embedding_gen.embed_texts(texts)

        # 存入 Chroma
        print(f"  💾 存入 Chroma 向量数据库...")
        self.chroma_store.add_documents(chunks, embeddings)

        return len(chunks)

    def ingest_directory(self, dir_path: str) -> dict:
        """摄取整个目录的文档，返回统计信息"""
        dir_p = Path(dir_path)
        if not dir_p.is_dir():
            raise NotADirectoryError(f"不是有效目录: {dir_path}")

        supported = {".pdf", ".txt", ".md"}
        files = [f for f in dir_p.rglob("*") if f.suffix.lower() in supported]

        if not files:
            print(f"⚠️ 目录中未找到支持的文件: {dir_path}")
            return {"total_files": 0, "total_chunks": 0, "files": {}}

        all_chunks: list[DocumentChunk] = []
        file_stats = {}

        for file_path in files:
            print(f"\n📄 处理: {file_path.name}")
            try:
                suffix = file_path.suffix.lower()
                if suffix == ".pdf":
                    chunks = ingest_pdf(str(file_path), self.config)
                elif suffix in (".txt", ".md"):
                    text = file_path.read_text(encoding="utf-8")
                    text_chunks = sliding_window_chunk(text, self.config.chunk_size, self.config.chunk_overlap)
                    chunks = [
                        DocumentChunk(
                            doc_id=f"{file_path.name}_c{i}",
                            source_file=file_path.name,
                            page_number=1,
                            section_title=file_path.stem,
                            content=ct,
                            chunk_type="text",
                            metadata={"chunk_index": i},
                        )
                        for i, ct in enumerate(text_chunks)
                    ]
                else:
                    continue

                all_chunks.extend(chunks)
                file_stats[file_path.name] = len(chunks)
                print(f"  ✅ {len(chunks)} 个分块")
            except Exception as e:
                print(f"  ❌ 处理失败: {e}")
                file_stats[file_path.name] = f"error: {e}"

        if not all_chunks:
            return {"total_files": len(files), "total_chunks": 0, "files": file_stats}

        # 批量生成 Embedding
        print(f"\n📐 批量生成 Embedding ({len(all_chunks)} 个分块)...")
        texts = [c.content for c in all_chunks]
        embeddings = self.embedding_gen.embed_texts(texts)

        # 存入 Chroma
        print(f"💾 存入 Chroma...")
        self.chroma_store.add_documents(all_chunks, embeddings)

        # 构建并保存 BM25 索引
        print(f"🔍 构建 BM25 索引...")
        self.bm25_index.build(all_chunks)
        self.bm25_index.save()
        print(f"💾 BM25 索引已保存: {self.bm25_index.persist_path}")

        stats = {
            "total_files": len(files),
            "total_chunks": len(all_chunks),
            "chroma_count": self.chroma_store.count(),
            "bm25_count": self.bm25_index.count(),
            "files": file_stats,
        }

        print(f"\n✅ 摄取完成!")
        print(f"   文件数: {stats['total_files']}")
        print(f"   分块数: {stats['total_chunks']}")
        print(f"   Chroma 文档数: {stats['chroma_count']}")
        print(f"   BM25 文档数: {stats['bm25_count']}")

        return stats


# ============================================================
# CLI 入口
# ============================================================

if __name__ == "__main__":
    import sys

    if len(sys.argv) < 2:
        print("用法: python ingest.py <文件或目录路径>")
        print("示例: python ingest.py ./docs/")
        sys.exit(1)

    target = sys.argv[1]
    pipeline = IngestPipeline()

    if os.path.isdir(target):
        stats = pipeline.ingest_directory(target)
    elif os.path.isfile(target):
        count = pipeline.ingest_file(target)
        print(f"✅ 摄取完成，共 {count} 个分块")
    else:
        print(f"❌ 路径无效: {target}")
        sys.exit(1)
