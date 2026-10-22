# -*- coding: utf-8 -*-
"""图2-1 系统总体用例图 —— 用例图引擎（复用 build_sequence_vsdx 的 VSDX 写入与 PNG 渲染）。

图元约定
  ACTORS    = [(id, 名称, x, y, 朝向)]        朝向: 'right' 参与者在左侧,'left' 在右侧
  SYSTEM    = (系统名, x1, y1, x2, y2)        系统边界矩形
  USE_CASES = [(id, 名称, cx, cy)]            椭圆用例,中心坐标
  LINKS     = [(actor_id, use_case_id)]       UML 关联（无箭头）
"""
import sys, io, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from build_sequence_vsdx import write_vsdx, write_png, text_w, DEFAULT_STYLE

UC_W, UC_H = 2.00, 0.64        # 用例椭圆
HEAD_R = 0.13                  # 参与者头部半径

STYLE = dict(DEFAULT_STYLE)
STYLE.update(dict(insert_width_cm=15.5))


def build_prims(spec, st):
    prims = []
    fs = st['fs_msg']
    name, x1, y1, x2, y2 = spec.SYSTEM

    # ---- 系统边界 ----
    prims.append(dict(kind='rect', shape='rect', geom_kind='rect',
                      box=[x1, y1, x2, y2], fill=None, line='#000000',
                      text=None, size=fs))
    # 边界标题：放在边界内左上（按文字宽度定宽，避免超出页面）
    # 字号与用例文字一致，保证按 15.5cm 插入后仍 ≥ 9pt
    gw_name = text_w(name, fs)
    prims.append(dict(kind='rect', shape='rect', geom_kind='rect',
                      box=[x1 + 0.10, y2 - 0.46, x1 + 0.20 + gw_name, y2 - 0.10],
                      fill=None, line=None, text=name, size=fs, align='left'))

    # ---- 用例椭圆 ----
    for cid, text, cx, cy in spec.USE_CASES:
        gw = text_w(text, fs)
        prims.append(dict(kind='rect', shape='ellipse', geom_kind='ellipse',
                          box=[cx - UC_W / 2, cy - UC_H / 2, cx + UC_W / 2, cy + UC_H / 2],
                          fill='#FFFFFF', line='#000000', text=text, size=fs, align='center',
                          _glyph_w=gw))

    # ---- 参与者（火柴人 + 名称）----
    for aid, aname, ax, ay, face in spec.ACTORS:
        # 头
        prims.append(dict(kind='rect', shape='ellipse', geom_kind='ellipse',
                          box=[ax - HEAD_R, ay + 0.32, ax + HEAD_R, ay + 0.32 + 2 * HEAD_R],
                          fill=None, line='#000000', text=None, size=fs))
        # 躯干
        prims.append(dict(kind='line', box=[ax, ay + 0.32, ax, ay - 0.10],
                          dash=False, arrow=None))
        # 双臂
        prims.append(dict(kind='line', box=[ax - 0.22, ay + 0.14, ax + 0.22, ay + 0.14],
                          dash=False, arrow=None))
        # 双腿
        prims.append(dict(kind='line', box=[ax, ay - 0.10, ax - 0.19, ay - 0.40],
                          dash=False, arrow=None))
        prims.append(dict(kind='line', box=[ax, ay - 0.10, ax + 0.19, ay - 0.40],
                          dash=False, arrow=None))
        # 名称：贴着图形外侧排布，避免被关联线穿过
        gw_a = text_w(aname, fs)
        if face == 'right':
            lx2 = ax + 0.14
            lx1 = lx2 - gw_a - 0.10
        else:
            lx1 = ax - 0.14
            lx2 = lx1 + gw_a + 0.10
        prims.append(dict(kind='rect', shape='rect', geom_kind='rect',
                          box=[lx1, ay - 0.76, lx2, ay - 0.48],
                          fill=None, line=None, text=aname, size=fs, align='center'))

    # ---- 关联 ----
    actors = {a[0]: a for a in spec.ACTORS}
    ucs = {c[0]: c for c in spec.USE_CASES}
    for aid, cid in spec.LINKS:
        _, _, ax, ay, face = actors[aid]
        _, _, cx, cy = ucs[cid]
        if face == 'right':
            sx, sy = ax + 0.22, ay + 0.10
            ex, ey = cx - UC_W / 2, cy
        else:
            sx, sy = ax - 0.22, ay + 0.10
            ex, ey = cx + UC_W / 2, cy
        prims.append(dict(kind='line', box=[sx, sy, ex, ey], dash=False, arrow=None))
    return prims


def write_report(prims, spec, st, out_path):
    import json
    rows = []
    for p in prims:
        if p['kind'] == 'line':
            rows.append(dict(role='assoc', box=p['box'], text='', size=st['fs_msg']))
        else:
            gw = p.get('_glyph_w') or (text_w(p['text'], p.get('size', st['fs_msg'])) if p.get('text') else 0)
            rows.append(dict(role='node', shape=p.get('shape', 'rect'), box=p['box'],
                             text=p.get('text') or '', size=p.get('size', st['fs_msg']),
                             glyph_w=gw))
    json.dump(dict(name=spec.NAME, style=st, prims=rows,
                   page=[st['page_w'], st['page_h']]),
              open(out_path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)


def main():
    import argparse, importlib.util
    ap = argparse.ArgumentParser()
    ap.add_argument('--spec', required=True)
    ap.add_argument('--out-vsdx', required=True)
    ap.add_argument('--out-png', required=True)
    ap.add_argument('--out-report', default=None)
    a = ap.parse_args()

    spec_mod = importlib.util.spec_from_file_location('uc_spec', a.spec)
    mod = importlib.util.module_from_spec(spec_mod)
    spec_mod.loader.exec_module(mod)
    spec = mod

    st = dict(STYLE)
    st['page_w'], st['page_h'] = spec.PAGE
    prims = build_prims(spec, st)
    model = {'prims': prims}

    template = os.path.join(os.path.dirname(HERE), 'assets', 'visio-template.vsdx')
    write_vsdx(template, a.out_vsdx, model, spec, st)
    write_png(model, spec, st, a.out_png, scale=150)
    if a.out_report:
        write_report(prims, spec, st, a.out_report)

    n_box = sum(1 for p in prims if p['kind'] != 'line')
    n_line = sum(1 for p in prims if p['kind'] == 'line')
    print(f"shapes={len(prims)} 图元={n_box} 连线={n_line} "
          f"page={st['page_w']}x{st['page_h']}in 用例={len(spec.USE_CASES)} 参与者={len(spec.ACTORS)}")


if __name__ == '__main__':
    main()

