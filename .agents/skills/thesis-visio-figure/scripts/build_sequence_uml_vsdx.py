# -*- coding: utf-8 -*-
"""UML 时序图生成器（规范版 v2）

v2 修正：
  ① 消息文字补上（v1 只画了箭头线，漏了消息标签）
  ② 角色参与者画火柴人（小人）+ 名称方框；对象参与者只画名称方框
其余规范沿用 v1：激活柱、箭头落在激活柱边缘、水平箭头、「名称:类名」+ 下划线。
"""
import sys, io, os, math, zipfile, importlib.util
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from build_sequence_vsdx import (text_w, geom_rect, geom_line, geom_ellipse,
                                 write_png, LINE_BOX_H, DEFAULT_STYLE)

TPL = os.path.join(os.path.dirname(HERE), 'assets', 'visio-template.vsdx')
NS = 'http://schemas.microsoft.com/office/visio/2012/main'
LW = 0.75 / 72.0

BAR_W, BAR_PAD = 0.16, 0.0
HDR_H = 0.64          # 参与者名称框高（参考图是紧凑方框，不是大块空白）
FIG_H = 0.74          # 火柴人高度；比名称框高 0.10，避免框线压到人形
TOP_PAD = 0.24        # 页面顶边到表头顶部
HEAD_GAP = 0.45       # 最下面一个表头框到第 1 条消息（要容下首条文字的全部字高）
BOTTOM_PAD = 0.45     # 最后一条消息到页面底边（此前漏算，导致末条被裁）
LBL_GAP = 0.07
LBL_H = 0.30
LOOP_W, LOOP_H = 0.30, 0.22   # 自调用回环：向右 0.30、向下 0.22
SELF_BAR_W, SELF_BAR_TOP, SELF_BAR_H = 0.16, 0.08, 0.28  # 自调用处的独立小方块
DY_SELF = 0.76                # 自调用行高：要让下一条消息的文字落在方块下方


def esc(t):
    return t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def num(x):
    return f"{x:.10f}"


def tb(text, size_pt, underline=True, align=1):
    style = "<Cell N='Style' V='4'/>" if underline else ''
    return ("<Section N='Character'><Row IX='0'>"
            f"<Cell N='Size' V='{size_pt/72.0:.10f}'/>"
            "<Cell N='Font' V='Times New Roman'/><Cell N='AsianFont' V='SimSun'/>"
            + style + "</Row></Section>"
            f"<Section N='Paragraph'><Row IX='0'><Cell N='HorzAlign' V='{align}'/>"
            "<Cell N='VertAlign' V='1'/></Row></Section>"
            f"<Text><cp IX='0'/><pp IX='0'/>{esc(text)}</Text>")


def rect_shape(sid, x1, y1, x2, y2, fill='#FFFFFF', dashed=False, text=None,
               size=12.25, underline=False, line=True, align=1):
    w, h = x2 - x1, y2 - y1
    body = (f"<Shape ID='{sid}' NameU='矩形' Name='矩形' Type='Shape' "
            "LineStyle='3' FillStyle='3' TextStyle='3'>"
            f"<Cell N='PinX' V='{num((x1+x2)/2)}'/><Cell N='PinY' V='{num((y1+y2)/2)}'/>"
            f"<Cell N='Width' V='{num(w)}'/><Cell N='Height' V='{num(h)}'/>"
            f"<Cell N='LocPinX' V='{num(w/2)}' F='Width*0.5'/>"
            f"<Cell N='LocPinY' V='{num(h/2)}' F='Height*0.5'/>"
            f"<Cell N='LineWeight' V='{LW:.10f}'/>"
            f"<Cell N='LinePattern' V='{'0' if not line else ('2' if dashed else '1')}'/>"
            f"<Cell N='FillPattern' V='{'1' if fill else '0'}'/>"
            + (f"<Cell N='FillForegnd' V='{fill}'/>" if fill else '')
            + geom_rect(w, h))
    if text:
        body += tb(text, size, underline, align)
    return body + "</Shape>"


