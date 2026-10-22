# -*- coding: utf-8 -*-
"""
thesis-visio-figure / build_structure_vsdx.py

功能结构图（树形层级图）生成器：顶层系统名 → 功能模块 → 子功能。
子功能框内文字竖排（一行一个字），层间用直角折线汇流。

VSDX 打包与 PNG 预览复用 build_sequence_vsdx.py 的通用工具箱；
几何坐标由本脚本按每个图形实际尺寸算成**数值**后再写入 XML
（VSDX 的 Cell 里 V 必须是计算结果，公式只能放 F —— 见 SKILL.md §7.1）。

用法：
    python build_structure_vsdx.py --spec specs/figure_3_1_structure.py \
        --out-vsdx out.vsdx --out-png out.png --out-report out.layout.json
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)
from build_sequence_vsdx import (  # noqa: E402  通用 VSDX/PNG 工具箱
    DEFAULT_STYLE, DEFAULT_TEMPLATE, load_spec, text_w, write_png, write_vsdx,
)

STRUCT_STYLE = dict(
    page_w=11.69, page_h=6.30,
    fs_msg=17.5, fs_life=17.5,
    line_weight_pt=0.75,
    insert_width_cm=15.5, max_aspect=1.50, min_bottom=0.14,
    text_pad=0.10, tol_text=0.02, tol_bar=0.03,
    line_em=1.45,
    leaf_pad_x=0.055, leaf_pad_y=0.07, leaf_gap=0.07,
    mod_gap=0.12, name_pad_x=0.24, box_pad_y=0.12,
    top_margin=0.46,
    gap_root_bus=0.30, gap_bus_mod=0.28, gap_mod_bus=0.28, gap_bus_leaf=0.26,
)


def layout(spec, st):
    fs = st['fs_msg']
    em = fs / 72.0                       # 一个汉字宽
    adv = em * st['line_em']             # 一行高
    prims = []

    # ---------------------------------------------------------- 1) 尺寸计算
    leaf_w = em + 2 * st['leaf_pad_x']
    max_leaf_n = max(len(t) for _, _, leaves in spec.MODULES for t in leaves)
    leaf_h = max_leaf_n * adv + 2 * st['leaf_pad_y']

    root_lines = list(spec.ROOT)
    root_w = max(text_w(t, fs) for t in root_lines) + st['name_pad_x']
    root_h = len(root_lines) * adv + st['box_pad_y']

    mod_h = max(len(ml) for _, ml, _ in spec.MODULES) * adv + st['box_pad_y']

    mods = []
    for name, name_lines, leaves in spec.MODULES:
        span = len(leaves) * leaf_w + (len(leaves) - 1) * st['leaf_gap']
        name_w = max(text_w(t, fs) for t in name_lines) + st['name_pad_x']
        mods.append(dict(name=name, name_lines=list(name_lines), leaves=list(leaves),
                         w=max(span, name_w), span=span))

    total_w = sum(m['w'] for m in mods) + (len(mods) - 1) * st['mod_gap']
    margin_x = max((st['page_w'] - total_w) / 2.0, 0.10)
    if margin_x < 0.10:
        raise SystemExit(f"内容宽 {total_w:.2f} in 超出页面宽 {st['page_w']} in")

    # ---------------------------------------------------------- 2) 竖向定位
    root_top = st['page_h'] - st['top_margin']
    root_bot = root_top - root_h
    bus1_y = root_bot - st['gap_root_bus']
    mod_top = bus1_y - st['gap_bus_mod']
    mod_bot = mod_top - mod_h
    bus2_y = mod_bot - st['gap_mod_bus']
    leaf_top = bus2_y - st['gap_bus_leaf']
    leaf_bot = leaf_top - leaf_h

    # ---------------------------------------------------------- 3) 横向定位
    x = margin_x
    for m in mods:
        m['x1'], m['x2'] = x, x + m['w']
        m['cx'] = (m['x1'] + m['x2']) / 2.0
        lw_span = len(m['leaves']) * leaf_w + (len(m['leaves']) - 1) * st['leaf_gap']
        lx = m['cx'] - lw_span / 2.0
        m['leaf_cx'] = []
        for _ in m['leaves']:
            m['leaf_cx'].append(lx + leaf_w / 2.0)
            lx += leaf_w + st['leaf_gap']
        x += m['w'] + st['mod_gap']

    root_cx = mods[len(mods) // 2]['cx']

    # ---------------------------------------------------------- 4) 连线
    def line(x1, y1, x2, y2):
        prims.append(dict(kind='line', role='flow', box=[x1, y1, x2, y2],
                          dash=False, arrow=None))

    # 根 → 汇流线 → 各模块
    line(root_cx, root_bot, root_cx, bus1_y)
    line(mods[0]['cx'], bus1_y, mods[-1]['cx'], bus1_y)
    for m in mods:
        line(m['cx'], bus1_y, m['cx'], mod_top)

    # 模块 → 汇流线 → 各子功能
    for m in mods:
        line(m['cx'], mod_bot, m['cx'], bus2_y)
        line(m['leaf_cx'][0], bus2_y, m['leaf_cx'][-1], bus2_y)
        for cx in m['leaf_cx']:
            line(cx, bus2_y, cx, leaf_top)

    # ---------------------------------------------------------- 5) 图形
    def box(x1, y1, x2, y2, text, gw, gh, role='node'):
        prims.append(dict(kind='rect', role=role, shape='rect', box=[x1, y1, x2, y2],
                          text=text, size=fs, align='center', valign='middle',
                          fill='#FFFFFF', line='#000000', rounding=0.0,
                          glyph_w=gw, glyph_box=[(x1 + x2) / 2.0 - gw / 2.0,
                                                 (y1 + y2) / 2.0 - gh / 2.0,
                                                 (x1 + x2) / 2.0 + gw / 2.0,
                                                 (y1 + y2) / 2.0 + gh / 2.0]))

    box(root_cx - root_w / 2, root_bot, root_cx + root_w / 2, root_top,
        "\r\n".join(root_lines),
        max(text_w(t, fs) for t in root_lines), len(root_lines) * adv)

    for m in mods:
        box(m['x1'], mod_bot, m['x2'], mod_top, "\r\n".join(m['name_lines']),
            max(text_w(t, fs) for t in m['name_lines']), len(m['name_lines']) * adv)
        for txt, cx in zip(m['leaves'], m['leaf_cx']):
            box(cx - leaf_w / 2, leaf_bot, cx + leaf_w / 2, leaf_top,
                "\r\n".join(txt), em, len(txt) * adv)

    model = dict(prims=prims, life_bottom=leaf_bot, y_of={}, frame_span={})
    model['meta'] = dict(content_w=total_w, margin_x=margin_x, leaf_w=leaf_w,
                         leaf_h=leaf_h, mod_h=mod_h, root_w=root_w, root_h=root_h,
                         bus1_y=bus1_y, bus2_y=bus2_y, root_cx=root_cx)
    return model


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
    st.update(STRUCT_STYLE)
    st.update(getattr(spec, 'STYLE', {}) or {})
    model = layout(spec, st)

    size = write_vsdx(a.template, a.out_vsdx, model, spec, st)
    pix = write_png(model, spec, st, a.out_png, a.scale)

    n_lines = sum(1 for p in model['prims'] if p['kind'] == 'line')
    report = dict(name=spec.NAME, style=st, shapes=len(model['prims']),
                  lifelines=0, messages=0, frames=[],
                  life_bottom=model['life_bottom'], n_lines=n_lines,
                  page_xml_bytes=size, png_size=pix, meta=model['meta'],
                  prims=model['prims'])
    rp = a.out_report or (os.path.splitext(a.out_vsdx)[0] + '.layout.json')
    os.makedirs(os.path.dirname(os.path.abspath(rp)), exist_ok=True)
    with open(rp, 'w', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=1)

    print(f"vsdx  : {a.out_vsdx}  ({os.path.getsize(a.out_vsdx)} bytes)")
    print(f"png   : {a.out_png}  {pix}")
    print(f"report: {rp}")
    print(f"shapes={len(model['prims'])} lines={n_lines} "
          f"modules={len(spec.MODULES)} leaves={sum(len(m[2]) for m in spec.MODULES)}")
    print(f"内容宽 {model['meta']['content_w']:.2f} in，左右留白 {model['meta']['margin_x']:.2f} in")
    return 0


if __name__ == '__main__':
    sys.exit(main())
