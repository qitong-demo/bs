# -*- coding: utf-8 -*-
"""
thesis-visio-figure / build_flowchart_vsdx.py

按图定义（spec）生成符合哈尔滨信息工程学院论文规范的可编辑 Visio 流程图 (.vsdx)，
同时输出 PNG 版式预览和 JSON 版式报告（供 check_figure.py 自检）。

VSDX 打包与 PNG 预览复用 build_sequence_vsdx.py 里的通用部件（该文件的 main 有 __main__ 保护）。

spec 模块需定义：
    NAME   图名
    NODES  [(id, 图元, 文字, x, y, w, h), ...]
           图元：term 起止(圆角矩形) / process 处理(矩形) / decision 判断(菱形) / io 输入输出(平行四边形)
    EDGES  [(类型, ...), ...]
           ('v', src, dst, label)              竖向箭头，端点按两框上下边自动算
           ('h', src, dst, label)              横向箭头，端点按两框左右边自动算
           ('poly', [(x,y),...], label, label_at)  折线（末段带箭头）
    STYLE  dict（可选，覆盖默认版式）

用法：
    python build_flowchart_vsdx.py --spec specs/figure_4_2_xxx.py \
        --out-vsdx out.vsdx --out-png out.png
"""
import argparse, json, math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)
from build_sequence_vsdx import (  # noqa: E402  通用 VSDX/PNG 工具箱
    DEFAULT_STYLE, build_pages, build_core, build_app, build_windows, build_page,
    write_png, load_spec, DEFAULT_TEMPLATE, text_w,
)

SHAPES = {
    'term':     ('roundrect',     'rect',          0.16),
    'process':  ('rect',          'rect',          0.0),
    'decision': ('diamond',       'diamond',       0.0),
    'io':       ('parallelogram', 'parallelogram', 0.0),
}

FLOW_STYLE = dict(
    fs_msg=12.25, fs_label=11.5,
    line_weight_pt=0.75,
    box_top_margin=0.30, box_bottom_margin=0.30,
    insert_width_cm=15.5, max_aspect=1.52,
    min_bottom=0.14,
    tol_text=0.02, tol_bar=0.03, tol_border=0.01,
    page_w=8.27, page_h=9.0,
)


# ------------------------------------------------------------------ 版面计算
def layout(spec, st):
    nodes = {}
    for nid, kind, txt, x, y, w, h in spec.NODES:
        nodes[nid] = dict(kind=kind, text=txt, x=x, y=y, w=w, h=h,
                          box=[x - w / 2.0, y - h / 2.0, x + w / 2.0, y + h / 2.0])

    lines, labels = [], []      # 先连线，再节点，最后标签（保证遮挡关系正确）

    def add_line(pts, arrow=True):
        for i in range(len(pts) - 1):
            (ax, ay), (bx, by) = pts[i], pts[i + 1]
            lines.append(dict(kind='line', role='flow',
                              box=[ax, ay, bx, by],
                              dash=False,
                              arrow=('filled' if (arrow and i == len(pts) - 2) else None)))

    def add_label(txt, cx, cy, align='center'):
        if not txt:
            return
        gw = text_w(txt, st['fs_label'])
        gh = st['fs_label'] / 72.0 * 1.25
        labels.append(dict(kind='text', role='flow_label',
                           box=[cx - gw / 2 - 0.03, cy - gh / 2, cx + gw / 2 + 0.03, cy + gh / 2],
                           text=txt, size=st['fs_label'], align=align, valign='middle',
                           glyph_w=gw,
                           glyph_box=[cx - gw / 2, cy - gh / 2, cx + gw / 2, cy + gh / 2]))

    for e in spec.EDGES:
        kind = e[0]
        if kind == 'v':
            _, src, dst, label = (list(e) + [None])[:4]
            a, b = nodes[src], nodes[dst]
            if b['y'] > a['y']:
                a, b = b, a
            x = a['x']
            y1, y2 = a['box'][1], b['box'][3]          # 上框底边 → 下框顶边
            add_line([(x, y1), (x, y2)])
            if label:
                add_label(label, x + 0.16, (y1 + y2) / 2.0, 'left')
        elif kind == 'h':
            _, src, dst, label = (list(e) + [None])[:4]
            a, b = nodes[src], nodes[dst]
            if b['x'] < a['x']:
                a, b = b, a
            yy = a['y']
            x1, x2 = a['box'][2], b['box'][0]
            add_line([(x1, yy), (x2, yy)])
            if label:
                add_label(label, (x1 + x2) / 2.0, yy + 0.16)
        elif kind == 'poly':
            pts = e[1]
            label = e[2] if len(e) > 2 else None
            lab_at = e[3] if len(e) > 3 else None
            arrow = e[4] if len(e) > 4 else True
            add_line(pts, arrow=arrow)
            if label and lab_at:
                add_label(label, lab_at[0], lab_at[1])

    # 先画节点、再画连线、最后画标签：连线与箭头压在框线之上，箭头不会被白底框遮住
    prims = []
    for nid, kind, txt, x, y, w, h in spec.NODES:
        shape, geom_kind, rounding = SHAPES[kind]
        prims.append(dict(kind='rect', role='node', shape=shape, geom_kind=geom_kind,
                          rounding=rounding,
                          box=[x - w / 2.0, y - h / 2.0, x + w / 2.0, y + h / 2.0],
                          fill='#FFFFFF', line='#000000', text=txt, two=None,
                          size=st['fs_msg'], align='center', valign='middle',
                          glyph_w=text_w(txt, st['fs_msg'])))
    prims.extend(lines)
    prims.extend(labels)

    xs = [p['box'][0] for p in prims] + [p['box'][2] for p in prims]
    ys = [p['box'][1] for p in prims] + [p['box'][3] for p in prims]
    return dict(prims=prims, nodes=nodes, content_bbox=[min(xs), min(ys), max(xs), max(ys)])


