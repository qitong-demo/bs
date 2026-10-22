# -*- coding: utf-8 -*-
"""实体属性图生成器（用 ER实体关系图模板.vsdx 的矩形/椭圆主控）

版式：实体矩形居中，属性椭圆分列左右两栏，连线自动从矩形边到椭圆边；
主键属性加下划线（Character.Style = 4）。
"""
import sys, io, os, math, zipfile, importlib.util
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from build_sequence_vsdx import write_png, text_w, LINE_BOX_H

TPL = os.path.join(os.path.dirname(HERE), 'assets', 'er-template.vsdx')
NS = 'http://schemas.microsoft.com/office/visio/2012/main'
LW = 0.75 / 72.0
M_RECT, M_ELLIPSE = '4', '2'      # 流程图过程(矩形) / 属性(椭圆)


def esc(t):
    return t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def num(x):
    return f"{x:.10f}"


def textblock(text, size_pt, underline=False):
    style = "<Cell N='Style' V='4'/>" if underline else ''
    return ("<Section N='Character'><Row IX='0'>"
            f"<Cell N='Size' V='{size_pt/72.0:.10f}'/>"
            f"<Cell N='Font' V='Times New Roman'/><Cell N='AsianFont' V='SimSun'/>"
            + style +
            "</Row></Section>"
            "<Section N='Paragraph'><Row IX='0'><Cell N='HorzAlign' V='1'/>"
            "<Cell N='VertAlign' V='1'/></Row></Section>"
            f"<Text><cp IX='0'/><pp IX='0'/>{esc(text)}</Text>")


def geom_rect(w, h):
    pts = [(w, 0), (w, h), (0, h), (0, 0)]
    rows = ''.join(f"<Row T='LineTo' IX='{i+2}'><Cell N='X' V='{num(px)}'/>"
                   f"<Cell N='Y' V='{num(py)}'/></Row>" for i, (px, py) in enumerate(pts))
    return (f"<Section N='Geometry' IX='0'><Cell N='NoFill' V='1'/><Cell N='NoLine' V='1'/>"
            f"<Row T='MoveTo' IX='1'><Cell N='X' V='0'/><Cell N='Y' V='0'/></Row>{rows}</Section>")


def geom_line(length):
    return (f"<Section N='Geometry' IX='0'><Cell N='NoFill' V='1'/><Cell N='NoLine' V='0'/>"
            f"<Row T='MoveTo' IX='1'><Cell N='X' V='0'/>"
            f"<Cell N='Y' V='{num(LINE_BOX_H/2)}'/></Row>"
            f"<Row T='LineTo' IX='2'><Cell N='X' V='{num(length)}'/>"
            f"<Cell N='Y' V='{num(LINE_BOX_H/2)}'/></Row></Section>")


def inst(sid, master, nameu, x, y, w, h, text, size_pt, underline=False):
    return (f"<Shape ID='{sid}' NameU='{nameu}' Name='{nameu}' Type='Shape' Master='{master}'>"
            f"<Cell N='PinX' V='{num(x)}'/><Cell N='PinY' V='{num(y)}'/>"
            f"<Cell N='Width' V='{num(w)}'/><Cell N='Height' V='{num(h)}'/>"
            f"<Cell N='LocPinX' V='{num(w/2)}' F='Width*0.5'/>"
            f"<Cell N='LocPinY' V='{num(h/2)}' F='Height*0.5'/>"
            f"<Cell N='Angle' V='0'/><Cell N='LineWeight' V='{LW:.10f}'/>"
            + textblock(text, size_pt, underline) + "</Shape>")


def line_shape(sid, x1, y1, x2, y2):
    length = math.hypot(x2 - x1, y2 - y1)
    ang = math.atan2(y2 - y1, x2 - x1)
    return (f"<Shape ID='{sid}' NameU='动态连接线' Name='动态连接线' Type='Shape' "
            f"LineStyle='3' FillStyle='3' TextStyle='3'>"
            f"<Cell N='PinX' V='{num((x1+x2)/2)}'/><Cell N='PinY' V='{num((y1+y2)/2)}'/>"
            f"<Cell N='Width' V='{num(length)}'/><Cell N='Height' V='{num(LINE_BOX_H)}'/>"
            f"<Cell N='LocPinX' V='{num(length/2)}' F='Width*0.5'/>"
            f"<Cell N='LocPinY' V='{num(LINE_BOX_H/2)}' F='Height*0.5'/>"
            f"<Cell N='Angle' V='{ang:.15f}' U='RAD'/>"
            f"<Cell N='BeginX' V='{num(x1)}'/><Cell N='BeginY' V='{num(y1)}'/>"
            f"<Cell N='EndX' V='{num(x2)}'/><Cell N='EndY' V='{num(y2)}'/>"
            f"<Cell N='LineWeight' V='{LW:.10f}'/><Cell N='LinePattern' V='1'/>"
            f"<Cell N='FillPattern' V='0'/><Cell N='BeginArrow' V='0'/><Cell N='EndArrow' V='0'/>"
            f"<Cell N='ResizeMode' V='0'/>" + geom_line(length) + "</Shape>")


