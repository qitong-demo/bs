# -*- coding: utf-8 -*-
"""
thesis-visio-figure / build_sequence_vsdx.py

按图定义（spec）生成符合哈尔滨信息工程学院论文规范的可编辑 Visio 时序图 (.vsdx)，
同时输出 PNG 版式预览和 JSON 版式报告（供 check_figure.py 自检）。

用法：
    python build_sequence_vsdx.py --spec specs/figure_4_1_user_auth.py \
        --out-vsdx out.vsdx --out-png out.png

spec 模块需定义：
    NAME       图名（用于 Visio 页面名，不含“图x-y”前缀也可）
    LIFELINES  [(key, 中文名, 西文名, x), ...]
    MSGS       [(src, dst, 文字, kind), ...]  kind: sync | ret | self
    FRAMES     [(tag, cond, i0, i1, x1, x2), ...]  tag: alt | else | opt ...
    BARS       [(key, i0, i1), ...]
    STYLE      dict（可选，覆盖默认版式）
"""
import argparse, importlib.util, json, math, os, sys, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
SKILL_ROOT = os.path.dirname(HERE)
DEFAULT_TEMPLATE = os.path.join(SKILL_ROOT, "assets", "visio-template.vsdx")
NS = "http://schemas.microsoft.com/office/visio/2012/main"
CM_PER_IN = 2.54

DEFAULT_STYLE = dict(
    page_w=8.27, page_h=11.69,
    fs_msg=12.25, fs_life=12.25, fs_sub=8.0, fs_tab=11.5, fs_cond=11.5,
    line_weight_pt=0.75,
    box_w=1.24, box_h=0.64, box_top_margin=0.16, box_bottom_gap=None,
    dy_msg=0.335, dy_self=0.50, frame_in=0.50, frame_out=0.08,
    tab_w=0.46, tab_h=0.24, tab_text_pad=0.08,
    bar_w=0.08, bar_pad=0.12,
    loop_w=0.28, loop_h=0.22, txt_off=0.34,
    life_bottom_pad=0.10, min_bottom=0.14,
    msg_text_lift=0.02, text_pad=0.10,
    insert_width_cm=15.5, max_aspect=1.50,
    ascii_em=0.52,
    # 自检容差（英寸）
    tol_text=0.02, tol_bar=0.03, tol_border=0.01,
)


# --------------------------------------------------------------------- utils
def load_spec(path):
    spec_dir = os.path.dirname(os.path.abspath(path))
    name = os.path.splitext(os.path.basename(path))[0]
    if spec_dir not in sys.path:
        sys.path.insert(0, spec_dir)
    module = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(module)
    module.loader.exec_module(mod)
    return mod


# Times New Roman 字符宽度表（单位：em/1000），用于精确估算文字宽度
_TW = {' ': 250, '!': 333, '"': 408, '#': 500, '$': 500, '%': 833, '&': 778, "'": 180,
       '(': 333, ')': 333, '*': 500, '+': 564, ',': 250, '-': 333, '.': 250, '/': 278,
       ':': 278, ';': 278, '<': 564, '=': 564, '>': 564, '?': 444, '@': 921,
       '[': 333, '\\': 278, ']': 333, '^': 469, '_': 500, '`': 333,
       '{': 480, '|': 200, '}': 480, '~': 541}
_TW.update({c: 500 for c in '0123456789'})
_TW.update(dict(zip('ABCDEFGHIJKLMNOPQRSTUVWXYZ',
                    [722, 667, 667, 722, 611, 556, 722, 722, 333, 389, 722, 611, 889,
                     722, 722, 556, 722, 667, 556, 611, 722, 722, 944, 722, 722, 611])))
_TW.update(dict(zip('abcdefghijklmnopqrstuvwxyz',
                    [444, 500, 444, 500, 444, 333, 500, 500, 278, 278, 500, 278, 778,
                     500, 500, 500, 500, 333, 389, 278, 500, 500, 722, 500, 500, 444])))


