# -*- coding: utf-8 -*-
"""用 ER实体关系图模板.vsdx 的矩形/菱形主控生成图3-2 系统E-R图。

实体 → Master=4（流程图过程，矩形）
联系 → Master=5（流程图-判断，菱形）
连线与基数标注 → 普通形状
"""
import sys, io, os, math, zipfile, importlib.util
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from build_sequence_vsdx import write_png, text_w, LINE_BOX_H

TPL = os.path.join(os.path.dirname(HERE), 'assets', 'er-template.vsdx')
NS = 'http://schemas.microsoft.com/office/visio/2012/main'
LW = 0.75 / 72.0
M_ENT, M_REL = '4', '5'


def esc(t):
    return t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def num(x):
    return f"{x:.10f}"


def textblock(text, size_pt, align=1):
    return (f"<Section N='Character'><Row IX='0'>"
            f"<Cell N='Size' V='{size_pt/72.0:.10f}'/>"
            f"<Cell N='Font' V='Times New Roman'/><Cell N='AsianFont' V='SimSun'/>"
            f"</Row></Section>"
            f"<Section N='Paragraph'><Row IX='0'><Cell N='HorzAlign' V='{align}'/>"
            f"<Cell N='VertAlign' V='1'/></Row></Section>"
            f"<Text><cp IX='0'/><pp IX='0'/>{esc(text)}</Text>")


def inst(sid, master, nameu, x, y, w, h, text, size_pt):
    return (f"<Shape ID='{sid}' NameU='{nameu}' Name='{nameu}' Type='Shape' Master='{master}'>"
            f"<Cell N='PinX' V='{num(x)}'/><Cell N='PinY' V='{num(y)}'/>"
            f"<Cell N='Width' V='{num(w)}'/><Cell N='Height' V='{num(h)}'/>"
            f"<Cell N='LocPinX' V='{num(w/2)}' F='Width*0.5'/>"
            f"<Cell N='LocPinY' V='{num(h/2)}' F='Height*0.5'/>"
            f"<Cell N='Angle' V='0'/><Cell N='LineWeight' V='{LW:.10f}'/>"
            + textblock(text, size_pt) + "</Shape>")


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
            f"<Cell N='LineWeight' V='{LW:.10f}'/>"
            f"<Cell N='LinePattern' V='1'/><Cell N='FillPattern' V='0'/>"
            f"<Cell N='BeginArrow' V='0'/><Cell N='EndArrow' V='0'/>"
            f"<Cell N='ResizeMode' V='0'/>" + geom_line(length) + "</Shape>")


def label_shape(sid, cx, cy, text, size_pt):
    w = max(text_w(text, size_pt) + 0.10, 0.24)
    h = 0.30
    return (f"<Shape ID='{sid}' NameU='矩形' Name='矩形' Type='Shape' "
            f"LineStyle='3' FillStyle='3' TextStyle='3'>"
            f"<Cell N='PinX' V='{num(cx)}'/><Cell N='PinY' V='{num(cy)}'/>"
            f"<Cell N='Width' V='{num(w)}'/><Cell N='Height' V='{num(h)}'/>"
            f"<Cell N='LocPinX' V='{num(w/2)}' F='Width*0.5'/>"
            f"<Cell N='LocPinY' V='{num(h/2)}' F='Height*0.5'/>"
            f"<Cell N='FillPattern' V='0'/>" + geom_rect(w, h) + textblock(text, size_pt) + "</Shape>")


