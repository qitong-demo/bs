# -*- coding: utf-8 -*-
"""用 用例图模板.vsdx 的 Use Case / Actor 主控形状生成图2-1。

做法：整包复用模板（主控、样式表、parts 全部保留），只重写 page1.xml 与 pages.xml 的页面尺寸。
  - 用例        → <Shape Master='2'>（模板主控「用例图-用例」，即 Use Case 椭圆）
  - 参与者      → <Shape Master='4'>（模板主控「用例图小人」，即 Actor）
  - 系统边界/关联 → 普通形状（模板未提供对应主控）
所有单元格的 V 一律写数值，公式只放在 F，符合 VSDX 规范。
"""
import sys, io, os, math, zipfile, importlib.util
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from build_sequence_vsdx import text_w, write_png, LINE_BOX_H

TPL = os.path.join(os.path.dirname(HERE), 'assets', 'usecase-template.vsdx')
NS = 'http://schemas.microsoft.com/office/visio/2012/main'
LINE_WEIGHT = 0.75 / 72.0
CHAR_IN = lambda pt: pt / 72.0

MASTER_UC, MASTER_ACTOR = '2', '4'


def esc(t):
    return (t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
             .replace('"', '&quot;'))


def cell(n, v, u=None, f=None):
    s = f"<Cell N='{n}' V='{v}'"
    if u:
        s += f" U='{u}'"
    if f:
        s += f" F='{f}'"
    if f is None and isinstance(v, str) and not v.startswith('#') and v not in ('TRUE', 'FALSE') \
            and not v.replace('.', '').replace('-', '').isdigit():
        # 字符串值（字体名等）不加引号
        pass
    return s + "/>"


def num(x):
    return f"{x:.10f}".rstrip('0').rstrip('.') if x != int(x) else f"{x:.10f}"


def textblock(text, size_pt, align=1):
    return (f"<Section N='Character'><Row IX='0'>"
            f"<Cell N='Size' V='{CHAR_IN(size_pt):.10f}'/>"
            f"<Cell N='Font' V='Times New Roman'/><Cell N='AsianFont' V='SimSun'/>"
            f"</Row></Section>"
            f"<Section N='Paragraph'><Row IX='0'><Cell N='HorzAlign' V='{align}'/>"
            f"</Row></Section>"
            f"<Text><cp IX='0'/><pp IX='0'/>{esc(text)}</Text>")


def shape_instance(sid, master, nameu, x, y, w, h, text, size_pt, extra=''):
    return (f"<Shape ID='{sid}' NameU='{esc(nameu)}' Name='{esc(nameu)}' Type='Shape' Master='{master}'>"
            f"<Cell N='PinX' V='{num(x)}'/><Cell N='PinY' V='{num(y)}'/>"
            f"<Cell N='Width' V='{num(w)}'/><Cell N='Height' V='{num(h)}'/>"
            f"<Cell N='LocPinX' V='{num(w/2)}' F='Width*0.5'/>"
            f"<Cell N='LocPinY' V='{num(h/2)}' F='Height*0.5'/>"
            f"<Cell N='Angle' V='0'/>"
            f"<Cell N='LineWeight' V='{LINE_WEIGHT:.10f}'/>"
            + extra + textblock(text, size_pt) + "</Shape>")


def geom_rect(w, h):
    pts = [(0, 0), (w, 0), (w, h), (0, h), (0, 0)]
    rows = ''.join(f"<Row T='LineTo' IX='{i+2}'><Cell N='X' V='{num(x)}'/><Cell N='Y' V='{num(y)}'/>"
                   f"</Row>" for i, (x, y) in enumerate(pts[1:]))
    return (f"<Section N='Geometry' IX='0'><Cell N='NoFill' V='1'/><Cell N='NoLine' V='0'/>"
            f"<Row T='MoveTo' IX='1'><Cell N='X' V='0'/><Cell N='Y' V='0'/></Row>{rows}</Section>")