def text_w(s, size_pt, ascii_em=0.52):
    """估算文字宽度（英寸）。中文按 1 em，西文查 Times New Roman 宽度表。"""
    w = 0.0
    for ch in s:
        if ord(ch) > 0x2000:
            w += 1.0
        else:
            w += _TW.get(ch, ascii_em * 1000) / 1000.0
    return w * size_pt / 72.0


def esc(t):
    return t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


# ------------------------------------------------------------------ layout
def layout(spec, st):
    L = spec.LIFELINES
    X = {k: x for k, _, _, x in L}
    msgs, frames, bars = spec.MSGS, spec.FRAMES, spec.BARS

    frame_start = {f[2]: i for i, f in enumerate(frames)}
    frame_end = {f[3]: i for i, f in enumerate(frames)}

    box_top = st['page_h'] - st['box_top_margin']
    # 首条消息文字位于箭线上方，必须让开表头框，否则会压住表头
    if st.get('box_bottom_gap') is None:
        st['box_bottom_gap'] = st['fs_msg'] / 72.0 * 1.35 + 0.06
    y = box_top - st['box_h'] - st['box_bottom_gap']

    y_of, span = {}, {}
    for idx, (s, d, txt, kind) in enumerate(msgs):
        if idx in frame_start:
            fi = frame_start[idx]
            span[fi] = [y + 0.05, None]
            y -= st['frame_in']
        y_of[idx] = y
        y -= st['dy_self'] if kind == 'self' else st['dy_msg']
        if idx in frame_end:
            fi = frame_end[idx]
            span[fi][1] = y + 0.16
            y -= st['frame_out']
    for fi in range(len(frames)):
        if frames[fi][0] == 'else' and fi > 0:
            span[fi][0] = span[fi - 1][1]

    life_bottom = y + st['life_bottom_pad']

    bar_span = [(k, y_of[i0] + st['bar_pad'], y_of[i1] - st['bar_pad']) for k, i0, i1 in bars]
    bar_at = {}
    for k, t, b in bar_span:
        bar_at.setdefault(k, []).append((t, b))

    def bar_edge(key, yv, direction):
        for t, b in bar_at.get(key, []):
            if b - 0.05 <= yv <= t + 0.05:
                return st['bar_w'] / 2.0 * (1 if direction > 0 else -1)
        return 0.0

    def blocked_x(yv):
        """该 y 上被激活条占据的 x 区间。"""
        out = []
        for key, t, b in bar_span:
            if b - 0.02 <= yv <= t + 0.02:
                out.append((X[key] - st['bar_w'] / 2.0, X[key] + st['bar_w'] / 2.0))
        return out

    def place_x(x1, ex, yv, w):
        """在箭头跨越的范围内找最宽的空档放文字，避开激活条。"""
        lo, hi = min(x1, ex), max(x1, ex)
        segs = [(lo, hi)]
        for b1, b2 in blocked_x(yv):
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
        segs = [s for s in segs if s[1] - s[0] > 0.02]
        if not segs:
            return (x1 + ex) / 2.0
        seg = max(segs, key=lambda s: s[1] - s[0])
        if seg[1] - seg[0] >= w:
            return (seg[0] + seg[1]) / 2.0
        return (x1 + ex) / 2.0

    # ---- primitives
    P = []

    def rect(x1, y1, x2, y2, role, fill=None, line='#000000', text=None, size=None,
             align='center', two=None):
        gw = None
        if text:
            gw = max(text_w(text, size or st['fs_msg']),
                     text_w(two, size or st['fs_msg']) if two else 0.0)
        P.append(dict(kind='rect', role=role, box=[x1, y1, x2, y2], fill=fill, line=line,
                      text=text, two=two, size=size or st['fs_msg'], align=align,
                      valign='middle', glyph_w=gw))

    def line(x1, y1, x2, y2, role, dash=False, arrow=None):
        P.append(dict(kind='line', role=role, box=[x1, y1, x2, y2], dash=dash, arrow=arrow))

    def text(x1, y1, x2, y2, s, role, size=None, align='center'):
        size = size or st['fs_msg']
        gw = text_w(s, size)
        gh = size / 72.0 * 1.25
        cy = (y1 + y2) / 2.0
        if align == 'left':
            gx1, gx2 = x1 + 0.02, x1 + 0.02 + gw
        else:
            cx = (x1 + x2) / 2.0
            gx1, gx2 = cx - gw / 2.0, cx + gw / 2.0
        P.append(dict(kind='text', role=role, box=[x1, y1, x2, y2], text=s, size=size,
                      align=align, valign='middle', glyph_w=gw,
                      glyph_box=[gx1, cy - gh / 2.0, gx2, cy + gh / 2.0]))

    # 1) 组合片段
    for fi, (tag, cond, i0, i1, x1, x2) in enumerate(frames):
        top, bot = span[fi]
        rect(x1, bot, x2, top, 'frame')
        rect(x1, top - st['tab_h'], x1 + st['tab_w'], top, 'tab', text=tag, size=st['fs_tab'])
        cw = text_w(cond, st['fs_cond']) + st['text_pad']
        text(x1 + st['tab_w'] + st['tab_text_pad'], top - st['tab_h'],
             x1 + st['tab_w'] + st['tab_text_pad'] + cw, top, cond, 'cond',
             size=st['fs_cond'], align='left')

    # 2) 生命线
    for key, name, sub, x in L:
        line(x, life_bottom, x, box_top - st['box_h'], 'lifeline', dash=True)

    # 3) 激活条
    for key, t, b in bar_span:
        x = X[key]
        rect(x - st['bar_w'] / 2, b, x + st['bar_w'] / 2, t, 'bar', fill='#FFFFFF')

    # 4) 消息
    for idx, (s, d, txt, kind) in enumerate(msgs):
        yv = y_of[idx]
        if kind == 'self':
            x = X[s]
            line(x, yv, x + st['loop_w'], yv, 'msg')
            line(x + st['loop_w'], yv, x + st['loop_w'], yv - st['loop_h'], 'msg')
            line(x + st['loop_w'], yv - st['loop_h'], x, yv - st['loop_h'], 'msg', arrow='filled')
            w = text_w(txt, st['fs_msg']) + st['text_pad']
            text(x + st['txt_off'], yv - st['loop_h'], x + st['txt_off'] + w, yv, txt, 'msg_text',
                 align='left')
        else:
            x1, x2 = X[s], X[d]
            direction = 1 if x2 > x1 else -1
            ex = x2 - bar_edge(d, yv, direction) * direction
            line(x1, yv, ex, yv, 'msg', dash=(kind == 'ret'),
                 arrow=('open' if kind == 'ret' else 'filled'))
            mid = place_x(x1, ex, yv, text_w(txt, st['fs_msg']))
            w = max(text_w(txt, st['fs_msg']) + st['text_pad'], 1.0)
            h = st['fs_msg'] / 72.0 * 1.35
            text(mid - w / 2, yv + st['msg_text_lift'], mid + w / 2,
                 yv + st['msg_text_lift'] + h, txt, 'msg_text')

    # 5) 生命线表头
    for key, name, sub, x in L:
        rect(x - st['box_w'] / 2, box_top - st['box_h'], x + st['box_w'] / 2, box_top, 'header',
             fill='#FFFFFF', text=name, size=st['fs_life'], two=sub)

    return dict(prims=P, life_bottom=life_bottom, y_of=y_of, frame_span=span, bar_span=bar_span,
                box_top=box_top)