def edge_point(cx, cy, ex, ey, hw, hh):
    """从椭圆/矩形中心朝 (ex,ey) 方向，求与包围盒的交点。"""
    dx, dy = ex - cx, ey - cy
    d = math.hypot(dx, dy)
    ux, uy = dx / d, dy / d
    t = min(hw / abs(ux) if ux else 1e9, hh / abs(uy) if uy else 1e9)
    return cx + ux * t, cy + uy * t


def build(sp):
    parts, sid = [], 0
    ename, ex, ey = sp.ENTITY
    ew, eh = sp.ENT_W, sp.ENT_H
    for text, cx, cy, is_pk in sp.ATTRS:
        sx, sy = edge_point(ex, ey, cx, cy, ew/2, eh/2)
        exx, eyy = edge_point(cx, cy, ex, ey, sp.ATTR_W/2, sp.ATTR_H/2)
        sid += 1
        parts.append(line_shape(sid, sx, sy, exx, eyy))
    sid += 1
    parts.append(inst(sid, M_RECT, '流程图过程', ex, ey, ew, eh, ename, sp.FS))
    for text, cx, cy, is_pk in sp.ATTRS:
        sid += 1
        parts.append(inst(sid, M_ELLIPSE, '属性', cx, cy, sp.ATTR_W, sp.ATTR_H, text,
                          sp.FS, underline=is_pk))
    return ("<?xml version='1.0' encoding='utf-8' ?>\n\n"
            f"<PageContents xmlns='{NS}' "
            "xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships' "
            "xml:space='preserve'><Shapes>" + "".join(parts) + "</Shapes></PageContents>")


def patch_pages(xml, sp):
    import re
    pw, ph = sp.PAGE
    xml = re.sub(r"(<Cell N='PageWidth' V=')[\d.]+'", lambda m: m.group(1) + f'{pw:.10f}\'', xml)
    xml = re.sub(r"(<Cell N='PageHeight' V=')[\d.]+'", lambda m: m.group(1) + f'{ph:.10f}\'', xml)
    xml = re.sub(r"ViewCenterX='[\d.]+'", f"ViewCenterX='{pw/2:.4f}'", xml)
    xml = re.sub(r"ViewCenterY='[\d.]+'", f"ViewCenterY='{ph/2:.4f}'", xml)
    return xml


def png_prims(sp):
    ename, ex, ey = sp.ENTITY
    prims = [dict(kind='rect', shape='rect', geom_kind='rect',
                  box=[ex-sp.ENT_W/2, ey-sp.ENT_H/2, ex+sp.ENT_W/2, ey+sp.ENT_H/2],
                  fill='#FFFFFF', line='#000000', text=ename, size=sp.FS, align='center')]
    for text, cx, cy, is_pk in sp.ATTRS:
        sx, sy = edge_point(ex, ey, cx, cy, sp.ENT_W/2, sp.ENT_H/2)
        exx, eyy = edge_point(cx, cy, ex, ey, sp.ATTR_W/2, sp.ATTR_H/2)
        prims.append(dict(kind='line', box=[sx, sy, exx, eyy], dash=False, arrow=None))
        prims.append(dict(kind='rect', shape='ellipse', geom_kind='ellipse',
                          box=[cx-sp.ATTR_W/2, cy-sp.ATTR_H/2, cx+sp.ATTR_W/2, cy+sp.ATTR_H/2],
                          fill='#FFFFFF', line='#000000',
                          text=text, size=sp.FS, align='center'))
    return prims


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument('--spec', required=True)
    ap.add_argument('--out-vsdx', required=True)
    ap.add_argument('--out-png', required=True)
    a = ap.parse_args()
    s = importlib.util.spec_from_file_location('attr_spec', a.spec)
    sp = importlib.util.module_from_spec(s)
    s.loader.exec_module(sp)
    with zipfile.ZipFile(TPL) as z:
        parts = {n: z.read(n) for n in z.namelist()}
    parts['visio/pages/page1.xml'] = build(sp).encode('utf-8')
    parts['visio/pages/pages.xml'] = patch_pages(parts['visio/pages/pages.xml'].decode('utf-8'), sp).encode('utf-8')
    os.makedirs(os.path.dirname(os.path.abspath(a.out_vsdx)), exist_ok=True)
    with zipfile.ZipFile(a.out_vsdx, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, d in parts.items():
            z.writestr(n, d)
    st = dict(page_w=sp.PAGE[0], page_h=sp.PAGE[1], fs_msg=sp.FS)
    write_png({'prims': png_prims(sp)}, sp, st, a.out_png, scale=170)
    print(f"实体 1 个、属性 {len(sp.ATTRS)} 个（主键 "
          f"{sum(1 for a in sp.ATTRS if a[3])} 个带下划线）；页面 {sp.PAGE[0]}×{sp.PAGE[1]} in")


if __name__ == '__main__':
    main()