def line_shape(sid, x1, y1, x2, y2, dashed=False, arrow='filled'):
    length = math.hypot(x2 - x1, y2 - y1)
    ang = math.atan2(y2 - y1, x2 - x1)
    end_arrow = {'filled': '1', 'open': '2', None: '0'}[arrow]
    return (f"<Shape ID='{sid}' NameU='动态连接线' Name='动态连接线' Type='Shape' "
            "LineStyle='3' FillStyle='3' TextStyle='3'>"
            f"<Cell N='PinX' V='{num((x1+x2)/2)}'/><Cell N='PinY' V='{num((y1+y2)/2)}'/>"
            f"<Cell N='Width' V='{num(length)}'/><Cell N='Height' V='{num(LINE_BOX_H)}'/>"
            f"<Cell N='LocPinX' V='{num(length/2)}' F='Width*0.5'/>"
            f"<Cell N='LocPinY' V='{num(LINE_BOX_H/2)}' F='Height*0.5'/>"
            f"<Cell N='Angle' V='{ang:.15f}' U='RAD'/>"
            f"<Cell N='BeginX' V='{num(x1)}'/><Cell N='BeginY' V='{num(y1)}'/>"
            f"<Cell N='EndX' V='{num(x2)}'/><Cell N='EndY' V='{num(y2)}'/>"
            f"<Cell N='LineWeight' V='{LW:.10f}'/>"
            f"<Cell N='LinePattern' V='{'2' if dashed else '1'}'/>"
            f"<Cell N='FillPattern' V='0'/>"
            f"<Cell N='BeginArrow' V='0'/><Cell N='EndArrow' V='{end_arrow}'/>"
            f"<Cell N='EndArrowSize' V='2'/><Cell N='ResizeMode' V='0'/>"
            + geom_line(length, LINE_BOX_H) + "</Shape>")


def ellipse_shape(sid, x1, y1, x2, y2, fill='#FFFFFF'):
    w, h = x2 - x1, y2 - y1
    return (f"<Shape ID='{sid}' NameU='椭圆' Name='椭圆' Type='Shape' "
            "LineStyle='3' FillStyle='3' TextStyle='3'>"
            f"<Cell N='PinX' V='{num((x1+x2)/2)}'/><Cell N='PinY' V='{num((y1+y2)/2)}'/>"
            f"<Cell N='Width' V='{num(w)}'/><Cell N='Height' V='{num(h)}'/>"
            f"<Cell N='LocPinX' V='{num(w/2)}' F='Width*0.5'/>"
            f"<Cell N='LocPinY' V='{num(h/2)}' F='Height*0.5'/>"
            f"<Cell N='LineWeight' V='{LW:.10f}'/><Cell N='LinePattern' V='1'/>"
            f"<Cell N='FillPattern' V='1'/><Cell N='FillForegnd' V='{fill}'/>"
            + geom_ellipse(w, h) + "</Shape>")


def is_actor(name):
    return name.endswith('用户') or name.endswith('管理员')