# ------------------------------------------------------------------- vsdx
def _cell(n, v, u=None, f=None):
    s = f"<Cell N='{n}' V='{v}'"
    if u:
        s += f" U='{u}'"
    if f:
        s += f" F='{f}'"
    return s + "/>"


def _char(size):
    return ("<Section N='Character'><Row IX='0'>"
            "<Cell N='Font' V='Times New Roman'/><Cell N='AsianFont' V='SimSun'/>"
            f"<Cell N='Color' V='#000000'/><Cell N='Size' V='{size/72.0:.10f}' U='PT'/>"
            "</Row></Section>")


def _para(align):
    hz = {'left': '0', 'center': '1', 'right': '2'}[align]
    return f"<Section N='Paragraph'><Row IX='0'><Cell N='HorzAlign' V='{hz}'/></Row></Section>"


# 几何一律写成「数值」，绝不把公式塞进 V。
# VSDX 里 Cell 的 V 必须是**计算结果**，公式要放在单独的 F 属性里；
# 把 "Width*1" 这类公式写进 V 而又不给 F，Visio 会读不出几何、整段 Geometry 被丢弃
# ——表现就是「图形和线全没了，只剩文字」。这是本项目踩过的最大的坑。
def _rows(*pts):
    out = []
    for i, (x, y) in enumerate(pts, start=1):
        t = 'MoveTo' if i == 1 else 'LineTo'
        out.append(f"<Row T='{t}' IX='{i}'><Cell N='X' V='{x:.10f}'/>"
                   f"<Cell N='Y' V='{y:.10f}'/></Row>")
    return "".join(out)