def geom_textbox(w, h):
    # 纯文字框：无填充、无边线，用矩形几何占位
    return geom_rect(w, h).replace("<Cell N='NoFill' V='1'/>", "<Cell N='NoFill' V='1'/>") \
                          .replace("<Cell N='NoLine' V='0'/>", "<Cell N='NoLine' V='1'/>")


def geom_line(length):
    return (f"<Section N='Geometry' IX='0'><Cell N='NoFill' V='1'/><Cell N='NoLine' V='0'/>"
            f"<Row T='MoveTo' IX='1'><Cell N='X' V='0'/>"
            f"<Cell N='Y' V='{num(LINE_BOX_H/2)}'/></Row>"
            f"<Row T='LineTo' IX='2'><Cell N='X' V='{num(length)}'/>"
            f"<Cell N='Y' V='{num(LINE_BOX_H/2)}'/></Row></Section>")


def build_page_xml(spec):
    parts, sid = [], 0
    # ---- 系统边界 ----
    x1, y1, x2, y2 = spec.SYSTEM[1:]
    w, h = x2 - x1, y2 - y1
    sid += 1
    parts.append(f"<Shape ID='{sid}' NameU='矩形' Name='矩形' Type='Shape' LineStyle='3' FillStyle='3' TextStyle='3'>"
                 f"<Cell N='PinX' V='{num((x1+x2)/2)}'/><Cell N='PinY' V='{num((y1+y2)/2)}'/>"
                 f"<Cell N='Width' V='{num(w)}'/><Cell N='Height' V='{num(h)}'/>"
                 f"<Cell N='LocPinX' V='{num(w/2)}' F='Width*0.5'/>"
                 f"<Cell N='LocPinY' V='{num(h/2)}' F='Height*0.5'/>"
                 f"<Cell N='LineWeight' V='{LINE_WEIGHT:.10f}'/>"
                 f"<Cell N='FillPattern' V='0'/>"
                 + geom_rect(w, h) + "</Shape>")
    # ---- 边界标题（纯文字） ----
    tx1, ty1, tx2, ty2 = spec.SYSTEM_TITLE
    tw, th = tx2 - tx1, ty2 - ty1
    sid += 1
    parts.append(f"<Shape ID='{sid}' NameU='矩形' Name='矩形' Type='Shape' LineStyle='3' FillStyle='3' TextStyle='3'>"
                 f"<Cell N='PinX' V='{num((tx1+tx2)/2)}'/><Cell N='PinY' V='{num((ty1+ty2)/2)}'/>"
                 f"<Cell N='Width' V='{num(tw)}'/><Cell N='Height' V='{num(th)}'/>"
                 f"<Cell N='LocPinX' V='{num(tw/2)}' F='Width*0.5'/>"
                 f"<Cell N='LocPinY' V='{num(th/2)}' F='Height*0.5'/>"
                 f"<Cell N='FillPattern' V='0'/>"
                 + geom_textbox(tw, th) + textblock(spec.SYSTEM[0], spec.FS, align=0) + "</Shape>")

    # ---- 用例（主控 2） ----
    for cid, text, cx, cy, uw, uh in spec.USE_CASES:
        sid += 1
        parts.append(shape_instance(sid, MASTER_UC, 'Use Case', cx, cy, uw, uh, text, spec.FS))

    # ---- 参与者（主控 4） ----
    for aid, aname, ax, ay, aw, ah, face, above in spec.ACTORS:
        sid += 1
        extra = ''
        if above:
            extra = (f"<Cell N='TxtPinY' V='{num(ah)}' F='Height*1'/>"
                     f"<Cell N='TxtLocPinY' V='0' F='Height*0'/>")
        parts.append(shape_instance(sid, MASTER_ACTOR, 'Actor', ax, ay, aw, ah, aname, spec.FS, extra))

    # ---- 关联（普通 1-D 线） ----
    actors = {a[0]: a for a in spec.ACTORS}
    ucs = {c[0]: c for c in spec.USE_CASES}
    for aid, cid in spec.LINKS:
        _, _, ax, ay, aw, ah, face, _ = actors[aid]
        _, _, cx, cy, uw, uh = ucs[cid]
        if face == 'right':
            sx, sy = ax + aw / 2, ay
            ex, ey = cx - uw / 2, cy
        else:
            sx, sy = ax - aw / 2, ay
            ex, ey = cx + uw / 2, cy
        length = math.hypot(ex - sx, ey - sy)
        ang = math.atan2(ey - sy, ex - sx)
        sid += 1
        parts.append(f"<Shape ID='{sid}' NameU='动态连接线' Name='动态连接线' Type='Shape' LineStyle='3' FillStyle='3' TextStyle='3'>"
                     f"<Cell N='PinX' V='{num((sx+ex)/2)}'/><Cell N='PinY' V='{num((sy+ey)/2)}'/>"
                     f"<Cell N='Width' V='{num(length)}'/><Cell N='Height' V='{num(LINE_BOX_H)}'/>"
                     f"<Cell N='LocPinX' V='{num(length/2)}' F='Width*0.5'/>"
                     f"<Cell N='LocPinY' V='{num(LINE_BOX_H/2)}' F='Height*0.5'/>"
                     f"<Cell N='Angle' V='{ang:.15f}' U='RAD'/>"
                     f"<Cell N='BeginX' V='{num(sx)}'/><Cell N='BeginY' V='{num(sy)}'/>"
                     f"<Cell N='EndX' V='{num(ex)}'/><Cell N='EndY' V='{num(ey)}'/>"
                     f"<Cell N='LineWeight' V='{LINE_WEIGHT:.10f}'/>"
                     f"<Cell N='LinePattern' V='1'/><Cell N='FillPattern' V='0'/>"
                     f"<Cell N='BeginArrow' V='0'/><Cell N='EndArrow' V='0'/>"
                     f"<Cell N='ResizeMode' V='0'/>"
                     + geom_line(length) + "</Shape>")

    return ("<?xml version='1.0' encoding='utf-8' ?>\n\n"
            f"<PageContents xmlns='{NS}' "
            "xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships' "
            "xml:space='preserve'><Shapes>" + "".join(parts) + "</Shapes></PageContents>")


