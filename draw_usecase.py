from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

OUT = Path(r"C:\Users\Lenovo\Desktop\bs")
W, H = 1500, 1000
img = Image.new("RGB", (W, H), "white")
d = ImageDraw.Draw(img)

font_path = r"C:\Windows\Fonts\simhei.ttf"
font = ImageFont.truetype(font_path, 28)
small = ImageFont.truetype(font_path, 25)
title_font = ImageFont.truetype(font_path, 32)

# System boundary
bx0, by0, bx1, by1 = 300, 55, 1235, 945
d.rectangle((bx0, by0, bx1, by1), outline="black", width=3)
d.text((bx0 + 20, by0 + 12), "新就业群体友好驿站服务与诉求响应平台", fill="black", font=title_font)

def actor(cx, cy, label, side="left"):
    # stick figure
    r = 15
    d.ellipse((cx-r, cy-r, cx+r, cy+r), outline="black", width=3)
    d.line((cx, cy+r, cx, cy+70), fill="black", width=3)
    d.line((cx-28, cy+38, cx+28, cy+38), fill="black", width=3)
    d.line((cx, cy+70, cx-25, cy+112), fill="black", width=3)
    d.line((cx, cy+70, cx+25, cy+112), fill="black", width=3)
    box = d.textbbox((0, 0), label, font=font)
    tw = box[2] - box[0]
    d.text((cx-tw/2, cy+125), label, fill="black", font=font)

def oval(cx, cy, text, width=270, height=72):
    d.ellipse((cx-width//2, cy-height//2, cx+width//2, cy+height//2),
              outline="black", width=3)
    box = d.textbbox((0, 0), text, font=font)
    tw, th = box[2]-box[0], box[3]-box[1]
    d.text((cx-tw/2, cy-th/2-3), text, fill="black", font=font)
    return (cx, cy)

def assoc(actor_pt, use_pt):
    d.line((actor_pt[0], actor_pt[1], use_pt[0], use_pt[1]), fill="black", width=2)

# Actors
user = (115, 430)
station = (1390, 265)
admin = (1390, 715)
actor(*user, "新就业群体用户")
actor(*station, "驿站管理员")
actor(*admin, "平台管理员")

# Use cases
cases = {}
cases["register"] = oval(500, 150, "用户注册与登录", 270)
cases["profile"] = oval(850, 150, "个人信息管理", 270)
cases["station"] = oval(500, 305, "驿站信息查询", 270)
cases["booking"] = oval(850, 305, "服务预约", 230)
cases["booking_manage"] = oval(1050, 470, "预约记录管理", 270)
cases["appeal"] = oval(515, 500, "诉求提交与查询", 290)
cases["response"] = oval(850, 620, "诉求响应处理", 270)
cases["activity"] = oval(500, 700, "活动报名", 230)
cases["activity_manage"] = oval(850, 800, "活动信息管理", 270)
cases["info"] = oval(510, 850, "政策资讯管理", 270)
cases["review"] = oval(1080, 670, "服务评价", 230)

# Associations: user
for key in ["register", "profile", "station", "booking", "appeal", "activity", "info", "review"]:
    assoc((user[0]+45, user[1]), cases[key])

# Station admin
for key in ["booking_manage", "response", "activity_manage", "info", "review"]:
    assoc((station[0]-45, station[1]), cases[key])

# Platform admin
for key in ["response", "activity_manage", "info", "review", "profile"]:
    assoc((admin[0]-45, admin[1]), cases[key])

# A small legend-like note inside boundary, matching academic UML figure style
d.text((bx0 + 25, by1 - 45), "图中椭圆表示系统用例，人物表示系统参与者，直线表示参与关系", fill="black", font=small)

png = OUT / "系统总用例图.png"
svg = OUT / "系统总用例图.svg"
img.save(png, dpi=(300, 300))

# Write a simple editable SVG with equivalent structure.
def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

svg_lines = [
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
    '<rect width="100%" height="100%" fill="white"/>',
    f'<rect x="{bx0}" y="{by0}" width="{bx1-bx0}" height="{by1-by0}" fill="white" stroke="black" stroke-width="3"/>',
    f'<text x="{bx0+20}" y="{by0+42}" font-family="SimHei, Microsoft YaHei" font-size="32">{esc("新就业群体友好驿站服务与诉求响应平台")}</text>',
]

def svg_actor(cx, cy, label):
    return [
        f'<circle cx="{cx}" cy="{cy}" r="15" fill="white" stroke="black" stroke-width="3"/>',
        f'<line x1="{cx}" y1="{cy+15}" x2="{cx}" y2="{cy+70}" stroke="black" stroke-width="3"/>',
        f'<line x1="{cx-28}" y1="{cy+38}" x2="{cx+28}" y2="{cy+38}" stroke="black" stroke-width="3"/>',
        f'<line x1="{cx}" y1="{cy+70}" x2="{cx-25}" y2="{cy+112}" stroke="black" stroke-width="3"/>',
        f'<line x1="{cx}" y1="{cy+70}" x2="{cx+25}" y2="{cy+112}" stroke="black" stroke-width="3"/>',
        f'<text x="{cx}" y="{cy+155}" text-anchor="middle" font-family="SimHei, Microsoft YaHei" font-size="28">{esc(label)}</text>',
    ]

def svg_oval(cx, cy, text, width=270, height=72):
    return [
        f'<ellipse cx="{cx}" cy="{cy}" rx="{width//2}" ry="{height//2}" fill="white" stroke="black" stroke-width="3"/>',
        f'<text x="{cx}" y="{cy+10}" text-anchor="middle" font-family="SimHei, Microsoft YaHei" font-size="28">{esc(text)}</text>',
    ]

for a in [svg_actor(*user, "新就业群体用户"), svg_actor(*station, "驿站管理员"), svg_actor(*admin, "平台管理员")]:
    svg_lines.extend(a)
for key, (cx, cy) in cases.items():
    txt = {"register":"用户注册与登录","profile":"个人信息管理","station":"驿站信息查询","booking":"服务预约",
           "booking_manage":"预约记录管理","appeal":"诉求提交与查询","response":"诉求响应处理",
           "activity":"活动报名","activity_manage":"活动信息管理","info":"政策资讯管理","review":"服务评价"}[key]
    widths = {"booking":230,"activity":230,"appeal":290,"review":230}
    svg_lines.extend(svg_oval(cx, cy, txt, widths.get(key,270)))
for a, keylist, side in [
    (user, ["register","profile","station","booking","appeal","activity","info","review"], "l"),
    (station, ["booking_manage","response","activity_manage","info","review"], "r"),
    (admin, ["response","activity_manage","info","review","profile"], "r"),
]:
    for key in keylist:
        cx, cy = cases[key]
        ax = a[0] + (45 if side == "l" else -45)
        svg_lines.append(f'<line x1="{ax}" y1="{a[1]}" x2="{cx}" y2="{cy}" stroke="black" stroke-width="2"/>')
svg_lines.append(f'<text x="{bx0+25}" y="{by1-45}" font-family="SimHei, Microsoft YaHei" font-size="25">图中椭圆表示系统用例，人物表示系统参与者，直线表示参与关系</text>')
svg_lines.append("</svg>")
svg.write_text("\n".join(svg_lines), encoding="utf-8")
print(png)
print(svg)