def layout(sp, st):
    fs = st['fs_msg']
    lifelines, msgs = sp.LIFELINES, sp.MSGS
    n = len(msgs)
    dy = st['dy_msg']
    xs = sorted(x for _, _, _, x in lifelines)
    page_w = st['page_w']

    # 名称框宽度：以本生命线为中心向左右各留一半间距；边缘生命线可以顶到页边。
    # 旧算法把左侧页边当成"邻居间距"，导致最左的角色框被压到 1.00，长名字折行。
    widths = {}
    for i, (k, name, cls, x) in enumerate(lifelines):
        half = x - 0.05
        half = min(half, page_w - x - 0.05)
        if i > 0:
            half = min(half, (x - xs[i - 1]) / 2.0 - 0.05)
        if i < len(xs) - 1:
            half = min(half, (xs[i + 1] - x) / 2.0 - 0.05)
        need = text_w(f":{name}", fs) + 0.24
        widths[k] = max(min(need, 2.0 * half), 1.20)

    has_actor = any(is_actor(nm) for _, nm, _, _ in lifelines)
    fig_h = FIG_H if has_actor else 0.0
    # 行高：普通消息 dy；自调用要容下回环，单独用 DY_SELF。
    row_h = [DY_SELF if kind == 'self' else dy for _, _, _, kind in msgs]
    offs = [0.0]
    for h in row_h[:-1]:
        offs.append(offs[-1] + h)
    # 顶边 + 人形 + 各表头框 + 首条留白 + 消息行 + 底边留白
    st['page_h'] = TOP_PAD + fig_h + HDR_H + HEAD_GAP + offs[-1] + BOTTOM_PAD
    hdr_top = st['page_h'] - TOP_PAD

    box_bot = {}
    for k, name, cls, x in lifelines:
        # 所有名称框（含角色）统一落在同一水平带：顶边 = hdr_top - fig_h，
        # 小人画在框上方，而不是把角色框单独往下挪。
        top = hdr_top - fig_h
        box_bot[k] = top - HDR_H
    y_first = offs[-1] + BOTTOM_PAD
    ys = [y_first - o for o in offs]

    # 激活条：一般参与者按老师要求“从第一条消息通到最后一条”；
    # 数据库层例外——参考图只在每个查询/返回连接处画一小段，不画通条。
    name_of = {k: name for k, name, _, _ in lifelines}
    part_idx = {}
    for i, (s, d, txt, kind) in enumerate(msgs):
        part_idx.setdefault(s, []).append(i)
        if d != s:
            part_idx.setdefault(d, []).append(i)
    bars = {}

    def seg(lo, hi):
        top = ys[lo] + BAR_PAD
        bottom = ys[hi] - BAR_PAD
        # 单条消息的段不能退化成一条横线，至少给 0.18 in 高
        if top - bottom < 0.18:
            mid = (top + bottom) / 2.0
            top, bottom = mid + 0.09, mid - 0.09
        return (bottom, top)

    for k, arr in part_idx.items():
        arr = sorted(set(arr))
        if '数据库' not in name_of[k]:
            bottom, top = seg(arr[0], arr[-1])
            if msgs[arr[-1]][3] == 'self':
                bottom -= (SELF_BAR_TOP + SELF_BAR_H)
            bars[k] = [(bottom, top)]
            continue
        runs, start, prev = [], arr[0], arr[0]
        for i in arr[1:]:
            if i - prev > 1:
                runs.append((start, prev))
                start = i
            prev = i
        runs.append((start, prev))
        bars[k] = [seg(lo, hi) for lo, hi in runs]
    return dict(dy=dy, dy_self=DY_SELF, row_h=row_h, ys=ys, bars=bars,
                hdr_top=hdr_top, box_bot=box_bot, widths=widths, hdr_h=HDR_H,
                n=n, fig_h=fig_h, lanes={k: x for k, _, _, x in lifelines})


def label_pos(L, st, sx, ex, y, text):
    """在箭头跨越的范围内找一段不被生命线/激活条打断的空档放文字。

    旧算法一律取箭头中点，跨两个角色的消息（图4-10 第1条、图4-13 第5/6条）
    文字正好压在中间那条生命线上。这里把生命线（及贴在其上的激活条）当障碍，
    取与箭头中点最接近、且放得下的空档。
    """
    lo, hi = min(sx, ex), max(sx, ex)
    segs = [(lo, hi)]
    for px in L['lanes'].values():
        b1, b2 = px - BAR_W / 2.0 - 0.02, px + BAR_W / 2.0 + 0.02
        nxt = []
        for s1, s2 in segs:
            if b2 <= s1 or b1 >= s2:
                nxt.append((s1, s2))
                continue
            if b1 > s1:
                nxt.append((s1, b1))
            if b2 < s2:
                nxt.append((b2, s2))
        segs = nxt
    segs = [s for s in segs if s[1] - s[0] > 0.04]
    w = text_w(text, st['fs_msg'])
    if not segs:
        return (lo + hi) / 2.0, w
    fit = [s for s in segs if s[1] - s[0] >= w + 0.04]
    if fit:
        seg = min(fit, key=lambda s: abs((s[0] + s[1]) / 2.0 - (lo + hi) / 2.0))
    else:
        seg = max(segs, key=lambda s: s[1] - s[0])
    return (seg[0] + seg[1]) / 2.0, w


