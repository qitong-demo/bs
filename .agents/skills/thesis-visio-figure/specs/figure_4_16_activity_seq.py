# -*- coding: utf-8 -*-
"""图4-16 活动报名与服务评价模块时序图 —— 图定义

UML 画法：参与者「普通用户」画小人图形；消息按发送顺序编号；对象生命线画激活条；
消息文字全部为中文短句。

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
技术依据：论文第4章 4.6 节（活动发布与报名、驿站服务评价）
"""

NAME = "图4-16 活动报名与服务评价模块时序图"

LIFELINES = [
    ('usr', '普通用户', '新就业群体', 0.95),
    ('ui', '前端页面', 'Vue', 3.1),
    ('srv', '后端服务', 'SpringBoot', 5.25),
    ('db', 'MySQL数据库', 'MySQL', 7.35),
]

ACTORS = ['usr']
NUMBER_MSGS = True
AUTO_FIT_PAGE = True

MSGS = [
    ('usr', 'ui', '选择活动并提交报名', 'sync'),
    ('ui', 'srv', '提交报名申请', 'sync'),
    ('srv', 'srv', '校验名额', 'self'),
    ('srv', 'db', '写入报名记录', 'sync'),
    ('db', 'srv', '返回报名结果', 'ret'),
    ('srv', 'ui', '返回报名结果', 'ret'),
    ('ui', 'usr', '提示报名成功', 'ret'),
    ('usr', 'ui', '填写驿站服务评价', 'sync'),
    ('ui', 'srv', '提交评分与评价', 'sync'),
    ('srv', 'srv', '校验评分', 'self'),
    ('srv', 'db', '写入驿站评价记录', 'sync'),
    ('db', 'srv', '返回保存结果', 'ret'),
    ('srv', 'ui', '返回保存结果', 'ret'),
    ('ui', 'usr', '提示评价成功', 'ret'),
]

FRAMES = []

# 激活条由 build_sequence_uml_vsdx 按每个参与者的首末消息自动生成，此表不再手工维护。
BARS = []

STYLE = dict(
    page_w=8.27,
    fs_msg=12.25, fs_life=12.25, fs_sub=8.0,
    box_w=1.55, box_h=0.70,
    box_top_margin=0.28,
    dy_msg=0.42,
    life_bottom_pad=0.10,
    bottom_margin=0.22,
    insert_width_cm=15.5,
)

