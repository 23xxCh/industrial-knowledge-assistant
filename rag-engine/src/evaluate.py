"""
评估脚本
加载测试集 → 执行 RAG 查询 → 计算 Citation Rate / Precision@K → 输出 CSV
"""

import os
import sys
import csv
import json
from pathlib import Path
from datetime import datetime

from dotenv import load_dotenv

load_dotenv()

from .rag_core import (
    RAGConfig,
    EvalResult,
    compute_citation_rate,
    compute_precision_at_k,
    export_eval_results,
)
from .query import RAGEngine


# ============================================================
# 评估运行器
# ============================================================

class RAGEvaluator:
    """RAG 评估器"""

    def __init__(self, config: RAGConfig = None):
        self.config = config or RAGConfig()
        self.engine = RAGEngine(self.config)

    def load_test_set(self, test_set_path: str) -> list[dict]:
        """加载测试集 JSON"""
        path = Path(test_set_path)
        if not path.exists():
            raise FileNotFoundError(f"测试集文件不存在: {test_set_path}")

        with open(path, "r", encoding="utf-8") as f:
            test_set = json.load(f)

        print(f"📋 加载测试集: {len(test_set)} 条问题")
        return test_set

    def run_single(self, item: dict) -> EvalResult:
        """对单条测试问题执行 RAG 查询并评估"""
        question = item["question"]
        expected = item.get("expected_answer", "")
        relevant_docs = item.get("relevant_docs", [])
        category = item.get("category", "")

        # 执行 RAG 查询
        result = self.engine.query(question, session_id=f"eval_{category}")

        answer = result["answer"]
        sources = result["sources"]

        # 计算引用率
        source_files = [s.get("source_file", "") for s in sources]
        citation_rate = compute_citation_rate(answer, source_files)

        # 计算 Precision@K
        retrieved_doc_ids = [s.get("doc_id", "") for s in sources]
        precision = compute_precision_at_k(retrieved_doc_ids, relevant_docs, k=5)

        return EvalResult(
            question=question,
            expected_answer=expected,
            actual_answer=answer,
            retrieved_docs=retrieved_doc_ids,
            citation_rate=citation_rate,
            precision_at_k=precision,
        )

    def run(self, test_set_path: str, output_dir: str = None) -> list[EvalResult]:
        """运行完整评估"""
        test_set = self.load_test_set(test_set_path)
        output_dir = output_dir or self.config.eval_output_dir

        results = []
        total = len(test_set)

        for i, item in enumerate(test_set, 1):
            category = item.get("category", "")
            print(f"\n[{i}/{total}] 📝 {item['question'][:60]}... ({category})")

            try:
                eval_result = self.run_single(item)
                results.append(eval_result)
                print(f"  引用率: {eval_result.citation_rate:.3f}  P@5: {eval_result.precision_at_k:.3f}")
            except Exception as e:
                print(f"  ❌ 查询失败: {e}")
                results.append(EvalResult(
                    question=item["question"],
                    expected_answer=item.get("expected_answer", ""),
                    actual_answer=f"ERROR: {e}",
                    retrieved_docs=[],
                    citation_rate=0.0,
                    precision_at_k=0.0,
                ))

        # 导出结果
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        output_path = os.path.join(output_dir, f"eval_results_{timestamp}.csv")
        self.export_results(results, output_path)

        return results

    def export_results(self, results: list[EvalResult], output_path: str):
        """导出评估结果为 CSV"""
        os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)

        with open(output_path, "w", newline="", encoding="utf-8-sig") as f:
            writer = csv.writer(f)
            writer.writerow([
                "问题",
                "期望回答",
                "实际回答",
                "检索文档",
                "引用率",
                "Precision@5",
            ])

            for r in results:
                writer.writerow([
                    r.question,
                    r.expected_answer,
                    r.actual_answer,
                    "|".join(r.retrieved_docs),
                    f"{r.citation_rate:.4f}",
                    f"{r.precision_at_k:.4f}",
                ])

        # 汇总统计
        avg_citation = sum(r.citation_rate for r in results) / len(results) if results else 0
        avg_precision = sum(r.precision_at_k for r in results) / len(results) if results else 0

        print(f"\n{'='*60}")
        print(f"📊 评估结果汇总")
        print(f"{'='*60}")
        print(f"   总问题数:     {len(results)}")
        print(f"   平均引用率:   {avg_citation:.4f}")
        print(f"   平均 P@5:     {avg_precision:.4f}")
        print(f"   结果已导出:   {output_path}")

        # 按类别统计
        categories = {}
        for r in results:
            # 从问题中无法直接获取类别，跳过
            pass

        print(f"{'='*60}")

        return output_path


# ============================================================
# CLI 入口
# ============================================================

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="RAG 引擎评估脚本")
    parser.add_argument(
        "--test-set",
        type=str,
        default="../evaluation/test_set.json",
        help="测试集 JSON 文件路径",
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default="../evaluation/results",
        help="结果输出目录",
    )
    parser.add_argument(
        "--llm-model",
        type=str,
        default=None,
        help="LLM 模型名称（覆盖环境变量）",
    )
    parser.add_argument(
        "--embedding-model",
        type=str,
        default=None,
        help="Embedding 模型名称（覆盖环境变量）",
    )

    args = parser.parse_args()

    # 构建配置
    config = RAGConfig()
    if args.llm_model:
        config.llm_model = args.llm_model
    if args.embedding_model:
        config.embedding_model = args.embedding_model

    print("🧪 工业知识助手 - RAG 评估")
    print(f"   LLM:       {config.llm_model}")
    print(f"   Embedding: {config.embedding_model}")
    print(f"   测试集:    {args.test_set}")
    print(f"   输出目录:  {args.output_dir}")

    evaluator = RAGEvaluator(config)
    results = evaluator.run(args.test_set, args.output_dir)