def geom_rect(w, h):
    return ("<Section N='Geometry' IX='0'><Cell N='NoFill' V='0'/><Cell N='NoLine' V='0'/>"
            + _rows((0, 0), (w, 0), (w, h), (0, h), (0, 0)) + "</Section>")


def geom_line(length, h):
    y = h / 2.0
    return ("<Section N='Geometry' IX='0'><Cell N='NoFill' V='1'/><Cell N='NoLine' V='0'/>"
            + _rows((0, y), (length, y)) + "</Section>")


def geom_diamond(w, h):
    return ("<Section N='Geometry' IX='0'><Cell N='NoFill' V='0'/><Cell N='NoLine' V='0'/>"
            + _rows((w / 2.0, h), (w, h / 2.0), (w / 2.0, 0), (0, h / 2.0), (w / 2.0, h))
            + "</Section>")


def geom_parallelogram(w, h, skew=0.22):
    dx = w * skew
    return ("<Section N='Geometry' IX='0'><Cell N='NoFill' V='0'/><Cell N='NoLine' V='0'/>"
            + _rows((dx, 0), (w, 0), (w - dx, h), (0, h), (dx, 0)) + "</Section>")


GEOM_KINDS = {'rect': geom_rect, 'diamond': geom_diamond,
              'parallelogram': geom_parallelogram}

# Visio 自带 UML 时序图「消息」主控形状的包围盒高度 = 5 mm，直接照抄；
# 连线包围盒高度必须为正，Height=0 的退化包围盒同样会让 Visio 丢弃连线。
LINE_BOX_H = 0.1968503937007874


