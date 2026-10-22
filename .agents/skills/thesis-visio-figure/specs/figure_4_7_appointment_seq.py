# -*- coding: utf-8 -*-
"""图4-7 驿站服务预约模块时序图 —— 图定义

UML 画法：参与者「普通用户」画小人图形；消息按发送顺序编号；对象生命线画激活条；
消息文字全部为中文短句。

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
技术依据：论文第4章 4.3 节（服务预约申请、预约审核与处理、我的预约记录）
"""

NAME = "图4-7 驿站服务预约模块时序图"

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
    ('usr', 'ui', '填写预约信息', 'sync'),
    ('ui', 'srv', '提交预约申请', 'sync'),
    ('srv', 'srv', '校验驿站与时段', 'self'),
    ('srv', 'db', '保存预约记录', 'sync'),
    ('db', 'srv', '返回预约编号', 'ret'),
    ('srv', 'ui', '返回提交结果', 'ret'),
    ('ui', 'usr', '提示预约已提交', 'ret'),
    ('usr', 'ui', '查看我的预约', 'sync'),
    ('ui', 'srv', '查询本人预约记录', 'sync'),
    ('srv', 'srv', '限定查询', 'self'),
    ('srv', 'db', '按用户查询预约记录', 'sync'),
    ('db', 'srv', '返回预约及状态', 'ret'),
    ('srv', 'ui', '返回预约记录', 'ret'),
    ('ui', 'usr', '展示状态与审核意见', 'ret'),
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

