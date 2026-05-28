"""
陈熙贤 - 智能制造工程师 简历生成器
使用 word-wrap 方式处理中文换行，避免字符重叠
"""
from reportlab.lib.pagesizes import A4
from reportlab.lib.colors import HexColor
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.enums import TA_LEFT, TA_CENTER
import os

OUTPUT_PATH = os.path.join(os.path.dirname(__file__), "..", "CV-陈熙贤-v3.pdf")

BLACK = HexColor("#000000")
DARK = HexColor("#333333")
GRAY = HexColor("#666666")
BLUE = HexColor("#1a5276")

PW, PH = A4  # 595 x 842
ML, MR, MT = 50, 50, 38

# 注册字体
FONT = "SimSun"
BOLD = "SimSun"
for fp, fn in [
    ("C:/Windows/Fonts/simsun.ttc", "SimSun"),
    ("C:/Windows/Fonts/simhei.ttf", "SimHei"),
    ("C:/Windows/Fonts/msyh.ttc", "MSYH"),
]:
    if os.path.exists(fp):
        try:
            pdfmetrics.registerFont(TTFont(fn, fp))
            FONT = fn
            BOLD = fn
            break
        except:
            pass

# 尝试注册微软雅黑（更现代）
for fp, fn in [("C:/Windows/Fonts/msyh.ttc", "MSYH"), ("C:/Windows/Fonts/msyhbd.ttc", "MSYHBD")]:
    if os.path.exists(fp):
        try:
            pdfmetrics.registerFont(TTFont(fn, fp))
            if "bd" in fp:
                BOLD = fn
            else:
                FONT = fn
        except:
            pass

print(f"Font: {FONT}, Bold: {BOLD}")


class CV:
    def __init__(self):
        self.c = canvas.Canvas(OUTPUT_PATH, pagesize=A4)
        self.y = PH - MT

    def _w(self, text, font, size):
        """安全测宽 - 对中文用固定宽度"""
        return self.c.stringWidth(text, font, size)

    def _newpage(self):
        self.c.showPage()
        self.y = PH - MT

    def _ck(self, need=30):
        if self.y < MT + need:
            self._newpage()

    def name(self, text):
        self.c.setFont(BOLD, 22)
        self.c.setFillColor(BLACK)
        w = self._w(text, BOLD, 22)
        self.c.drawString((PW - w) / 2, self.y, text)
        self.y -= 28

    def contact(self, items):
        self.c.setFont(FONT, 9.5)
        self.c.setFillColor(GRAY)
        line = "   \u2022   ".join(items)
        w = self._w(line, FONT, 9.5)
        self.c.drawString((PW - w) / 2, self.y, line)
        self.y -= 24

    def section(self, title):
        self._ck(36)
        self.c.setFont(BOLD, 13.5)
        self.c.setFillColor(BLUE)
        self.c.drawString(ML, self.y, title)
        self.c.setStrokeColor(BLUE)
        self.c.setLineWidth(1.5)
        self.c.line(ML, self.y - 4, PW - MR, self.y - 4)
        self.y -= 22

    def entry(self, left, right):
        self._ck(22)
        self.c.setFont(BOLD, 11)
        self.c.setFillColor(BLACK)
        self.c.drawString(ML + 4, self.y, left)
        self.c.setFont(FONT, 9.5)
        self.c.setFillColor(GRAY)
        self.c.drawRightString(PW - MR, self.y, right)
        self.y -= 20

    def sub(self, left, right):
        self.c.setFont(FONT, 10)
        self.c.setFillColor(DARK)
        self.c.drawString(ML + 4, self.y, left)
        self.c.setFont(FONT, 9.5)
        self.c.setFillColor(GRAY)
        self.c.drawRightString(PW - MR, self.y, right)
        self.y -= 18

    def bullet(self, text, indent=14):
        self._ck(35)
        x = ML + indent
        # bullet
        self.c.setFont(FONT, 8)
        self.c.setFillColor(BLUE)
        self.c.drawString(x - 9, self.y, "\u25cf")
        # 文字
        self.c.setFont(FONT, 9.5)
        self.c.setFillColor(DARK)
        self._wrap(text, x, CW=PW - MR - x - 2)
        self.y -= 5

    def skill(self, cat, val):
        self._ck(20)
        x = ML + 14
        self.c.setFont(FONT, 8)
        self.c.setFillColor(BLUE)
        self.c.drawString(x - 9, self.y, "\u25cf")
        self.c.setFont(BOLD, 9.5)
        self.c.setFillColor(BLACK)
        self.c.drawString(x, self.y, cat)
        cw = self._w(cat, BOLD, 9.5)
        self.c.setFont(FONT, 9.5)
        self.c.setFillColor(DARK)
        self._wrap(val, x + cw, CW=PW - MR - x - cw - 2)
        self.y -= 4

    def _wrap(self, text, x, CW=400):
        """中文友好换行 - 行高 20 避免重叠"""
        font = FONT
        size = 9.5
        line = ""
        lh = 20  # 行高（微软雅黑需要更大行高）
        for ch in text:
            test = line + ch
            if self._w(test, font, size) > CW and line:
                self.c.drawString(x, self.y, line)
                self.y -= lh
                self._ck(10)
                line = ch
            else:
                line = test
        if line:
            self.c.drawString(x, self.y, line)

    def save(self):
        self.c.save()
        print(f"Saved: {OUTPUT_PATH}")