def build_page(model, spec, st):
    lw = st['line_weight_pt'] / 72.0
    shapes, sid = [], 0
    for p in model['prims']:
        sid += 1
        x1, y1, x2, y2 = p['box']
        w, h = abs(x2 - x1), abs(y2 - y1)
        pinx, piny = (x1 + x2) / 2.0, (y1 + y2) / 2.0
        if p['kind'] == 'line':
            dx, dy = x2 - x1, y2 - y1
            length = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            cells = [_cell('PinX', f'{pinx:.8f}'), _cell('PinY', f'{piny:.8f}'),
                     _cell('Width', f'{length:.8f}'), _cell('Height', f'{LINE_BOX_H:.10f}'),
                     _cell('LocPinX', f'{length/2:.8f}', f='Width*0.5'),
                     _cell('LocPinY', f'{LINE_BOX_H/2:.10f}', f='Height*0.5'),
                     _cell('FlipX', '0'), _cell('FlipY', '0'),
                     _cell('ResizeMode', '0'),
                     _cell('Angle', f'{ang:.15f}', u='RAD'),
                     _cell('BeginX', f'{x1:.8f}'), _cell('BeginY', f'{y1:.8f}'),
                     _cell('EndX', f'{x2:.8f}'), _cell('EndY', f'{y2:.8f}'),
                     _cell('LineWeight', f'{lw:.6f}'), _cell('LineColor', '#000000'),
                     _cell('LinePattern', '2' if p['dash'] else '1'),
                     _cell('FillPattern', '0'), _cell('Rounding', '0'),
                     _cell('BeginArrow', '0'),
                     _cell('EndArrow', {'filled': '1', 'open': '2', None: '0'}[p['arrow']]),
                     _cell('EndArrowSize', '2')]
            body = _char(st['fs_msg']) + geom_line(length, LINE_BOX_H)
            name = '动态连接线'
        else:
            cells = [_cell('PinX', f'{pinx:.6f}'), _cell('PinY', f'{piny:.6f}'),
                     _cell('Width', f'{w:.6f}'), _cell('Height', f'{h:.6f}'),
                     _cell('LocPinX', f'{w/2:.6f}', f='Width*0.5'),
                     _cell('LocPinY', f'{h/2:.6f}', f='Height*0.5'),
                     _cell('Angle', '0'),
                     _cell('LineWeight', f'{lw:.6f}'),
                     _cell('LineColor', p.get('line') or '#000000'),
                     _cell('LinePattern', '1' if p.get('line') else '0'),
                     _cell('Rounding', f'{p.get("rounding", 0):.6f}'),
                     _cell('FillForegnd', p.get('fill') or '#FFFFFF'),
                     _cell('FillBkgnd', p.get('fill') or '#FFFFFF'),
                     _cell('FillPattern', '1' if p.get('fill') else '0'),
                     _cell('VerticalAlign', '1'),
                     _cell('LeftMargin', '0.02'), _cell('RightMargin', '0.02'),
                     _cell('TopMargin', '0.01'), _cell('BottomMargin', '0.01')]
            gk = p.get('geom_kind', 'rect')
            geom = GEOM_KINDS.get(gk, geom_rect)(w, h)
            body = _char(p['size']) + _para(p.get('align', 'center')) + geom
            if p.get('text'):
                body += f"<Text><cp IX='0'/><pp IX='0'/>{esc(p['text'])}"
                if p.get('two'):
                    body += "\r\n" + esc(p['two'])
                body += "</Text>"
            name = '矩形'
        shapes.append(f"<Shape ID='{sid}' Type='Shape' NameU='{name}' Name='{name}' "
                      "LineStyle='3' FillStyle='3' TextStyle='3'>"
                      + "".join(cells) + body + "</Shape>")
    return ("<?xml version='1.0' encoding='utf-8' ?>\n\n"
            f"<PageContents xmlns='{NS}' "
            "xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships' "
            "xml:space='preserve'><Shapes>" + "".join(shapes) + "</Shapes></PageContents>")


def build_pages(spec, st):
    nm = esc(spec.NAME)
    return ("<?xml version='1.0' encoding='utf-8' ?>\n\n"
            f"<Pages xmlns='{NS}' "
            "xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships' "
            "xml:space='preserve'>"
            f"<Page ID='0' NameU='{nm}' Name='{nm}' ViewScale='-1' "
            f"ViewCenterX='{st['page_w']/2:.4f}' ViewCenterY='{st['page_h']/2:.4f}'>"
            "<PageSheet LineStyle='0' FillStyle='0' TextStyle='0'>"
            f"<Cell N='PageWidth' V='{st['page_w']:.10f}'/>"
            f"<Cell N='PageHeight' V='{st['page_h']:.10f}'/>"
            "<Cell N='PageScale' V='0.03937007874015748' U='MM'/>"
            "<Cell N='DrawingScale' V='0.03937007874015748' U='MM'/>"
            "<Cell N='DrawingSizeType' V='0'/><Cell N='DrawingScaleType' V='0'/>"
            "<Cell N='InhibitSnap' V='0'/><Cell N='DrawingResizeType' V='1'/>"
            "</PageSheet><Rel r:id='rId1'/></Page></Pages>")


