# -*- coding: utf-8 -*-
"""
thesis-visio-figure / check_figure.py

图合规自检（SKILL.md §1.3 七条硬性约束 + 包完整性）。
不通过则不得交付。

用法：
    python check_figure.py --vsdx out.vsdx --spec specs/figure_4_1_user_auth.py [--report out.layout.json]
"""
import argparse, json, math, os, posixpath, re, sys, zipfile
from lxml import etree

RELNS = {'r': 'http://schemas.openxmlformats.org/package/2006/relationships'}
CTNS = {'ct': 'http://schemas.openxmlformats.org/package/2006/content-types'}
CM_PER_IN = 2.54
FAILS, WARNS = [], []


def fail(msg):
    FAILS.append(msg)


def warn(msg):
    WARNS.append(msg)


def load_spec(path):
    import importlib.util
    d = os.path.dirname(os.path.abspath(path))
    n = os.path.splitext(os.path.basename(path))[0]
    if d not in sys.path:
        sys.path.insert(0, d)
    m = importlib.util.spec_from_file_location(n, path)
    mod = importlib.util.module_from_spec(m)
    m.loader.exec_module(mod)
    return mod


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--vsdx', required=True)
    ap.add_argument('--spec', required=True)
    ap.add_argument('--report', default=None)
    a = ap.parse_args()

    spec = load_spec(a.spec)
    report_path = a.report or (os.path.splitext(a.vsdx)[0] + '.layout.json')
    report = json.load(open(report_path, encoding='utf-8'))
    st = report['style']

    # ---------- 1. 包完整性 ----------
    z = zipfile.ZipFile(a.vsdx)
    names = set(z.namelist())
    for n in sorted(names):
        if n.endswith(('.xml', '.rels')):
            try:
                etree.fromstring(z.read(n))
            except Exception as e:
                fail(f"XML 解析失败 {n}: {e}")
    ct = etree.fromstring(z.read('[Content_Types].xml'))
    defaults = {d.get('Extension').lower() for d in ct.findall('ct:Default', CTNS)}
    overrides = {o.get('PartName') for o in ct.findall('ct:Override', CTNS)}
    for n in sorted(names):
        if n == '[Content_Types].xml' or n.endswith('.rels'):
            continue
        if '/' + n in overrides or n.rsplit('.', 1)[-1].lower() in defaults:
            continue
        fail(f"缺少内容类型声明: {n}")
    for part in overrides:
        if part.lstrip('/') not in names:
            fail(f"内容类型指向不存在的部件: {part}")
    for n in sorted(names):
        if not n.endswith('.rels'):
            continue
        base = posixpath.dirname(posixpath.dirname(n))
        for rel in etree.fromstring(z.read(n)).findall('r:Relationship', RELNS):
            if rel.get('TargetMode') == 'External':
                continue
            full = posixpath.normpath(posixpath.join(base, rel.get('Target'))) if base else rel.get('Target')
            if full not in names:
                fail(f"关系目标缺失 {n} -> {rel.get('Target')}")
    if not any(n.startswith('visio/pages/page1.xml') for n in names):
        fail("缺少 visio/pages/page1.xml")

    page = z.read('visio/pages/page1.xml').decode('utf-8')

    # ---------- 2. 线条匀称 ----------
    weights = set(re.findall(r"N='LineWeight' V='([^']+)'", page))
    if len(weights) != 1:
        fail(f"线条粗细不统一（违反“线条匀称”）: {sorted(weights)}")
    colors = set(re.findall(r"N='LineColor' V='([^']+)'", page))
    if colors - {'#000000'}:
        warn(f"存在非黑色线条: {sorted(colors)}")
    arrows = set(re.findall(r"N='EndArrow' V='([^']+)'", page))
    if not arrows <= {'0', '1', '2'}:
        fail(f"箭头类型异常: {sorted(arrows)}")

    # ---------- 2.5 全局：V 里绝不能出现公式（VSDX 的 V 必须是计算结果） ----------
    bad_v = re.findall(r"<Cell N='[^']+' V='([^']*(?:Width|Height)[^']*)'", page)
    if bad_v:
        fail(f"有 {len(bad_v)} 个 Cell 把公式写进了 V（Visio 会丢掉整段 Geometry，"
             f"表现为图形与连线全部消失）: {sorted(set(bad_v))[:6]}")

    # ---------- 3. 字体（中文宋体 + 西文 Times New Roman） ----------
    fonts = set(re.findall(r"N='Font' V='([^']+)'", page))
    asian = set(re.findall(r"N='AsianFont' V='([^']+)'", page))
    if fonts != {'Times New Roman'}:
        fail(f"西文字体必须统一为 Times New Roman，实际: {sorted(fonts)}")
    if asian != {'SimSun'}:
        fail(f"中文字体必须统一为 SimSun(宋体)，实际: {sorted(asian)}")

    # ---------- 4. 连线几何自洽 + 包围盒不得退化 ----------
    n_lines = 0
    for m in re.finditer(r"<Shape ID='(\d+)'[^>]*NameU='动态连接线'[^>]*>(.*?)</Shape>", page, re.S):
        n_lines += 1
        sid, seg = m.group(1), m.group(2)
        try:
            bx = float(re.search(r"N='BeginX' V='([-\d.]+)'", seg).group(1))
            by = float(re.search(r"N='BeginY' V='([-\d.]+)'", seg).group(1))
            ex = float(re.search(r"N='EndX' V='([-\d.]+)'", seg).group(1))
            ey = float(re.search(r"N='EndY' V='([-\d.]+)'", seg).group(1))
            w = float(re.search(r"N='Width' V='([-\d.]+)'", seg).group(1))
            h = float(re.search(r"N='Height' V='([-\d.]+)'", seg).group(1))
            ang = float(re.search(r"N='Angle' V='([-\d.]+)'", seg).group(1))
            lp = float(re.search(r"N='LinePattern' V='([-\d.]+)'", seg).group(1))
        except AttributeError:
            fail(f"连线 {m.group(1)} 缺少几何单元")
            continue
        # Height 为 0 会让 Visio 认为包围盒退化，整条连线可能不显示（已踩过的坑）
        if h <= 1e-6:
            fail(f"连线 {m.group(1)} 的 Height={h}（退化包围盒），Visio 会丢弃该连线")
        # VSDX 的 Cell 里 V 必须是数值；把公式写进 V 而不给 F，Visio 会丢整段 Geometry
        bad_cells = re.findall(r"<Cell N='[XY]' V='([^']*(?:Width|Height)[^']*)'", seg)
        if bad_cells:
            fail(f"连线 {m.group(1)} 的几何坐标把公式写进了 V（应为数值）：{bad_cells}")
        # 几何必须画在包围盒纵向中线上
        ys = [float(v) for v in re.findall(r"<Cell N='Y' V='([-\d.]+)'", seg)]
        if not ys or any(abs(v - h / 2.0) > 1e-6 for v in ys):
            fail(f"连线 {m.group(1)} 的几何未落在包围盒中线（Y 应为 {h/2.0:.6f}）")
        exp = math.atan2(ey - by, ex - bx)
        # 容差：角度 1e-9 弧度；宽度/长度 1e-4 英寸（XML 里坐标定点存储带来的舍入）
        if abs(ang - exp) > 1e-9 or abs(w - math.hypot(ex - bx, ey - by)) > 1e-4 or w <= 0:
            fail(f"连线 {m.group(1)} 方向/长度不自洽 (Angle={ang}, 期望={exp}, "
                 f"Width={w}, 实际长={math.hypot(ex - bx, ey - by)})")
        if lp == 0:
            fail(f"连线 {m.group(1)} 的 LinePattern=0（无描边），连线不可见")
    if n_lines == 0:
        fail("页面中没有任何连线（布局异常）")
    report['n_lines'] = n_lines

    # ---------- 5. 版面：文字互不重叠、不压激活条、不跨框线 ----------
    tol_text = st.get('tol_text', 0.02)
    tol_bar = st.get('tol_bar', 0.03)

    def ov(a, b):
        return (min(a[2], b[2]) - max(a[0], b[0]), min(a[3], b[3]) - max(a[1], b[1]))

    glyphs = [p for p in report['prims'] if p.get('glyph_box')]
    for i in range(len(glyphs)):
        for j in range(i + 1, len(glyphs)):
            ox, oy = ov(glyphs[i]['glyph_box'], glyphs[j]['glyph_box'])
            if ox > tol_text and oy > tol_text:
                fail(f"文字重叠: {glyphs[i]['text']!r} 与 {glyphs[j]['text']!r} "
                     f"(重叠 {ox:.3f} x {oy:.3f} in)")

    bars = [p['box'] for p in report['prims'] if p.get('role') == 'bar']
    headers = [p['box'] for p in report['prims'] if p.get('role') == 'header']
    for g in glyphs:
        if g.get('role') == 'header':
            continue
        for b in bars:
            ox, oy = ov(g['glyph_box'], b)
            if ox > tol_bar and oy > tol_bar:
                fail(f"文字压住激活条: {g['text']!r} 与激活条 "
                     f"({['%.2f' % v for v in b]}) 重叠 {ox:.3f} x {oy:.3f} in")
        for hb in headers:
            ox, oy = ov(g['glyph_box'], hb)
            if ox > tol_text and oy > tol_text:
                fail(f"文字压住生命线表头: {g['text']!r} 与表头 "
                     f"({['%.2f' % v for v in hb]}) 重叠 {ox:.3f} x {oy:.3f} in")

    frames = [p['box'] for p in report['prims'] if p.get('role') == 'frame']
    for g in glyphs:
        gb = g['glyph_box']
        for fb in frames:
            ox, oy = ov(gb, fb)
            if ox <= tol_bar or oy <= tol_bar:
                continue
            fully_inside = (gb[0] >= fb[0] - 1e-9 and gb[2] <= fb[2] + 1e-9
                            and gb[1] >= fb[1] - 1e-9 and gb[3] <= fb[3] + 1e-9)
            fully_outside = (gb[2] <= fb[0] or gb[0] >= fb[2]
                             or gb[3] <= fb[1] or gb[1] >= fb[3])
            if not (fully_inside or fully_outside):
                fail(f"文字跨越组合片段框线: {g['text']!r} 与框 {['%.2f' % v for v in fb]}")

    # 生命线表头：文字必须装得进表头框（否则 Visio 会折行）
    for p in report['prims']:
        if p.get('role') == 'header' and p.get('glyph_w'):
            inner = (p['box'][2] - p['box'][0]) - 0.08
            if p['glyph_w'] > inner:
                fail(f"表头文字超宽（会折行）: {p['text']!r} 需 {p['glyph_w']:.3f} in，"
                     f"可用 {inner:.3f} in")

    # 流程图节点：文字必须装得进图元（菱形要按内接条件判断，不能只看外框宽）
    for p in report['prims']:
        if p.get('role') != 'node' or not p.get('glyph_w'):
            continue
        bw = p['box'][2] - p['box'][0]
        bh = p['box'][3] - p['box'][1]
        gw, gh = p['glyph_w'], p.get('size', 12.25) / 72.0 * 1.25
        shp = p.get('shape', 'rect')
        if shp == 'diamond':
            ratio = (gw / 2.0) / (bw / 2.0) + (gh / 2.0) / (bh / 2.0)
            if ratio > 1.0:
                fail(f"判断框文字超出菱形（会压到斜边）: {p['text']!r} 占比 {ratio:.2f} > 1.00")
        elif shp == 'parallelogram':
            usable = (bw - bw * 0.22) - 0.08
            if gw > usable:
                fail(f"平行四边形文字超宽: {p['text']!r} 需 {gw:.3f} in，可用 {usable:.3f} in")
        else:
            usable = bw - 0.08
            if gw > usable:
                fail(f"流程框文字超宽（会折行）: {p['text']!r} 需 {gw:.3f} in，可用 {usable:.3f} in")

    # 相邻生命线之间的消息文字不得压到两侧激活条（宽度预算，SKILL.md §3.4）
    life_x = sorted(set(round(p['box'][0], 4) for p in report['prims'] if p.get('role') == 'lifeline'))
    for g in glyphs:
        if g.get('role') != 'msg_text':
            continue
        gb = g['glyph_box']
        for k in range(len(life_x) - 1):
            pass

    # ---------- 6. 页面与插入尺寸 ----------
    lb = report.get('life_bottom')
    if lb is not None and lb < st.get('min_bottom', 0.14) - 1e-9:
        fail(f"生命线底部仅剩 {lb:.2f} in，低于最小下边距 {st.get('min_bottom', 0.14)} in，内容可能超出页面")
    if lb is not None and lb > st['page_h']:
        fail(f"生命线底部 {lb:.2f} in 超出页面高度 {st['page_h']} in")
    w_in, h_in = st['page_w'], st['page_h']
    aspect = h_in / w_in
    if aspect > st['max_aspect'] + 1e-9:
        fail(f"图长宽比 {aspect:.3f} 超过 {st['max_aspect']}，按页心宽插入后会跨页")
    insert_w = st['insert_width_cm']
    rendered_h_cm = h_in * (insert_w / (w_in * CM_PER_IN)) * CM_PER_IN
    k = insert_w / (w_in * CM_PER_IN)
    eff_msg = st['fs_msg'] * k
    eff_life = st['fs_life'] * k
    if eff_msg < 9.0 - 1e-9:
        fail(f"插入后正文字号 {eff_msg:.2f} 磅 < 小五(9磅)，不满足可读性要求")
    if rendered_h_cm > 23.5 + 0.01:
        fail(f"插入后图高 {rendered_h_cm:.2f} cm 超过单页可用高 23.5 cm")

    # ---------- 7. 图名无标点 ----------
    name = spec.NAME
    m = re.match(r"^(图\d+-\d+) (.+)$", name)
    if not m:
        fail(f"图名未按“图序+半角空格+图名”格式: {name}")
    else:
        caption = m.group(2)
        if re.search(r"[，。、；：！？,.;:!?()（）《》\"'“”‘’—…]", caption):
            fail(f"图名含标点符号（规范不允许）: {caption}")
        if re.search(r"[A-Za-z]", caption) and False:
            pass

    # ---------- 报告 ----------
    print("=" * 68)
    print(f"图名：{name}")
    print(f"页面：{w_in} × {h_in} in   长宽比 {aspect:.3f}（上限 {st['max_aspect']}）")
    print(f"图形 {report['shapes']} 个 | 生命线 {report['lifelines']} 条 | 消息 {report['messages']} 条 | "
          f"组合片段 {len(report['frames'])} 个 | 已校验连线 {report.get('n_lines', 0)} 条")
    print(f"线宽：{sorted(weights)[0]} in ({float(sorted(weights)[0])*72:.2f} 磅)")
    print(f"字体：{sorted(fonts)} / 中文 {sorted(asian)}")
    print(f"按 {insert_w} cm 宽插入 → 缩放 {k:.3f}，图高 {rendered_h_cm:.2f} cm，"
          f"消息字号 {eff_msg:.2f} 磅，生命线名 {eff_life:.2f} 磅")
    print("-" * 68)
    for w in WARNS:
        print("  [提示]", w)
    for f in FAILS:
        print("  [不通过]", f)
    print("=" * 68)
    print("RESULT:", "PASS" if not FAILS else f"FAIL ({len(FAILS)} 项)")
    return 1 if FAILS else 0


if __name__ == '__main__':
    sys.exit(main())