def main():
    v = CV()

    # 姓名
    v.name("陈熙贤")
    v.contact([
        "23xxchen@stu.edu.cn",
        "+86 137-9061-3670",
        "广东汕头",
        "GitHub: github.com/23xxCh",
    ])

    # 教育
    v.section("教育背景")
    v.entry("汕头大学  工学院  智能制造工程", "广东 汕头")
    v.sub("本科  |  智能制造工程", "2023.09 - 2027.06（预计）")
    v.bullet("核心课程：精益生产、先进制造、机器人技术、信号与系统、产品设计、机器学习、嵌入式系统、机器视觉原理")
    v.bullet("英语四级 524 分，具备英文技术文档阅读能力")
    v.y -= 6

    # 实习
    v.section("实习经历")
    v.entry("伟易达（东莞）电子产品有限公司", "广东 东莞")
    v.sub("机械结构实习生", "2025 暑期")
    v.bullet("参与玩具产品产线实习，了解注塑、组装、质检等完整生产流程，深入学习模具结构与成型工艺。")
    v.bullet("使用 Creo 进行零部件 3D 建模与装配设计，配合模具知识完成产品结构优化方案。")
    v.bullet("产线现场观察与记录，协助分析生产瓶颈，提出基于精益生产的改进建议。")
    v.y -= 6

    # 项目
    v.section("项目经历")

    v.entry("一鉴钟氢 - 基于光声衰荡光谱的氢气检测技术", "2025")
    v.bullet("挑战杯广东省省赛一等奖项目。负责光声衰荡光谱检测系统的硬件搭建与信号处理。")
    v.bullet("使用 MATLAB 进行光谱信号采集与数据分析，优化检测精度，实现 ppb 级氢气浓度检测。")
    v.bullet("撰写技术文档与实验报告，参与路演答辩，从 200+ 支队伍中脱颖而出。")
    v.y -= 6

    v.entry("基于 ROS2 的氢气检测自主巡检小车", "2025")
    v.bullet("基于 ROS2 框架开发自主巡检机器人，集成氢气传感器模块，实现危险环境自动检测与预警。")
    v.bullet("完成导航建图（SLAM）、路径规划、传感器融合等核心模块开发与调试。")
    v.bullet("硬件选型与嵌入式系统开发，ESP32 主控 + 传感器通信，PCB 画板（嘉立创 EDA）。")
    v.y -= 6

    v.entry("AI Agent 智能对话机器人", "2025")
    v.bullet("基于小智 AI 与 ESP-Claw 框架开发智能对话机器人，集成语音识别与大模型推理能力。")
    v.bullet("实现硬件端（ESP32）与云端大模型的实时通信，支持多轮对话与语音交互。")
    v.bullet("具备 AI Agent 开发经验，熟悉 Claude Code、Codex 等 AI 辅助编程工具。")
    v.y -= 6

    v.entry("数据分析与预测建模", "2024")
    v.bullet("对汽车销售数据进行清洗、特征工程与可视化分析，使用 Python 构建回归预测模型。")
    v.bullet("基于 MATLAB 完成桥梁静力学有限元分析（伏图软件），输出结构应力分布报告。")
    v.bullet("使用嘉立创 EDA 完成 PCB 电路设计与打样，了解博途软件并完成 PLC 程序编写。")
    v.y -= 6

    # 技能
    v.section("专业技能")
    v.skill("CAD/建模：", "SolidWorks（熟练）、Creo（熟练）、UG（熟练）、AutoCAD（熟练）、Moldex 模流分析")
    v.skill("编程语言：", "Python（主力）、C/C++（嵌入式）、MATLAB（数据分析与建模）")
    v.skill("机器人开发：", "ROS2 框架、SLAM 导航建图、路径规划、传感器融合")
    v.skill("嵌入式硬件：", "ESP32 开发、PCB 设计（嘉立创 EDA）、PLC 编程（博途）")
    v.skill("AI 工具：", "Claude Code、Codex 等 AI 辅助编程工具，AI Agent 开发经验")
    v.skill("仿真分析：", "伏图软件（有限元分析）、MATLAB 信号处理")
    v.skill("办公软件：", "Office 全家桶（Word/Excel/PPT 熟练使用）")
    v.y -= 6

    # 获奖
    v.section("获奖与荣誉")
    v.bullet("挑战杯广东省省赛一等奖 - 一鉴钟氢（光声衰荡光谱氢气检测技术）")
    v.bullet("GitHub 个人仓库 20+ 个项目，涵盖机器人、嵌入式、数据分析、AI 应用等方向")
    v.y -= 6

    # 自我评价
    v.section("自我评价")
    v.bullet("智能制造工程专业背景，具备机械结构设计 + 嵌入式开发 + AI 应用的复合能力。")
    v.bullet("自驱力强，外向开朗，团队协作能力优秀，善于将技术方案落地为可执行的产品。")

    v.save()


if __name__ == "__main__":
    main()