def build_page(sp, st, L):
    parts, sid = [], 0
    pos = {k: x for k, _, _, x in sp.LIFELINES}
    for k, name, cls, x in sp.LIFELINES:
        sid += 1
        parts.append(line_shape(sid, x, L['box_bot'][k], x, 0.16, dashed=True, arrow=None))
    for k, name, cls, x in sp.LIFELINES:
        if is_actor(name):
            top, r = L['hdr_top'], 0.10
            cy = top - r - 0.04
            sid += 1
            parts.append(ellipse_shape(sid, x - r, cy - r, x + r, cy + r))
            sid += 1
            parts.append(line_shape(sid, x, cy - r, x, cy - r - 0.28, arrow=None))
            sid += 1
            parts.append(line_shape(sid, x - 0.16, cy - r - 0.10, x + 0.16, cy - r - 0.10, arrow=None))
            sid += 1
            parts.append(line_shape(sid, x, cy - r - 0.28, x - 0.13, top - 0.74, arrow=None))
            sid += 1
            parts.append(line_shape(sid, x, cy - r - 0.28, x + 0.13, top - 0.74, arrow=None))
        w = L['widths'][k]
        sid += 1
        parts.append(rect_shape(sid, x - w/2, L['box_bot'][k], x + w/2,
                                L['box_bot'][k] + L['hdr_h'], fill='#FFFFFF',
                                text=f":{name}",
                                size=st['fs_msg'], underline=True))
    for k, name, cls, x in sp.LIFELINES:
        for lo, hi in L['bars'].get(k, []):
            sid += 1
            parts.append(rect_shape(sid, x - BAR_W/2, lo, x + BAR_W/2, hi, fill='#FFFFFF'))
    for i, (s, d, txt, kind) in enumerate(sp.MSGS):
        y = L['ys'][i]
        label = f"{i + 1}. {txt}"
        if kind == 'self':
            # 参考图画法：自调用处先有一个独立的小激活方块，回环挂在方块上沿
            sid += 1
            bx0 = pos[s] + BAR_W/2
            parts.append(rect_shape(sid, bx0 - SELF_BAR_W/2, y - SELF_BAR_TOP - SELF_BAR_H,
                                    bx0 + SELF_BAR_W/2, y - SELF_BAR_TOP, fill='#FFFFFF'))
            bx = bx0 + SELF_BAR_W / 2
            xt = bx0
            xr = xt + LOOP_W
            yb = y - LOOP_H
            sid += 1
            parts.append(line_shape(sid, xt, y, xr, y, arrow=None))
            sid += 1
            parts.append(line_shape(sid, xr, y, xr, yb, arrow=None))
            sid += 1
            parts.append(line_shape(sid, xr, yb, bx, yb, arrow='filled'))
            tw = text_w(label, st['fs_msg'])
            lx = xr + 0.06
            nxt = [p for p in pos.values() if p > bx]
            right = min(nxt) - 0.10 if nxt else st['page_w'] - 0.10
            gw = max(min(tw + 0.10, right - lx), 0.50)
            sid += 1
            parts.append(rect_shape(sid, lx, y - 0.20, lx + gw, y + 0.02,
                                    fill=None, text=label, size=st['fs_msg'],
                                    underline=False, line=False, align=0))
            continue
        sx = pos[s] + (BAR_W/2 if pos[d] > pos[s] else -BAR_W/2)
        ex = pos[d] - (BAR_W/2 if pos[d] > pos[s] else -BAR_W/2)
        sid += 1
        parts.append(line_shape(sid, sx, y, ex, y, dashed=(kind == 'ret'),
                                arrow=('open' if kind == 'ret' else 'filled')))
        mid, tw = label_pos(L, st, sx, ex, y, label)
        gw = tw + 0.14
        sid += 1
        parts.append(rect_shape(sid, mid - gw/2, y + LBL_GAP, mid + gw/2, y + LBL_GAP + LBL_H,
                                fill=None, text=label, size=st['fs_msg'], underline=False, line=False))
    return ("<?xml version='1.0' encoding='utf-8' ?>\n\n"
            f"<PageContents xmlns='{NS}' "
            "xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships' "
            "xml:space='preserve'><Shapes>" + "".join(parts) + "</Shapes></PageContents>")