def patch_pages(xml, spec):
    pw, ph = spec.PAGE
    xml = xml.replace("<Cell N='PageWidth' V='8.26771653543307'/>",
                      f"<Cell N='PageWidth' V='{pw:.10f}'/>")
    xml = xml.replace("<Cell N='PageHeight' V='11.69291338582677'/>",
                      f"<Cell N='PageHeight' V='{ph:.10f}'/>")
    if 'PageWidth' not in xml:
        raise RuntimeError('未能在 pages.xml 中定位 PageWidth')
    import re
    xml = re.sub(r"(<Cell N='PageWidth' V=')[\d.]+'", lambda m: m.group(1) + f'{pw:.10f}\'', xml)
    xml = re.sub(r"(<Cell N='PageHeight' V=')[\d.]+'", lambda m: m.group(1) + f'{ph:.10f}\'', xml)
    xml = re.sub(r"ViewCenterX='[\d.]+'", f"ViewCenterX='{pw/2:.4f}'", xml)
    xml = re.sub(r"ViewCenterY='[\d.]+'", f"ViewCenterY='{ph/2:.4f}'", xml)
    return xml


# ---------------- PNG 预览（用基础图元近似，便于目视核对版式） ----------------
def png_prims(spec):
    prims = []
    x1, y1, x2, y2 = spec.SYSTEM[1:]
    prims.append(dict(kind='rect', shape='rect', geom_kind='rect', box=[x1, y1, x2, y2],
                      fill=None, line='#000000', text=None, size=spec.FS))
    tx1, ty1, tx2, ty2 = spec.SYSTEM_TITLE
    prims.append(dict(kind='rect', shape='rect', geom_kind='rect', box=[tx1, ty1, tx2, ty2],
                      fill=None, line=None, text=spec.SYSTEM[0], size=spec.FS, align='left'))
    for cid, text, cx, cy, uw, uh in spec.USE_CASES:
        prims.append(dict(kind='rect', shape='ellipse', geom_kind='ellipse',
                          box=[cx - uw/2, cy - uh/2, cx + uw/2, cy + uh/2],
                          fill='#FFFFFF', line='#000000', text=text, size=spec.FS, align='center'))
    for aid, aname, ax, ay, aw, ah, face, above in spec.ACTORS:
        hh, bw = ah * 0.62, aw * 2.6
        # 头 / 躯干 / 臂 / 腿（按 Actor 主控比例）
        prims.append(dict(kind='rect', shape='ellipse', geom_kind='ellipse',
                          box=[ax - aw*0.30, ay + ah*0.22, ax + aw*0.30, ay + ah*0.50],
                          fill=None, line='#000000', text=None, size=spec.FS))
        prims.append(dict(kind='line', box=[ax, ay + ah*0.22, ax, ay - ah*0.20], dash=False, arrow=None))
        prims.append(dict(kind='line', box=[ax - bw/2, ay + ah*0.06, ax + bw/2, ay + ah*0.06], dash=False, arrow=None))
        prims.append(dict(kind='line', box=[ax, ay - ah*0.20, ax - bw*0.32, ay - ah*0.50], dash=False, arrow=None))
        prims.append(dict(kind='line', box=[ax, ay - ah*0.20, ax + bw*0.32, ay - ah*0.50], dash=False, arrow=None))
        gw = text_w(aname, spec.FS)
        ty = (ay + ah*0.50 + 0.04, ay + ah*0.50 + 0.34) if above else (ay - ah*0.50 - 0.34, ay - ah*0.50 - 0.04)
        prims.append(dict(kind='rect', shape='rect', geom_kind='rect',
                          box=[ax - gw/2 - 0.06, ty[0], ax + gw/2 + 0.06, ty[1]],
                          fill=None, line=None, text=aname, size=spec.FS, align='center'))
    actors = {a[0]: a for a in spec.ACTORS}
    ucs = {c[0]: c for c in spec.USE_CASES}
    for aid, cid in spec.LINKS:
        _, _, ax, ay, aw, ah, face, _ = actors[aid]
        _, _, cx, cy, uw, uh = ucs[cid]
        if face == 'right':
            sx, sy, ex, ey = ax + aw/2, ay, cx - uw/2, cy
        else:
            sx, sy, ex, ey = ax - aw/2, ay, cx + uw/2, cy
        prims.append(dict(kind='line', box=[sx, sy, ex, ey], dash=False, arrow=None))
    return prims


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument('--spec', required=True)
    ap.add_argument('--out-vsdx', required=True)
    ap.add_argument('--out-png', required=True)
    a = ap.parse_args()

    sp = importlib.util.spec_from_file_location('uc_spec', a.spec)
    mod = importlib.util.module_from_spec(sp)
    sp.loader.exec_module(mod)

    with zipfile.ZipFile(TPL) as z:
        parts = {n: z.read(n) for n in z.namelist()}
    parts['visio/pages/page1.xml'] = build_page_xml(mod).encode('utf-8')
    parts['visio/pages/pages.xml'] = patch_pages(parts['visio/pages/pages.xml'].decode('utf-8'), mod).encode('utf-8')
    os.makedirs(os.path.dirname(os.path.abspath(a.out_vsdx)), exist_ok=True)
    with zipfile.ZipFile(a.out_vsdx, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, d in parts.items():
            z.writestr(n, d)

    prims = png_prims(mod)
    model = {'prims': prims}
    st = dict(page_w=mod.PAGE[0], page_h=mod.PAGE[1], fs_msg=mod.FS)
    write_png(model, mod, st, a.out_png, scale=150)

    print(f"主控实例：用例 {len(mod.USE_CASES)} 个 (Master=2)、参与者 {len(mod.ACTORS)} 个 (Master=4)、"
          f"关联 {len(mod.LINKS)} 条；页面 {mod.PAGE[0]}×{mod.PAGE[1]} in")


if __name__ == '__main__':
    main()
