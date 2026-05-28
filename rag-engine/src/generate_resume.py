"""
陈熙贤 - 智能制造工程师 简历生成器
"""
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib.colors import HexColor
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
import os

OUTPUT_PATH = os.path.join(os.path.dirname(__file__), "..", "CV-陈熙贤-智能制造工程师.pdf")

BLACK = HexColor("#000000")
DARK_GRAY = HexColor("#333333")
GRAY = HexColor("#666666")
BLUE = HexColor("#1a5276")

PAGE_W, PAGE_H = A4
ML = 50
MR = 50
MT = 40
CW = PAGE_W - ML - MR

CHINESE_FONT = "Helvetica"
CHINESE_BOLD = "Helvetica-Bold"
for fp, fn in [("C:/Windows/Fonts/msyh.ttc","MSYH"),("C:/Windows/Fonts/msyhbd.ttc","MSYHBD"),("C:/Windows/Fonts/simhei.ttf","SimHei")]:
    if os.path.exists(fp):
        try:
            pdfmetrics.registerFont(TTFont(fn, fp))
            if "bd" in fp: CHINESE_BOLD = fn
            elif "msyh" in fp: CHINESE_FONT = fn
            elif "simhei" in fp: CHINESE_FONT = fn; CHINESE_BOLD = fn
        except: pass

class RB:
    def __init__(self, fn):
        self.c = canvas.Canvas(fn, pagesize=A4)
        self.y = PAGE_H - MT
    def ck(self, n=30):
        if self.y < MT + n: self.c.showPage(); self.y = PAGE_H - MT
    def name(self, t):
        self.c.setFont(CHINESE_BOLD, 20); self.c.setFillColor(BLACK)
        tw = self.c.stringWidth(t, CHINESE_BOLD, 20)
        self.c.drawString((PAGE_W-tw)/2, self.y, t); self.y -= 25
    def contact(self, items):
        self.c.setFont(CHINESE_FONT, 9); self.c.setFillColor(GRAY)
        line = "  \u2022  ".join(items)
        tw = self.c.stringWidth(line, CHINESE_FONT, 9)
        self.c.drawString((PAGE_W-tw)/2, self.y, line); self.y -= 22
    def sec(self, t):
        self.ck(40); self.c.setFont(CHINESE_BOLD, 13); self.c.setFillColor(BLUE)
        self.c.drawString(ML, self.y, t)
        self.c.setStrokeColor(BLUE); self.c.setLineWidth(1.5)
        self.c.line(ML, self.y-3, PAGE_W-MR, self.y-3); self.y -= 20
    def hdr(self, l, r, bold=True):
        self.ck(20); f = CHINESE_BOLD if bold else CHINESE_FONT
        self.c.setFont(f, 10.5); self.c.setFillColor(BLACK)
        self.c.drawString(ML+4, self.y, l)
        self.c.setFont(CHINESE_FONT, 9.5); self.c.setFillColor(GRAY)
        self.c.drawRightString(PAGE_W-MR, self.y, r); self.y -= 16
    def sub(self, l, r):
        self.c.setFont(CHINESE_FONT, 9.5); self.c.setFillColor(DARK_GRAY)
        self.c.drawString(ML+4, self.y, l)
        self.c.drawRightString(PAGE_W-MR, self.y, r); self.y -= 14
    def bul(self, t, indent=12):
        self.ck(30); x = ML + indent
        self.c.setFont(CHINESE_FONT, 8); self.c.setFillColor(BLUE)
        self.c.drawString(x-8, self.y, "\u25cf")
        self.c.setFont(CHINESE_FONT, 9.5); self.c.setFillColor(DARK_GRAY)
        cl = ""
        for ch in t:
            tl = cl + ch
            if self.c.stringWidth(tl, CHINESE_FONT, 9.5) > CW - indent - 5:
                self.c.drawString(x, self.y, cl); self.y -= 13; cl = ch
            else: cl = tl
        if cl: self.c.drawString(x, self.y, cl)
        self.y -= 2
    def sk(self, cat, con):
        self.ck(15); x = ML + 12
        self.c.setFont(CHINESE_FONT, 8); self.c.setFillColor(BLUE)
        self.c.drawString(x-8, self.y, "\u25cf")
        self.c.setFont(CHINESE_BOLD, 9.5); self.c.setFillColor(BLACK)
        self.c.drawString(x, self.y, cat)
        cw = self.c.stringWidth(cat, CHINESE_BOLD, 9.5)
        self.c.setFont(CHINESE_FONT, 9.5); self.c.setFillColor(DARK_GRAY)
        self.c.drawString(x+cw, self.y, con); self.y -= 15
    def save(self): self.c.save(); print(f"Saved: {OUTPUT_PATH}")