def png_prims(sp, st, L):
    prims = []
    pos = {k: x for k, _, _, x in sp.LIFELINES}
    for k, name, cls, x in sp.LIFELINES:
        prims.append(dict(kind='line', box=[x, 0.16, x, L['box_bot'][k]], dash=True, arrow=None))
    for k, name, cls, x in sp.LIFELINES:
        if is_actor(name):
            top, r = L['hdr_top'], 0.10
            cy = top - r - 0.04
            prims.append(dict(kind='rect', shape='ellipse', geom_kind='ellipse',
                              box=[x - r, cy - r, x + r, cy + r], fill='#FFFFFF',
                              line='#000000', text=None, size=st['fs_msg']))
            prims.append(dict(kind='line', box=[x, cy - r, x, cy - r - 0.28], dash=False, arrow=None))
            prims.append(dict(kind='line', box=[x - 0.16, cy - r - 0.10, x + 0.16, cy - r - 0.10],
                              dash=False, arrow=None))
            prims.append(dict(kind='line', box=[x, cy - r - 0.28, x - 0.13, top - 0.74],
                              dash=False, arrow=None))
            prims.append(dict(kind='line', box=[x, cy - r - 0.28, x + 0.13, top - 0.74],
                              dash=False, arrow=None))
    for k, name, cls, x in sp.LIFELINES:
        w = L['widths'][k]
        prims.append(dict(kind='rect', shape='rect', geom_kind='rect',
                          box=[x - w/2, L['box_bot'][k], x + w/2, L['box_bot'][k] + L['hdr_h']],
                          fill='#FFFFFF', line='#000000',
                          text=f":{name}",
                          size=st['fs_msg'], align='center', underline=True))
    for k, name, cls, x in sp.LIFELINES:
        for lo, hi in L['bars'].get(k, []):
            prims.append(dict(kind='rect', shape='rect', geom_kind='rect',
                              box=[x - BAR_W/2, lo, x + BAR_W/2, hi], fill='#FFFFFF',
                              line='#000000', text=None, size=st['fs_msg']))
    for i, (s, d, txt, kind) in enumerate(sp.MSGS):
        y = L['ys'][i]
        label = f"{i + 1}. {txt}"
        if kind == 'self':
            prims.append(dict(kind='rect', shape='rect', geom_kind='rect',
                              box=[pos[s] + BAR_W/2 - SELF_BAR_W/2, y - SELF_BAR_TOP - SELF_BAR_H,
                                   pos[s] + BAR_W/2 + SELF_BAR_W/2, y - SELF_BAR_TOP],
                              fill='#FFFFFF', line='#000000', text=None, size=st['fs_msg']))
            bx = pos[s] + BAR_W/2 + SELF_BAR_W/2
            xt = pos[s] + BAR_W/2
            xr = xt + LOOP_W
            yb = y - LOOP_H
            prims.append(dict(kind='line', box=[xt, y, xr, y], dash=False, arrow=None))
            prims.append(dict(kind='line', box=[xr, y, xr, yb], dash=False, arrow=None))
            prims.append(dict(kind='line', box=[xr, yb, bx, yb], dash=False, arrow='filled'))
            tw = text_w(label, st['fs_msg'])
            lx = xr + 0.06
            nxt = [p for p in pos.values() if p > bx]
            right = min(nxt) - 0.10 if nxt else st['page_w'] - 0.10
            gw = max(min(tw + 0.10, right - lx), 0.50)
            prims.append(dict(kind='rect', shape='rect', geom_kind='rect',
                              box=[lx, y - 0.20, lx + gw, y + 0.02],
                              fill=None, line=None, text=label, size=st['fs_msg'], align='left'))
            continue
        sx = pos[s] + (BAR_W/2 if pos[d] > pos[s] else -BAR_W/2)
        ex = pos[d] - (BAR_W/2 if pos[d] > pos[s] else -BAR_W/2)
        prims.append(dict(kind='line', box=[sx, y, ex, y], dash=(kind == 'ret'),
                          arrow=('open' if kind == 'ret' else 'filled')))
        mid, tw = label_pos(L, st, sx, ex, y, label)
        gw = tw + 0.14
        prims.append(dict(kind='rect', shape='rect', geom_kind='rect',
                          box=[mid - gw/2, y + LBL_GAP, mid + gw/2, y + LBL_GAP + LBL_H],
                          fill=None, line=None, text=label, size=st['fs_msg'], align='center'))
    return prims