def fit_page(spec, st, model):
    """按内容自动定页高/页宽（若 spec 未显式给出）。"""
    x1, y1, x2, y2 = model['content_bbox']
    if not st.get('page_w'):
        st['page_w'] = round(x2 + st['box_bottom_margin'] + 0.10, 2)
    if not st.get('page_h'):
        st['page_h'] = round(y2 + st['box_top_margin'], 2)
    return st


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
    st.update(FLOW_STYLE)
    st.update(getattr(spec, 'STYLE', {}) or {})
    if getattr(spec, 'PAGE', None):
        st['page_w'], st['page_h'] = spec.PAGE

    model = layout(spec, st)
    fit_page(spec, st, model)

    page = build_page(model, spec, st)
    import zipfile
    with zipfile.ZipFile(a.template) as z:
        parts = {n: z.read(n) for n in z.namelist()}
    parts['visio/pages/page1.xml'] = page.encode('utf-8')
    parts['visio/pages/pages.xml'] = build_pages(spec, st).encode('utf-8')
    parts['docProps/core.xml'] = build_core(spec).encode('utf-8')
    parts['docProps/app.xml'] = build_app(spec).encode('utf-8')
    parts['visio/windows.xml'] = build_windows(st).encode('utf-8')
    os.makedirs(os.path.dirname(os.path.abspath(a.out_vsdx)), exist_ok=True)
    with zipfile.ZipFile(a.out_vsdx, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, d in parts.items():
            z.writestr(n, d)

    pix = write_png(model, spec, st, a.out_png, a.scale)

    report = dict(name=spec.NAME, style=st, shapes=len(model['prims']),
                  messages=0, lifelines=0, frames=[],
                  content_bbox=model['content_bbox'],
                  page_xml_bytes=len(page), png_size=pix, prims=model['prims'])
    rp = a.out_report or (os.path.splitext(a.out_vsdx)[0] + '.layout.json')
    with open(rp, 'w', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=1)

    print(f"vsdx : {a.out_vsdx}  ({os.path.getsize(a.out_vsdx)} bytes)")
    print(f"png  : {a.out_png}  {pix}")
    print(f"report: {rp}")
    print(f"shapes={len(model['prims'])} nodes={len(spec.NODES)} edges={len(spec.EDGES)} "
          f"page={st['page_w']}x{st['page_h']}in content={['%.2f' % v for v in model['content_bbox']]}")


if __name__ == '__main__':
    main()