def main():
    b = RB(OUTPUT_PATH)
    b.name("\u9648\u7199\u8d24")
    b.contact(["23xxchen@stu.edu.cn","+86 137-9061-3670","\u5e7f\u4e1c\u6c55\u5934","GitHub: github.com/23xxCh"])

    b.sec("\u6559\u80b2\u80cc\u666f")
    b.hdr("\u6c55\u5934\u5927\u5b66  \u5de5\u5b66\u9662  \u667a\u80fd\u5236\u9020\u5de5\u7a0b","\u5e7f\u4e1c \u6c55\u5934")
    b.sub("\u672c\u79d1  |  \u667a\u80fd\u5236\u9020\u5de5\u7a0b","2023.09 \u2013 2027.06\uff08\u9884\u8ba1\uff09")
    b.bul("\u6838\u5fc3\u8bfe\u7a0b\uff1a\u7cbe\u76ca\u751f\u4ea7\u3001\u5148\u8fdb\u5236\u9020\u3001\u673a\u5668\u4eba\u6280\u672f\u3001\u4fe1\u53f7\u4e0e\u7cfb\u7edf\u3001\u4ea7\u54c1\u8bbe\u8ba1\u3001\u673a\u5668\u5b66\u4e60\u3001\u5d4c\u5165\u5f0f\u7cfb\u7edf\u3001\u673a\u5668\u89c6\u89c9\u539f\u7406")
    b.bul("\u82f1\u8bed\u56db\u7ea7 524 \u5206\uff0c\u5177\u5907\u82f1\u6587\u6280\u672f\u6587\u6863\u9605\u8bfb\u80fd\u529b")
    b.y -= 6

    b.sec("\u5b9e\u4e60\u7ecf\u5386")
    b.hdr("\u4f1f\u6613\u8fbe\uff08\u4e1c\u839e\uff09\u7535\u5b50\u4ea7\u54c1\u6709\u9650\u516c\u53f8","\u5e7f\u4e1c \u4e1c\u839e")
    b.sub("\u673a\u68b0\u7ed3\u6784\u5b9e\u4e60\u751f","2025 \u6691\u671f")
    b.bul("\u53c2\u4e0e\u73a9\u5177\u4ea7\u54c1\u4ea7\u7ebf\u5b9e\u4e60\uff0c\u4e86\u89e3\u6ce8\u5851\u3001\u7ec4\u88c5\u3001\u8d28\u68c0\u7b49\u5b8c\u6574\u751f\u4ea7\u6d41\u7a0b\uff0c\u6df1\u5165\u5b66\u4e60\u6a21\u5177\u7ed3\u6784\u4e0e\u6210\u578b\u5de5\u827a\u3002")
    b.bul("\u4f7f\u7528 Creo \u8fdb\u884c\u96f6\u90e8\u4ef6 3D \u5efa\u6a21\u4e0e\u88c5\u914d\u8bbe\u8ba1\uff0c\u914d\u5408\u6a21\u5177\u77e5\u8bc6\u5b8c\u6210\u4ea7\u54c1\u7ed3\u6784\u4f18\u5316\u65b9\u6848\u3002")
    b.bul("\u4ea7\u7ebf\u73b0\u573a\u89c2\u5bdf\u4e0e\u8bb0\u5f55\uff0c\u534f\u52a9\u5206\u6790\u751f\u4ea7\u74f6\u9888\uff0c\u63d0\u51fa\u57fa\u4e8e\u7cbe\u76ca\u751f\u4ea7\u7684\u6539\u8fdb\u5efa\u8bae\u3002")
    b.y -= 6

    b.sec("\u9879\u76ee\u7ecf\u5386")
    # 项目1
    b.hdr("\u4e00\u9274\u949f\u6c22 \u2014 \u57fa\u4e8e\u5149\u58f0\u8870\u8361\u5149\u8c31\u7684\u6c22\u6c14\u68c0\u6d4b\u6280\u672f","2025")
    b.bul("\u6311\u6218\u676f\u5e7f\u4e1c\u7701\u7701\u8d5b\u4e00\u7b49\u5956\u9879\u76ee\u3002\u8d1f\u8d23\u5149\u58f0\u8870\u8361\u5149\u8c31\u68c0\u6d4b\u7cfb\u7edf\u7684\u786c\u4ef6\u642d\u5efa\u4e0e\u4fe1\u53f7\u5904\u7406\u3002")
    b.bul("\u4f7f\u7528 MATLAB \u8fdb\u884c\u5149\u8c31\u4fe1\u53f7\u91c7\u96c6\u4e0e\u6570\u636e\u5206\u6790\uff0c\u4f18\u5316\u68c0\u6d4b\u7cbe\u5ea6\uff0c\u5b9e\u73b0 ppb \u7ea7\u6c22\u6c14\u6d53\u5ea6\u68c0\u6d4b\u3002")
    b.bul("\u64b0\u5199\u6280\u672f\u6587\u6863\u4e0e\u5b9e\u9a8c\u62a5\u544a\uff0c\u53c2\u4e0e\u8def\u6f14\u7b54\u8fa9\uff0c\u4ece 200+ \u652f\u961f\u4f0d\u4e2d\u8131\u9896\u800c\u51fa\u3002")
    b.y -= 6
    # 项目2
    b.hdr("\u57fa\u4e8e ROS2 \u7684\u6c22\u6c14\u68c0\u6d4b\u81ea\u4e3b\u5de1\u68c0\u5c0f\u8f66","2025")
    b.bul("\u57fa\u4e8e ROS2 \u6846\u67b6\u5f00\u53d1\u81ea\u4e3b\u5de1\u68c0\u673a\u5668\u4eba\uff0c\u96c6\u6210\u6c22\u6c14\u4f20\u611f\u5668\u6a21\u5757\uff0c\u5b9e\u73b0\u5371\u9669\u73af\u5883\u81ea\u52a8\u68c0\u6d4b\u4e0e\u9884\u8b66\u3002")
    b.bul("\u5b8c\u6210\u5bfc\u822a\u5efa\u56fe\uff08SLAM\uff09\u3001\u8def\u5f84\u89c4\u5212\u3001\u4f20\u611f\u5668\u878d\u5408\u7b49\u6838\u5fc3\u6a21\u5757\u5f00\u53d1\u4e0e\u8c03\u8bd5\u3002")
    b.bul("\u786c\u4ef6\u9009\u578b\u4e0e\u5d4c\u5165\u5f0f\u7cfb\u7edf\u5f00\u53d1\uff0cESP32 \u4e3b\u63a7 + \u4f20\u611f\u5668\u901a\u4fe1\uff0cPCB \u753b\u677f\uff08\u5609\u7acb\u521b EDA\uff09\u3002")
    b.y -= 6
    # 项目3
    b.hdr("AI Agent \u667a\u80fd\u5bf9\u8bdd\u673a\u5668\u4eba","2025")
    b.bul("\u57fa\u4e8e\u5c0f\u667a AI \u4e0e ESP-Claw \u6846\u67b6\u5f00\u53d1\u667a\u80fd\u5bf9\u8bdd\u673a\u5668\u4eba\uff0c\u96c6\u6210\u8bed\u97f3\u8bc6\u522b\u4e0e\u5927\u6a21\u578b\u63a8\u7406\u80fd\u529b\u3002")
    b.bul("\u5b9e\u73b0\u786c\u4ef6\u7aef\uff08ESP32\uff09\u4e0e\u4e91\u7aef\u5927\u6a21\u578b\u7684\u5b9e\u65f6\u901a\u4fe1\uff0c\u652f\u6301\u591a\u8f6e\u5bf9\u8bdd\u4e0e\u8bed\u97f3\u4ea4\u4e92\u3002")
    b.bul("\u5177\u5907 AI Agent \u5f00\u53d1\u7ecf\u9a8c\uff0c\u719f\u6089 Claude Code\u3001Codex \u7b49 AI \u8f85\u52a9\u7f16\u7a0b\u5de5\u5177\u3002")
    b.y -= 6
    # 项目4
    b.hdr("\u6570\u636e\u5206\u6790\u4e0e\u9884\u6d4b\u5efa\u6a21","2024")
    b.bul("\u5bf9\u6c7d\u8f66\u9500\u552e\u6570\u636e\u8fdb\u884c\u6e05\u6d17\u3001\u7279\u5f81\u5de5\u7a0b\u4e0e\u53ef\u89c6\u5316\u5206\u6790\uff0c\u4f7f\u7528 Python \u6784\u5efa\u56de\u5f52\u9884\u6d4b\u6a21\u578b\u3002")
    b.bul("\u57fa\u4e8e MATLAB \u5b8c\u6210\u6865\u6881\u9759\u529b\u5b66\u6709\u9650\u5143\u5206\u6790\uff08\u4f0f\u56fe\u8f6f\u4ef6\uff09\uff0c\u8f93\u51fa\u7ed3\u6784\u5e94\u529b\u5206\u5e03\u62a5\u544a\u3002")
    b.bul("\u4f7f\u7528\u5609\u7acb\u521b EDA \u5b8c\u6210 PCB \u7535\u8def\u8bbe\u8ba1\u4e0e\u6253\u6837\uff0c\u4e86\u89e3\u535a\u9014\u8f6f\u4ef6\u5e76\u5b8c\u6210 PLC \u7a0b\u5e8f\u7f16\u5199\u3002")
    b.y -= 6

    b.sec("\u4e13\u4e1a\u6280\u80fd")
    b.sk("CAD/\u5efa\u6a21\uff1a","SolidWorks\uff08\u719f\u7ec3\uff09\u3001Creo\uff08\u719f\u7ec3\uff09\u3001UG\uff08\u719f\u7ec3\uff09\u3001AutoCAD\uff08\u719f\u7ec3\uff09\u3001Moldex \u6a21\u6d41\u5206\u6790")
    b.sk("\u7f16\u7a0b\u8bed\u8a00\uff1a","Python\uff08\u4e3b\u529b\uff09\u3001C/C++\uff08\u5d4c\u5165\u5f0f\uff09\u3001MATLAB\uff08\u6570\u636e\u5206\u6790\u4e0e\u5efa\u6a21\uff09")
    b.sk("\u673a\u5668\u4eba\u5f00\u53d1\uff1a","ROS2 \u6846\u67b6\u3001SLAM \u5bfc\u822a\u5efa\u56fe\u3001\u8def\u5f84\u89c4\u5212\u3001\u4f20\u611f\u5668\u878d\u5408")
    b.sk("\u5d4c\u5165\u5f0f\u786c\u4ef6\uff1a","ESP32 \u5f00\u53d1\u3001PCB \u8bbe\u8ba1\uff08\u5609\u7acb\u521b EDA\uff09\u3001PLC \u7f16\u7a0b\uff08\u535a\u9014\uff09")
    b.sk("AI \u5de5\u5177\uff1a","Claude Code\u3001Codex \u7b49 AI \u8f85\u52a9\u7f16\u7a0b\u5de5\u5177\uff0cAI Agent \u5f00\u53d1\u7ecf\u9a8c")
    b.sk("\u4eff\u771f\u5206\u6790\uff1a","\u4f0f\u56fe\u8f6f\u4ef6\uff08\u6709\u9650\u5143\u5206\u6790\uff09\u3001MATLAB \u4fe1\u53f7\u5904\u7406")
    b.sk("\u529e\u516c\u8f6f\u4ef6\uff1a","Office \u5168\u5bb6\u6876\uff08Word/Excel/PPT \u719f\u7ec3\u4f7f\u7528\uff09")
    b.y -= 6

    b.sec("\u83b7\u5956\u4e0e\u8363\u8a89")
    b.bul("\u6311\u6218\u676f\u5e7f\u4e1c\u7701\u7701\u8d5b\u4e00\u7b49\u5956 - \u4e00\u9274\u949f\u6c22\uff08\u5149\u58f0\u8870\u8361\u5149\u8c31\u6c22\u6c14\u68c0\u6d4b\u6280\u672f\uff09")
    b.bul("GitHub \u4e2a\u4eba\u4ed3\u5e93 20+ \u4e2a\u9879\u76ee\uff0c\u6db5\u76d6\u673a\u5668\u4eba\u3001\u5d4c\u5165\u5f0f\u3001\u6570\u636e\u5206\u6790\u3001AI \u5e94\u7528\u7b49\u65b9\u5411")
    b.y -= 6

    b.sec("\u81ea\u6211\u8bc4\u4ef7")
    b.bul("\u667a\u80fd\u5236\u9020\u5de5\u7a0b\u4e13\u4e1a\u80cc\u666f\uff0c\u5177\u5907\u673a\u68b0\u7ed3\u6784\u8bbe\u8ba1 + \u5d4c\u5165\u5f0f\u5f00\u53d1 + AI \u5e94\u7528\u7684\u590d\u5408\u80fd\u529b\u3002")
    b.bul("\u81ea\u9a71\u529b\u5f3a\uff0c\u5916\u5411\u5f00\u6717\uff0c\u56e2\u961f\u534f\u4f5c\u80fd\u529b\u4f18\u79c0\uff0c\u5584\u4e8e\u5c06\u6280\u672f\u65b9\u6848\u843d\u5730\u4e3a\u53ef\u6267\u884c\u7684\u4ea7\u54c1\u3002")

    b.save()

if __name__ == "__main__":
    main()