def main():
    import argparse, re
    ap = argparse.ArgumentParser()
    ap.add_argument('--spec', required=True)
    ap.add_argument('--out-vsdx', required=True)
    ap.add_argument('--out-png', required=True)
    a = ap.parse_args()
    s = importlib.util.spec_from_file_location('seq_spec', a.spec)
    sp = importlib.util.module_from_spec(s)
    s.loader.exec_module(sp)
    st = dict(DEFAULT_STYLE)
    st.update(getattr(sp, 'STYLE', {}))
    st.setdefault('fs_msg', 12.25)
    L = layout(sp, st)
    page_xml = build_page(sp, st, L)
    with zipfile.ZipFile(TPL) as z:
        parts = {n: z.read(n) for n in z.namelist()}
    parts['visio/pages/page1.xml'] = page_xml.encode('utf-8')
    pg = parts['visio/pages/pages.xml'].decode('utf-8')
    pg = re.sub(r"(<Cell N='PageWidth' V=')[\d.]+'", lambda m: m.group(1) + f"{st['page_w']:.10f}'", pg)
    pg = re.sub(r"(<Cell N='PageHeight' V=')[\d.]+'", lambda m: m.group(1) + f"{st['page_h']:.10f}'", pg)
    pg = re.sub(r"ViewCenterX='[\d.]+'", f"ViewCenterX='{st['page_w']/2:.4f}'", pg)
    pg = re.sub(r"ViewCenterY='[\d.]+'", f"ViewCenterY='{st['page_h']/2:.4f}'", pg)
    parts['visio/pages/pages.xml'] = pg.encode('utf-8')
    os.makedirs(os.path.dirname(os.path.abspath(a.out_vsdx)), exist_ok=True)
    with zipfile.ZipFile(a.out_vsdx, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, d in parts.items():
            z.writestr(n, d)
    write_png({'prims': png_prims(sp, st, L)}, sp, st, a.out_png, scale=150)
    actors = sum(1 for _, n, _, _ in sp.LIFELINES if is_actor(n))
    print(f"参与者 {len(sp.LIFELINES)}（火柴人 {actors} 个）、消息 {len(sp.MSGS)} 条（均带文字）、"
          f"激活柱 {sum(len(v) for v in L['bars'].values())} 段；页面 {st['page_w']}×{round(st['page_h'],2)} in")


if __name__ == '__main__':
    main()
