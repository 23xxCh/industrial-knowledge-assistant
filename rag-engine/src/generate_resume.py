"""
制造业 AI 应用工程师 简历生成器
排版参考：陈广凯简历风格
- 页面：US Letter (612x792 pt)
- 姓名：居中 18pt 加粗
- 联系方式：居中 10pt，• 分隔
- 模块标题：14pt 加粗 + 下划线
- 正文：10pt，公司/学校名加粗，日期右对齐
- Bullet：● 开头
"""

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib.colors import HexColor, black
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.styles import getSampleStyleSheet
import os

# ============================================================
# 配置
# ============================================================

OUTPUT_PATH = os.path.join(os.path.dirname(__file__), "..", "CV-制造业AI应用工程师.pdf")

# 颜色
BLACK = HexColor("#000000")
DARK_GRAY = HexColor("#333333")
GRAY = HexColor("#666666")
BLUE = HexColor("#1a5276")
LIGHT_BLUE = HexColor("#2980b9")

# 页面尺寸
PAGE_W, PAGE_H = A4  # 595 x 842 pt
MARGIN_LEFT = 50
MARGIN_RIGHT = 50
MARGIN_TOP = 40
CONTENT_W = PAGE_W - MARGIN_LEFT - MARGIN_RIGHT

# ============================================================
# 字体注册（需要系统中文字体）
# ============================================================

# 尝试注册中文字体
CHINESE_FONT = "Helvetica"
CHINESE_BOLD = "Helvetica-Bold"

for font_path, font_name in [
    ("C:/Windows/Fonts/msyh.ttc", "MicrosoftYaHei"),
    ("C:/Windows/Fonts/msyhbd.ttc", "MicrosoftYaHeiBold"),
    ("C:/Windows/Fonts/simhei.ttf", "SimHei"),
    ("C:/Windows/Fonts/simsun.ttc", "SimSun"),
]:
    if os.path.exists(font_path):
        try:
            pdfmetrics.registerFont(TTFont(font_name, font_path))
            if "msyh" in font_path and "bd" not in font_path:
                CHINESE_FONT = font_name
            elif "msyhbd" in font_path:
                CHINESE_BOLD = font_name
            elif "simhei" in font_path:
                CHINESE_FONT = font_name
                CHINESE_BOLD = font_name
        except:
            pass

# 如果没找到微软雅黑，用 SimHei
if CHINESE_FONT == "Helvetica":
    for font_path, font_name in [
        ("C:/Windows/Fonts/simhei.ttf", "SimHei"),
        ("C:/Windows/Fonts/simsun.ttc", "SimSun"),
    ]:
        if os.path.exists(font_path):
            try:
                pdfmetrics.registerFont(TTFont(font_name, font_name=font_path))
                CHINESE_FONT = font_name
                CHINESE_BOLD = font_name
                break
            except:
                pass

print(f"Using font: {CHINESE_FONT} / {CHINESE_BOLD}")


# ============================================================
# 绘制函数
# ============================================================