def build_core(spec):
    nm = esc(spec.NAME)
    return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n\n'
            '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" '
            'xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" '
            'xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
            f'<dc:title>{nm}</dc:title><dc:subject></dc:subject><dc:creator>齐烔</dc:creator>'
            '<cp:keywords></cp:keywords><dc:description></dc:description>'
            '<cp:lastModifiedBy>齐烔</cp:lastModifiedBy>'
            '<dcterms:created xsi:type="dcterms:W3CDTF">2026-09-20T00:00:00Z</dcterms:created>'
            '<dcterms:modified xsi:type="dcterms:W3CDTF">2026-09-20T00:00:00Z</dcterms:modified>'
            '<cp:category></cp:category><dc:language>zh-CN</dc:language></cp:coreProperties>')


def build_app(spec):
    nm = esc(spec.NAME)
    return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n\n'
            '<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" '
            'xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">'
            '<Template></Template><Application>Microsoft Visio</Application><ScaleCrop>false</ScaleCrop>'
            '<HeadingPairs><vt:vector size="4" baseType="variant">'
            '<vt:variant><vt:lpstr>页</vt:lpstr></vt:variant><vt:variant><vt:i4>1</vt:i4></vt:variant>'
            '<vt:variant><vt:lpstr>主控形状</vt:lpstr></vt:variant><vt:variant><vt:i4>2</vt:i4></vt:variant>'
            '</vt:vector></HeadingPairs><TitlesOfParts><vt:vector size="3" baseType="lpstr">'
            f'<vt:lpstr>{nm}</vt:lpstr><vt:lpstr>用例图-用例</vt:lpstr><vt:lpstr>用例图小人</vt:lpstr>'
            '</vt:vector></TitlesOfParts><Manager></Manager><Company></Company>'
            '<LinksUpToDate>false</LinksUpToDate><SharedDoc>false</SharedDoc><HyperlinkBase></HyperlinkBase>'
            '<HyperlinksChanged>false</HyperlinksChanged><AppVersion>16.0000</AppVersion></Properties>')


def build_windows(st):
    return ('<?xml version=\'1.0\' encoding=\'utf-8\' ?>\n\n'
            f"<Windows ClientWidth='1920' ClientHeight='1040' xmlns='{NS}' "
            "xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships' "
            "xml:space='preserve'>"
            "<Window ID='0' WindowType='Drawing' WindowState='1073741824' WindowLeft='0' WindowTop='0' "
            f"WindowWidth='1600' WindowHeight='1000' ContainerType='Page' Page='0' ViewScale='-1' "
            f"ViewCenterX='{st['page_w']/2:.4f}' ViewCenterY='{st['page_h']/2:.4f}'>"
            "<ShowRulers>1</ShowRulers><ShowGrid>0</ShowGrid><ShowPageBreaks>1</ShowPageBreaks>"
            "<ShowGuides>1</ShowGuides><ShowConnectionPoints>1</ShowConnectionPoints>"
            "<GlueSettings>9</GlueSettings><SnapSettings>65847</SnapSettings>"
            "<SnapExtensions>34</SnapExtensions><SnapAngles/><DynamicGridEnabled>1</DynamicGridEnabled>"
            "<TabSplitterPos>0.5</TabSplitterPos></Window></Windows>")


