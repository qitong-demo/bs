"""把本项目的 Markdown 说明文档转换为排版规范的 Word（.docx）。

排版口径（对齐学校本科毕业设计撰写规范）：
  正文     宋体 / Times New Roman 小四（12pt），固定行距 23 磅，首行缩进 2 字符
  一级标题 黑体 三号（16pt）居中
  二级标题 黑体 小三（15pt）左对齐
  三级标题 黑体 四号（14pt）左对齐
  表格     宋体 五号（10.5pt）三线以外用全框线，表头加粗并跨页重复
  引用块   楷体/灰色、左右缩进，无首行缩进

用法：
  python scripts/md_to_docx.py <input.md> <output.docx>
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

CN_FONT = "宋体"
CN_HEAD_FONT = "黑体"
EN_FONT = "Times New Roman"
MONO_FONT = "Consolas"
BODY_SIZE = Pt(12)
TABLE_SIZE = Pt(9)
CODE_COLOR = RGBColor(0x60, 0x60, 0x60)
QUOTE_COLOR = RGBColor(0x44, 0x44, 0x44)


# ---------------------------------------------------------------- 底层工具


def set_run_font(run, cn=CN_FONT, en=EN_FONT, size=BODY_SIZE, bold=None):
    run.font.name = en
    run.font.size = size
    if bold is not None:
        run.font.bold = bold
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), en)
    rfonts.set(qn("w:hAnsi"), en)
    rfonts.set(qn("w:eastAsia"), cn)


def set_first_line_indent(paragraph, chars: int = 2, size_pt: float = 12.0):
    """按「N 字符」设置首行缩进（东亚版式）。"""
    ppr = paragraph._p.get_or_add_pPr()
    ind = ppr.find(qn("w:ind"))
    if ind is None:
        ind = OxmlElement("w:ind")
        ppr.append(ind)
    ind.set(qn("w:firstLineChars"), str(chars * 100))
    ind.set(qn("w:firstLine"), str(int(chars * size_pt * 20)))


def set_fixed_line_spacing(paragraph, pt: float = 23.0):
    pf = paragraph.paragraph_format
    pf.line_spacing_rule = WD_LINE_SPACING.EXACTLY
    pf.line_spacing = Pt(pt)
    pf.space_before = Pt(0)
    pf.space_after = Pt(0)


def style_font(style, cn=CN_FONT, en=EN_FONT, size=BODY_SIZE, bold=None):
    style.font.name = en
    style.font.size = size
    if bold is not None:
        style.font.bold = bold
    rpr = style.element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), en)
    rfonts.set(qn("w:hAnsi"), en)
    rfonts.set(qn("w:eastAsia"), cn)


def repeat_header_row(row):
    tr_pr = row._tr.get_or_add_trPr()
    header = OxmlElement("w:tblHeader")
    header.set(qn("w:val"), "true")
    tr_pr.append(header)


def add_field(paragraph, instruction: str):
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = instruction
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.append(begin)
    run._r.append(instr)
    run._r.append(end)
    set_run_font(run, size=Pt(9))


# ---------------------------------------------------------------- 行内解析

INLINE_RE = re.compile(r"(\*\*.+?\*\*|`[^`]+`)")


def add_inline(paragraph, text: str, size=BODY_SIZE, cn=CN_FONT, bold_all=False):
    text = text.replace("\\|", "|")
    for piece in INLINE_RE.split(text):
        if not piece:
            continue
        if piece.startswith("**") and piece.endswith("**") and len(piece) > 4:
            run = paragraph.add_run(piece[2:-2])
            set_run_font(run, cn=cn, size=size, bold=True)
        elif piece.startswith("`") and piece.endswith("`") and len(piece) > 2:
            run = paragraph.add_run(piece[1:-1])
            set_run_font(run, cn=MONO_FONT, en=MONO_FONT, size=Pt(size.pt - 1.5))
            run.font.color.rgb = CODE_COLOR
        else:
            run = paragraph.add_run(piece)
            set_run_font(run, cn=cn, size=size, bold=True if bold_all else None)


# ---------------------------------------------------------------- 样式准备


def build_styles(doc: Document):
    normal = doc.styles["Normal"]
    style_font(normal, size=BODY_SIZE)
    normal.paragraph_format.line_spacing_rule = WD_LINE_SPACING.EXACTLY
    normal.paragraph_format.line_spacing = Pt(23)
    normal.paragraph_format.space_before = Pt(0)
    normal.paragraph_format.space_after = Pt(0)

    headings = {
        "Heading 1": (Pt(16), WD_ALIGN_PARAGRAPH.CENTER, 12, 6),
        "Heading 2": (Pt(15), WD_ALIGN_PARAGRAPH.LEFT, 10, 4),
        "Heading 3": (Pt(14), WD_ALIGN_PARAGRAPH.LEFT, 8, 3),
        "Heading 4": (Pt(12), WD_ALIGN_PARAGRAPH.LEFT, 6, 2),
    }
    for name, (size, align, before, after) in headings.items():
        style = doc.styles[name]
        style_font(style, cn=CN_HEAD_FONT, size=size, bold=True)
        style.font.color.rgb = RGBColor(0, 0, 0)
        style.paragraph_format.alignment = align
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.line_spacing_rule = WD_LINE_SPACING.EXACTLY
        style.paragraph_format.line_spacing = Pt(size.pt + 8)
        style.paragraph_format.first_line_indent = Pt(0)
        # 去掉 Word 内置标题样式自带的蓝色
        rpr = style.element.get_or_add_rPr()
        color = rpr.find(qn("w:color"))
        if color is None:
            color = OxmlElement("w:color")
            rpr.append(color)
        color.set(qn("w:val"), "000000")

    title_style = doc.styles["Title"]
    style_font(title_style, cn=CN_HEAD_FONT, size=Pt(22), bold=True)
    title_style.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title_style.paragraph_format.space_after = Pt(6)
    rpr = title_style.element.get_or_add_rPr()
    color = rpr.find(qn("w:color"))
    if color is None:
        color = OxmlElement("w:color")
        rpr.append(color)
    color.set(qn("w:val"), "000000")


# ---------------------------------------------------------------- 块级转换

TABLE_SEP_RE = re.compile(r"^\|[\s:\-|]+\|$")


def split_row(line: str) -> list[str]:
    line = line.strip()
    if line.startswith("|"):
        line = line[1:]
    if line.endswith("|"):
        line = line[:-1]
    return [cell.strip() for cell in line.split("|")]


def visual_len(text: str) -> int:
    """按视觉宽度估算：中文字符算 2，其余算 1（粗体标记不计入）。"""
    text = re.sub(r"\*\*|`", "", text)
    width = 0
    for ch in text:
        width += 2 if ord(ch) > 0x2E7F else 1
    return width


def set_table_width(table, weights: list[int], total_cm: float = 16.0):
    """按列内容权重给表格设定固定列宽，保证总宽不超过版心。"""
    total_weight = sum(weights) or len(weights)
    total_twips = int(total_cm * 567)
    widths = [max(1, int(total_twips * w / total_weight)) for w in weights]
    # 修正取整误差
    widths[-1] += total_twips - sum(widths)

    tbl_pr = table._tbl.tblPr
    for tag in ("w:tblW", "w:tblLayout"):
        old = tbl_pr.find(qn(tag))
        if old is not None:
            tbl_pr.remove(old)
    tbl_w = OxmlElement("w:tblW")
    tbl_w.set(qn("w:type"), "dxa")
    tbl_w.set(qn("w:w"), str(total_twips))
    tbl_pr.append(tbl_w)
    layout = OxmlElement("w:tblLayout")
    layout.set(qn("w:type"), "fixed")
    tbl_pr.append(layout)

    grid = table._tbl.find(qn("w:tblGrid"))
    if grid is not None:
        table._tbl.remove(grid)
    grid = OxmlElement("w:tblGrid")
    for width in widths:
        col = OxmlElement("w:gridCol")
        col.set(qn("w:w"), str(width))
        grid.append(col)
    table._tbl.insert(list(table._tbl).index(tbl_pr) + 1, grid)

    for row in table.rows:
        for idx, cell in enumerate(row.cells):
            if idx < len(widths):
                cell.width = Pt(widths[idx] / 20)


def flush_table(doc: Document, rows: list[list[str]]):
    if not rows:
        return
    header, body = rows[0], rows[1:]
    table = doc.add_table(rows=1, cols=len(header))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False

    for idx, text in enumerate(header):
        cell = table.rows[0].cells[idx]
        cell.text = ""
        para = cell.paragraphs[0]
        para.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_fixed_line_spacing(para, 15)
        add_inline(para, text, size=TABLE_SIZE, cn=CN_HEAD_FONT, bold_all=True)
    repeat_header_row(table.rows[0])

    for row_data in body:
        row = table.add_row()
        for idx in range(len(header)):
            cell = row.cells[idx]
            cell.text = ""
            para = cell.paragraphs[0]
            set_fixed_line_spacing(para, 15)
            add_inline(
                para,
                row_data[idx] if idx < len(row_data) else "",
                size=TABLE_SIZE,
            )

    weights = []
    for idx in range(len(header)):
        longest = visual_len(header[idx])
        for row_data in body:
            if idx < len(row_data):
                longest = max(longest, visual_len(row_data[idx]))
        weights.append(min(max(longest, 8), 46))
    set_table_width(table, weights)
    doc.add_paragraph()


def convert(md_path: Path, docx_path: Path):
    lines = md_path.read_text(encoding="utf-8").splitlines()
    doc = Document()
    build_styles(doc)

    section = doc.sections[0]
    section.page_width = Cm(21)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(2.5)
    section.bottom_margin = Cm(2.5)
    section.left_margin = Cm(2.5)
    section.right_margin = Cm(2.5)
    section.header_distance = Cm(1.5)
    section.footer_distance = Cm(1.5)

    footer_para = section.footer.paragraphs[0]
    footer_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    add_field(footer_para, " PAGE ")

    table_buffer: list[list[str]] = []
    title_done = False

    def flush():
        nonlocal table_buffer
        if table_buffer:
            flush_table(doc, table_buffer)
            table_buffer = []

    for raw in lines:
        line = raw.rstrip()

        if line.startswith("|"):
            if TABLE_SEP_RE.match(line):
                continue
            table_buffer.append(split_row(line))
            continue
        flush()

        if not line.strip():
            continue
        if line.strip() in {"---", "***", "___"}:
            continue

        if line.startswith("# "):
            text = line[2:].strip()
            if not title_done:
                doc.add_paragraph(text, style="Title")
                title_done = True
            else:
                doc.add_heading(text, level=1)
            continue

        if line.startswith("#### "):
            doc.add_heading(line[5:].strip(), level=4)
            continue
        if line.startswith("### "):
            doc.add_heading(line[4:].strip(), level=3)
            continue
        if line.startswith("## "):
            doc.add_heading(line[3:].strip(), level=2)
            continue

        if line.startswith(">"):
            text = line.lstrip(">").strip()
            para = doc.add_paragraph()
            pf = para.paragraph_format
            pf.left_indent = Cm(0.75)
            pf.right_indent = Cm(0.75)
            pf.first_line_indent = Pt(0)
            set_fixed_line_spacing(para, 20)
            add_inline(para, text, size=Pt(10.5))
            for run in para.runs:
                run.font.color.rgb = QUOTE_COLOR
            continue

        bullet = re.match(r"^\s*[-*]\s+(.*)$", line)
        if bullet:
            para = doc.add_paragraph(style="List Bullet")
            para.paragraph_format.first_line_indent = Pt(0)
            para.paragraph_format.left_indent = Cm(0.74)
            set_fixed_line_spacing(para, 23)
            add_inline(para, bullet.group(1))
            continue

        numbered = re.match(r"^\s*(\d+)[.、]\s+(.*)$", line)
        if numbered:
            para = doc.add_paragraph(style="List Number")
            para.paragraph_format.first_line_indent = Pt(0)
            para.paragraph_format.left_indent = Cm(0.74)
            set_fixed_line_spacing(para, 23)
            add_inline(para, numbered.group(2))
            continue

        para = doc.add_paragraph()
        set_fixed_line_spacing(para, 23)
        set_first_line_indent(para, 2, 12)
        add_inline(para, line.strip())

    flush()
    doc.save(str(docx_path))
    return doc


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    src = Path(sys.argv[1]).resolve()
    dst = Path(sys.argv[2]).resolve()
    if not src.is_file():
        print(f"找不到输入文件：{src}")
        return 1
    dst.parent.mkdir(parents=True, exist_ok=True)
    doc = convert(src, dst)
    print(f"已生成：{dst}")
    print(f"段落数：{len(doc.paragraphs)}，表格数：{len(doc.tables)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