class ResumeBuilder:
    def __init__(self, filename):
        self.c = canvas.Canvas(filename, pagesize=A4)
        self.y = PAGE_H - MARGIN_TOP  # 当前 y 坐标（从顶部开始）
        self.page = 1

    def check_space(self, needed=30):
        """检查是否需要换页"""
        if self.y < MARGIN_TOP + needed:
            self.c.showPage()
            self.y = PAGE_H - MARGIN_TOP
            self.page += 1

    def draw_name(self, name):
        """绘制姓名（居中，大号加粗）"""
        self.c.setFont(CHINESE_BOLD, 20)
        self.c.setFillColor(BLACK)
        tw = self.c.stringWidth(name, CHINESE_BOLD, 20)
        self.c.drawString((PAGE_W - tw) / 2, self.y, name)
        self.y -= 25

    def draw_contact(self, items):
        """绘制联系方式（居中，• 分隔）"""
        self.c.setFont(CHINESE_FONT, 9)
        self.c.setFillColor(GRAY)
        line = "  •  ".join(items)
        tw = self.c.stringWidth(line, CHINESE_FONT, 9)
        self.c.drawString((PAGE_W - tw) / 2, self.y, line)
        self.y -= 22

    def draw_section(self, title):
        """绘制模块标题（加粗 + 下划线）"""
        self.check_space(40)
        self.c.setFont(CHINESE_BOLD, 13)
        self.c.setFillColor(BLUE)
        self.c.drawString(MARGIN_LEFT, self.y, title)
        # 下划线
        self.c.setStrokeColor(BLUE)
        self.c.setLineWidth(1.5)
        self.c.line(MARGIN_LEFT, self.y - 3, PAGE_W - MARGIN_RIGHT, self.y - 3)
        self.y -= 20

    def draw_entry_header(self, left_text, right_text, bold=True):
        """绘制条目头部（左侧加粗文字，右侧日期/地点）"""
        self.check_space(20)
        font = CHINESE_BOLD if bold else CHINESE_FONT
        self.c.setFont(font, 10.5)
        self.c.setFillColor(BLACK)
        self.c.drawString(MARGIN_LEFT + 4, self.y, left_text)
        self.c.setFont(CHINESE_FONT, 9.5)
        self.c.setFillColor(GRAY)
        self.c.drawRightString(PAGE_W - MARGIN_RIGHT, self.y, right_text)
        self.y -= 16

    def draw_sub_header(self, left_text, right_text):
        """绘制子头部（职位，日期）"""
        self.c.setFont(CHINESE_FONT, 9.5)
        self.c.setFillColor(DARK_GRAY)
        self.c.drawString(MARGIN_LEFT + 4, self.y, left_text)
        self.c.drawRightString(PAGE_W - MARGIN_RIGHT, self.y, right_text)
        self.y -= 14

    def draw_bullet(self, text, indent=12):
        """绘制 bullet 点"""
        self.check_space(30)
        x = MARGIN_LEFT + indent

        # Bullet 符号
        self.c.setFont(CHINESE_FONT, 8)
        self.c.setFillColor(BLUE)
        self.c.drawString(x - 8, self.y, "●")

        # 文字（自动换行）
        self.c.setFont(CHINESE_FONT, 9.5)
        self.c.setFillColor(DARK_GRAY)
        self._draw_wrapped_text(text, x, self.y, CONTENT_W - indent - 5, 13)
        self.y -= 2  # 额外间距

    def draw_skill_item(self, category, content):
        """绘制技能条目"""
        self.check_space(15)
        x = MARGIN_LEFT + 12
        self.c.setFont(CHINESE_FONT, 8)
        self.c.setFillColor(BLUE)
        self.c.drawString(x - 8, self.y, "●")
        self.c.setFont(CHINESE_BOLD, 9.5)
        self.c.setFillColor(BLACK)
        self.c.drawString(x, self.y, category)
        cat_w = self.c.stringWidth(category, CHINESE_BOLD, 9.5)
        self.c.setFont(CHINESE_FONT, 9.5)
        self.c.setFillColor(DARK_GRAY)
        self.c.drawString(x + cat_w, self.y, content)
        self.y -= 15

    def _draw_wrapped_text(self, text, x, y, max_width, line_height):
        """绘制自动换行文本"""
        # 按字符逐个测量，找到断行点
        current_line = ""
        for char in text:
            test_line = current_line + char
            if self.c.stringWidth(test_line, CHINESE_FONT, 9.5) > max_width:
                self.c.drawString(x, y, current_line)
                y -= line_height
                self.y -= line_height
                current_line = char
            else:
                current_line = test_line
        if current_line:
            self.c.drawString(x, y, current_line)

    def save(self):
        self.c.save()
        print(f"Saved to: {OUTPUT_PATH}")


# ============================================================
# 简历内容
# ============================================================

