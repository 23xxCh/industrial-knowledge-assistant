"""
对话服务 - FastAPI HTTP 接口
POST /chat       - 对话（支持 SSE 流式响应）
POST /ingest     - 上传文档并摄取
GET  /health     - 健康检查
"""

import os
import json
import uuid
import shutil
import tempfile
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv

load_dotenv()

from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.responses import StreamingResponse, JSONResponse
from pydantic import BaseModel

from .rag_core import RAGConfig
from .query import RAGEngine
from .ingest import IngestPipeline

# ============================================================
# 初始化
# ============================================================

app = FastAPI(
    title="工业知识助手 - RAG 引擎",
    description="基于 Hybrid RAG 的工业知识问答系统",
    version="1.0.0",
)

config = RAGConfig(
    chunk_size=int(os.getenv("CHUNK_SIZE", "600")),
    chunk_overlap=int(os.getenv("CHUNK_OVERLAP", "200")),
    embedding_model=os.getenv("EMBEDDING_MODEL", "text-embedding-3-small"),
    llm_model=os.getenv("LLM_MODEL", "gpt-4o-mini"),
    temperature=float(os.getenv("LLM_TEMPERATURE", "0.0")),
    chroma_persist_dir=os.getenv("CHROMA_PERSIST_DIR", "./chroma_db"),
)

rag_engine = RAGEngine(config)
ingest_pipeline = IngestPipeline(config)

# 临时文件上传目录
UPLOAD_DIR = Path(os.getenv("UPLOAD_DIR", "./uploads"))
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)


# ============================================================
# 请求/响应模型
# ============================================================

class ChatRequest(BaseModel):
    question: str
    session_id: Optional[str] = None
    stream: bool = False


class ChatResponse(BaseModel):
    answer: str
    sources: list[dict]
    rewritten_question: str
    session_id: str


class IngestResponse(BaseModel):
    success: bool
    message: str
    stats: Optional[dict] = None


class HealthResponse(BaseModel):
    status: str
    version: str
    chroma_count: int
    bm25_count: int
    llm_model: str
    embedding_model: str


# ============================================================
# 接口实现
# ============================================================

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """健康检查"""
    return HealthResponse(
        status="ok",
        version="1.0.0",
        chroma_count=rag_engine.retriever.chroma_store.count(),
        bm25_count=rag_engine.retriever.bm25_index.count(),
        llm_model=config.llm_model,
        embedding_model=config.embedding_model,
    )


@app.post("/chat")
async def chat(request: ChatRequest):
    """
    对话接口
    - stream=false: 返回完整 JSON 响应
    - stream=true: 返回 SSE 流式响应
    """
    if not request.question.strip():
        raise HTTPException(status_code=400, detail="问题不能为空")

    session_id = request.session_id or str(uuid.uuid4())

    if request.stream:
        return StreamingResponse(
            _stream_chat(request.question, session_id),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no",
            },
        )

    # 非流式
    result = rag_engine.query(request.question, session_id=session_id)
    return ChatResponse(
        answer=result["answer"],
        sources=result["sources"],
        rewritten_question=result["rewritten_question"],
        session_id=result["session_id"],
    )


async def _stream_chat(question: str, session_id: str):
    """SSE 流式响应生成器"""
    try:
        gen = rag_engine.query(question, session_id=session_id, stream=True)

        for event in gen:
            if event["type"] == "metadata":
                data = json.dumps(event, ensure_ascii=False)
                yield f"event: metadata\ndata: {data}\n\n"
            elif event["type"] == "token":
                data = json.dumps({"type": "token", "content": event["content"]}, ensure_ascii=False)
                yield f"event: token\ndata: {data}\n\n"
            elif event["type"] == "done":
                data = json.dumps(event, ensure_ascii=False)
                yield f"event: done\ndata: {data}\n\n"
    except Exception as e:
        error_data = json.dumps({"type": "error", "message": str(e)}, ensure_ascii=False)
        yield f"event: error\ndata: {error_data}\n\n"


@app.post("/ingest", response_model=IngestResponse)
async def ingest_document(
    file: UploadFile = File(...),
    session_id: Optional[str] = Form(None),
):
    """上传文档并摄取到知识库"""
    # 检查文件类型
    allowed_suffixes = {".pdf", ".txt", ".md"}
    suffix = Path(file.filename).suffix.lower()
    if suffix not in allowed_suffixes:
        raise HTTPException(
            status_code=400,
            detail=f"不支持的文件格式: {suffix}，支持: {', '.join(allowed_suffixes)}",
        )

    # 保存上传文件
    save_path = UPLOAD_DIR / file.filename
    try:
        with open(save_path, "wb") as f:
            content = await file.read()
            f.write(content)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"文件保存失败: {e}")

    # 摄取
    try:
        chunk_count = ingest_pipeline.ingest_file(str(save_path))
        return IngestResponse(
            success=True,
            message=f"文件 {file.filename} 摄取成功",
            stats={
                "filename": file.filename,
                "chunks": chunk_count,
                "chroma_total": ingest_pipeline.chroma_store.count(),
            },
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"摄取失败: {e}")
    finally:
        # 清理临时文件
        if save_path.exists():
            save_path.unlink()


@app.post("/ingest/directory")
async def ingest_directory(dir_path: str = Form(...)):
    """摄取整个目录的文档"""
    if not os.path.isdir(dir_path):
        raise HTTPException(status_code=400, detail=f"目录不存在: {dir_path}")

    try:
        stats = ingest_pipeline.ingest_directory(dir_path)
        return IngestResponse(
            success=True,
            message=f"目录摄取完成，共 {stats['total_chunks']} 个分块",
            stats=stats,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"摄取失败: {e}")


@app.get("/sessions/{session_id}/history")
async def get_session_history(session_id: str):
    """获取会话历史"""
    session = rag_engine.get_session(session_id)
    return {
        "session_id": session.session_id,
        "history": session.history,
        "turns": len(session.history) // 2,
    }


@app.delete("/sessions/{session_id}")
async def clear_session(session_id: str):
    """清除会话历史"""
    if session_id in rag_engine.sessions:
        del rag_engine.sessions[session_id]
    return {"status": "ok", "session_id": session_id}


# ============================================================
# 启动入口
# ============================================================

if __name__ == "__main__":
    import uvicorn

    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8000"))

    print(f"🚀 工业知识助手 RAG 引擎启动中...")
    print(f"   地址: http://{host}:{port}")
    print(f"   文档: http://{host}:{port}/docs")
    print(f"   模型: {config.llm_model}")
    print(f"   Embedding: {config.embedding_model}")

    uvicorn.run(app, host=host, port=port)