def write_vsdx(template, out_path, model, spec, st):
    page = build_page(model, spec, st)
    with zipfile.ZipFile(template) as z:
        parts = {n: z.read(n) for n in z.namelist()}
    parts['visio/pages/page1.xml'] = page.encode('utf-8')
    parts['visio/pages/pages.xml'] = build_pages(spec, st).encode('utf-8')
    parts['docProps/core.xml'] = build_core(spec).encode('utf-8')
    parts['docProps/app.xml'] = build_app(spec).encode('utf-8')
    parts['visio/windows.xml'] = build_windows(st).encode('utf-8')
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with zipfile.ZipFile(out_path, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, d in parts.items():
            z.writestr(n, d)
    return len(page)


# -------------------------------------------------------------------- png
def write_png(model, spec, st, out_path, scale=130):
    from PIL import Image, ImageDraw, ImageFont
    W, H = int(st['page_w'] * scale), int(st['page_h'] * scale)
    img = Image.new('RGB', (W, H), 'white')
    dr = ImageDraw.Draw(img)
    FONT_CJK = r"C:\Windows\Fonts\simsun.ttc"
    FONT_LAT = r"C:\Windows\Fonts\times.ttf"
    _cache = {}

    def font(size, latin):
        key = (round(size, 2), latin)
        if key not in _cache:
            path = FONT_LAT if latin else FONT_CJK
            try:
                _cache[key] = ImageFont.truetype(path, max(int(round(size / 72.0 * scale)), 6))
            except Exception:
                _cache[key] = ImageFont.load_default()
        return _cache[key]

    def runs(s):
        """把字符串切成 西文 / 中文 片段，便于用各自字体渲染（与 Visio 的 Font/AsianFont 一致）。"""
        out, cur, cur_lat = [], '', None
        for ch in s:
            lat = ord(ch) <= 0x2000
            if cur_lat is None or lat == cur_lat:
                cur += ch
                cur_lat = lat
            else:
                out.append((cur, cur_lat))
                cur, cur_lat = ch, lat
        if cur:
            out.append((cur, cur_lat))
        return out

    def rich_width(s, size):
        return sum(dr.textlength(t, font=font(size, lat)) for t, lat in runs(s))

    def rich_draw(x, y, s, size):
        for t, lat in runs(s):
            f = font(size, lat)
            dr.text((x, y), t, fill='black', font=f)
            x += dr.textlength(t, font=f)
        return x

    def tx(x):
        return x * scale

    def ty(y):
        return (st['page_h'] - y) * scale

    def draw_text(s, box, size, align, valign):
        x1, y1, x2, y2 = box
        lines = s.split('\r\n') if '\r\n' in s else s.split('\n')
        widths = [rich_width(ln, size) for ln in lines]
        f0 = font(size, False)
        ascend, descend = f0.getmetrics()
        heights = [ascend + descend] * len(lines)
        lh = size / 72.0 * scale * 0.30
        total = sum(heights) + lh * (len(lines) - 1)
        lx1, lx2 = tx(x1), tx(x2)
        ly1, ly2 = ty(y2), ty(y1)
        cy = (ly1 + ly2) / 2 - total / 2
        for i, ln in enumerate(lines):
            if align == 'left':
                cx = lx1 + 2
            elif align == 'right':
                cx = lx2 - widths[i] - 2
            else:
                cx = (lx1 + lx2) / 2 - widths[i] / 2
            rich_draw(cx, cy, ln, size)
            cy += heights[i] + lh

    def arrowhead(x, y, direction, filled=True, size=9):
        if filled:
            dr.polygon([(x, y), (x - direction * size, y - size * 0.42),
                        (x - direction * size, y + size * 0.42)], fill='black')
        else:
            dr.line([(x, y), (x - direction * size, y - size * 0.5)], fill='black', width=2)
            dr.line([(x, y), (x - direction * size, y + size * 0.5)], fill='black', width=2)

    def dashed(p1, p2, dash=9, gap=6, width=2):
        x1, y1 = p1
        x2, y2 = p2
        dist = math.hypot(x2 - x1, y2 - y1)
        if dist == 0:
            return
        dx, dy = (x2 - x1) / dist, (y2 - y1) / dist
        n = 0.0
        while n < dist:
            m = min(n + dash, dist)
            dr.line([(x1 + dx * n, y1 + dy * n), (x1 + dx * m, y1 + dy * m)], fill='black', width=width)
            n += dash + gap

    for p in model['prims']:
        x1, y1, x2, y2 = p['box']
        if p['kind'] == 'rect':
            shp = p.get('shape', 'rect')
            l, t, r, b = tx(x1), ty(y2), tx(x2), ty(y1)
            fill = 'white' if p.get('fill') else None
            if shp == 'diamond':
                dr.polygon([((l + r) / 2, t), (r, (t + b) / 2), ((l + r) / 2, b), (l, (t + b) / 2)],
                           fill=fill, outline='black', width=2)
            elif shp == 'parallelogram':
                dx = (r - l) * 0.22
                dr.polygon([(l + dx, t), (r, t), (r - dx, b), (l, b)],
                           fill=fill, outline='black', width=2)
            elif shp == 'roundrect':
                rad = int((b - t) * 0.45)
                dr.rounded_rectangle([l, t, r, b], radius=rad, fill=fill, outline='black', width=2)
            else:
                dr.rectangle([l, t, r, b], fill=fill, outline='black', width=2)
            if p.get('text'):
                s = p['text'] + (('\r\n' + p['two']) if p.get('two') else '')
                draw_text(s, p['box'], p['size'], p.get('align', 'center'), p.get('valign', 'middle'))
        elif p['kind'] == 'text':
            draw_text(p['text'], p['box'], p['size'], p['align'], p.get('valign', 'middle'))
        elif p['kind'] == 'line':
            if p['dash']:
                dashed((tx(x1), ty(y1)), (tx(x2), ty(y2)))
            else:
                dr.line([tx(x1), ty(y1), tx(x2), ty(y2)], fill='black', width=2)
            if p['arrow']:
                arrowhead(tx(x2), ty(y2), 1 if x2 > x1 else -1, filled=(p['arrow'] == 'filled'))

    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    img.save(out_path)
    return img.size


# ------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--spec', required=True)
    ap.add_argument('--out-vsdx', required=True)
    ap.add_argument('--out-png', required=True)
    ap.add_argument('--out-report', default=None)
    ap.add_argument('--template', default=DEFAULT_TEMPLATE)
    ap.add_argument('--scale', type=int, default=130)
    a = ap.parse_args()

    spec = load_spec(a.spec)
    st = dict(DEFAULT_STYLE)
    st.update(getattr(spec, 'STYLE', {}) or {})
    model = layout(spec, st)

    size = write_vsdx(a.template, a.out_vsdx, model, spec, st)
    pix = write_png(model, spec, st, a.out_png, a.scale)

    report = dict(name=spec.NAME, style=st, shapes=len(model['prims']),
                  messages=len(spec.MSGS), lifelines=len(spec.LIFELINES),
                  life_bottom=model['life_bottom'], page_xml_bytes=size,
                  png_size=pix, prims=model['prims'],
                  frames=[list(f) for f in spec.FRAMES],
                  frame_span={str(k): v for k, v in model['frame_span'].items()},
                  y_of={str(k): v for k, v in model['y_of'].items()})
    rp = a.out_report or (os.path.splitext(a.out_vsdx)[0] + '.layout.json')
    with open(rp, 'w', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=1)

    print(f"vsdx : {a.out_vsdx}  ({os.path.getsize(a.out_vsdx)} bytes)")
    print(f"png  : {a.out_png}  {pix}")
    print(f"report: {rp}")
    print(f"shapes={len(model['prims'])} messages={len(spec.MSGS)} "
          f"lifelines={len(spec.LIFELINES)} life_bottom={model['life_bottom']:.2f}in")


if __name__ == '__main__':
    main()