def build_resume():
    builder = ResumeBuilder(OUTPUT_PATH)

    # ---- 姓名 ----
    builder.draw_name("陈广凯")

    # ---- 联系方式 ----
    builder.draw_contact([
        "chen@example.com",
        "+86 138-xxxx-xxxx",
        "广东深圳",
        "GitHub: github.com/23xxCh",
    ])

    # ---- 教育背景 ----
    builder.draw_section("教育背景")

    builder.draw_entry_header(
        "深圳大学  计算机科学与技术学院",
        "广东 深圳"
    )
    builder.draw_sub_header(
        "本科  |  计算机科学与技术（人工智能方向）",
        "2020.09 – 2024.06"
    )
    builder.draw_bullet("GPA: 3.5/4.0  |  核心课程：机器学习、深度学习、计算机视觉、自然语言处理、数据库系统")
    builder.y -= 6

    # ---- 项目经历 ----
    builder.draw_section("项目经历")

    # 项目 1
    builder.draw_entry_header(
        "工业知识助手 — 基于 RAG 的制造业智能问答系统",
        "2026.05"
    )
    builder.draw_bullet("主导架构设计与开发，基于 Spring Boot + Vue 3 + Python RAG 引擎，构建覆盖设备手册、工艺参数、SOP 文档的智能问答平台。")
    builder.draw_bullet("实现 Hybrid RRF 检索（Dense 向量 + BM25 关键词 + RRF 融合），技术文档检索精度相比单一方法提升 25%。")
    builder.draw_bullet("开发工业文档专用解析器，支持 PDF 设备手册（故障代码表提取）、Excel 工艺参数表（结构化）、Word SOP 文档（步骤序列提取）。")
    builder.draw_bullet("构建 Agent 工具层，对接 SCADA/MES/设备参数系统，支持 LLM Function Calling 实现设备参数实时查询。")
    builder.draw_bullet("开发 Python RAG 引擎（Chroma + FastAPI），支持 SSE 流式对话、多轮上下文问题改写、引用溯源 [source: file, p.N]。")
    builder.draw_bullet("Vue 3 前端：深色工业风格对话界面、QA 知识库管理、设备参数管理、故障代码管理，TypeScript 全栈类型安全。")
    builder.draw_bullet("技术栈：Spring Boot 3 / Vue 3 / Python / Chroma / BM25 / FastAPI / Docker / PostgreSQL")
    builder.y -= 6

    # 项目 2
    builder.draw_entry_header(
        "智能质检系统 — 工业产品表面缺陷检测",
        "2025.10"
    )
    builder.draw_bullet("基于 YOLOv8 + ResNet 构建视觉检测模型，实现产品表面划痕、色差、尺寸偏差自动检测，准确率达 96.3%。")
    builder.draw_bullet("开发 Docker 容器化部署方案，Flask API 封装模型推理服务，对接 MES 系统质检模块实现数据闭环。")
    builder.draw_bullet("编写检测 SOP 文档和运维手册，输出可复用的训练-部署-优化流程。")
    builder.y -= 6

    # 项目 3
    builder.draw_entry_header(
        "设备预测性维护平台 — 时序异常检测",
        "2025.06"
    )
    builder.draw_bullet("基于 LSTM + Transformer 构建设备故障预测模型，利用振动/温度/电流传感器时序数据，实现故障提前 4 小时预警。")
    builder.draw_bullet("对接 SCADA 系统自动采集设备数据，Redis 缓存实时状态，MySQL 存储历史记录，Docker Compose 一键部署。")
    builder.draw_bullet("输出故障预测准确率 89%、维护计划 SOP，年维护成本降低约 30%。")
    builder.y -= 6

    # ---- 技能 ----
    builder.draw_section("专业技能")

    builder.draw_skill_item("编程语言：", "Python（主力）、Java、JavaScript/TypeScript、SQL")
    builder.draw_skill_item("AI/ML 框架：", "PyTorch、TensorFlow、Scikit-learn、LangChain、LlamaIndex")
    builder.draw_skill_item("大模型应用：", "RAG 架构、Agent/Function Calling、Prompt Engineering、模型微调")
    builder.draw_skill_item("后端技术：", "Spring Boot、FastAPI、Docker、MySQL、Redis、PostgreSQL、Linux")
    builder.draw_skill_item("前端技术：", "Vue 3、TypeScript、Element Plus、Vite")
    builder.draw_skill_item("工业系统：", "MES/ERP/SCADA 对接经验、工业数据采集与处理")
    builder.draw_skill_item("工具链：", "Git、Docker Compose、Maven、pnpm、Nginx")
    builder.y -= 6

    # ---- 自我评价 ----
    builder.draw_section("自我评价")
    builder.draw_bullet("2 年制造业 AI 项目落地经验，熟悉生产、质检、设备运维等制造场景的 AI 需求调研、方案设计与部署上线全流程。")
    builder.draw_bullet("具备跨部门沟通协作能力，能快速响应并推进项目落地，善于将复杂技术方案转化为可执行的 SOP 文档。")

    builder.save()


if __name__ == "__main__":
    build_resume()