def build_page_xml(sp):
    E = {e[0]: e for e in sp.ENTITIES}
    parts, sid = [], 0
    # 实体
    for eid, name, x, y in sp.ENTITIES:
        sid += 1
        parts.append(inst(sid, M_ENT, '流程图过程', x, y, sp.ENT_W, sp.ENT_H, name, sp.FS))
    # 联系 + 两侧连线 + 基数
    for rid, rname, a, ca, b, cb in sp.RELS:
        _, _, ax, ay = E[a]
        _, _, bx, by = E[b]
        dx, dy = bx - ax, by - ay
        dist = math.hypot(dx, dy)
        ux, uy = dx / dist, dy / dist
        mx, my = (ax + bx) / 2, (ay + by) / 2
        # 线段端点：实体边界 → 菱形边界
        def edge(x, y, sgn, half_w, half_h):
            t = min(half_w / abs(ux) if ux else 1e9, half_h / abs(uy) if uy else 1e9)
            return x + sgn * ux * t, y + sgn * uy * t
        ax1, ay1 = edge(ax, ay, +1, sp.ENT_W / 2, sp.ENT_H / 2)
        bx1, by1 = edge(bx, by, -1, sp.ENT_W / 2, sp.ENT_H / 2)
        t_dia = min((sp.DIA_W / 2) / abs(ux) if ux else 1e9, (sp.DIA_H / 2) / abs(uy) if uy else 1e9)
        d1x, d1y = mx - ux * t_dia, my - uy * t_dia
        d2x, d2y = mx + ux * t_dia, my + uy * t_dia
        sid += 1
        parts.append(line_shape(sid, ax1, ay1, d1x, d1y))
        sid += 1
        parts.append(line_shape(sid, d2x, d2y, bx1, by1))
        sid += 1
        parts.append(inst(sid, M_REL, '流程图-判断', mx, my, sp.DIA_W, sp.DIA_H, rname, sp.FS))
        # 基数：靠近实体一端，沿法线偏移
        nx, ny = -uy, ux
        for (ex, ey, card, px_, py_, direction) in ((ax1, ay1, ca, ax, ay, 1), (bx1, by1, cb, bx, by, -1)):
            sgn = 1 if ny > 0 or (ny == 0 and nx > 0) else -1
            lx = ex + direction * ux * 0.38 + nx * 0.55 * sgn
            ly = ey + direction * uy * 0.38 + ny * 0.55 * sgn
            sid += 1
            parts.append(label_shape(sid, lx, ly, card, sp.CARD_FS))
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
    E = {e[0]: e for e in sp.ENTITIES}
    prims = []
    for eid, name, x, y in sp.ENTITIES:
        prims.append(dict(kind='rect', shape='rect', geom_kind='rect',
                          box=[x - sp.ENT_W/2, y - sp.ENT_H/2, x + sp.ENT_W/2, y + sp.ENT_H/2],
                          fill='#FFFFFF', line='#000000', text=name, size=sp.FS, align='center'))
    for rid, rname, a, ca, b, cb in sp.RELS:
        _, _, ax, ay = E[a]
        _, _, bx, by = E[b]
        dx, dy = bx - ax, by - ay
        dist = math.hypot(dx, dy)
        ux, uy = dx / dist, dy / dist
        mx, my = (ax + bx) / 2, (ay + by) / 2
        def edge(x, y, sgn):
            t = min((sp.ENT_W/2) / abs(ux) if ux else 1e9, (sp.ENT_H/2) / abs(uy) if uy else 1e9)
            return x + sgn * ux * t, y + sgn * uy * t
        ax1, ay1 = edge(ax, ay, +1)
        bx1, by1 = edge(bx, by, -1)
        t_dia = min((sp.DIA_W/2) / abs(ux) if ux else 1e9, (sp.DIA_H/2) / abs(uy) if uy else 1e9)
        d1x, d1y = mx - ux * t_dia, my - uy * t_dia
        d2x, d2y = mx + ux * t_dia, my + uy * t_dia
        prims.append(dict(kind='line', box=[ax1, ay1, d1x, d1y], dash=False, arrow=None))
        prims.append(dict(kind='line', box=[d2x, d2y, bx1, by1], dash=False, arrow=None))
        prims.append(dict(kind='rect', shape='diamond', geom_kind='diamond',
                          box=[mx - sp.DIA_W/2, my - sp.DIA_H/2, mx + sp.DIA_W/2, my + sp.DIA_H/2],
                          fill='#FFFFFF', line='#000000', text=rname, size=sp.FS, align='center'))
        nx, ny = -uy, ux
        for ex, ey, card, direction in ((ax1, ay1, ca, 1), (bx1, by1, cb, -1)):
            sgn = 1 if (ny > 0 or (ny == 0 and nx > 0)) else -1
            lx = ex + direction * ux * 0.38 + nx * 0.55 * sgn
            ly = ey + direction * uy * 0.38 + ny * 0.55 * sgn
            gw = text_w(card, sp.CARD_FS)
            prims.append(dict(kind='rect', shape='rect', geom_kind='rect',
                              box=[lx - gw/2 - 0.05, ly - 0.15, lx + gw/2 + 0.05, ly + 0.15],
                              fill=None, line=None, text=card, size=sp.CARD_FS, align='center'))
    return prims


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument('--spec', required=True)
    ap.add_argument('--out-vsdx', required=True)
    ap.add_argument('--out-png', required=True)
    a = ap.parse_args()
    s = importlib.util.spec_from_file_location('er_spec', a.spec)
    mod = importlib.util.module_from_spec(s)
    s.loader.exec_module(mod)
    with zipfile.ZipFile(TPL) as z:
        parts = {n: z.read(n) for n in z.namelist()}
    parts['visio/pages/page1.xml'] = build_page_xml(mod).encode('utf-8')
    parts['visio/pages/pages.xml'] = patch_pages(parts['visio/pages/pages.xml'].decode('utf-8'), mod).encode('utf-8')
    os.makedirs(os.path.dirname(os.path.abspath(a.out_vsdx)), exist_ok=True)
    with zipfile.ZipFile(a.out_vsdx, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, d in parts.items():
            z.writestr(n, d)
    st = dict(page_w=mod.PAGE[0], page_h=mod.PAGE[1], fs_msg=mod.FS)
    write_png({'prims': png_prims(mod)}, mod, st, a.out_png, scale=150)
    print(f"实体 {len(mod.ENTITIES)} 个(Master=4)、联系 {len(mod.RELS)} 个(Master=5)、"
          f"连线 {len(mod.RELS)*2} 条、基数标注 {len(mod.RELS)*2} 个；页面 {mod.PAGE[0]}×{mod.PAGE[1]} in")


if __name__ == '__main__':
    main()

