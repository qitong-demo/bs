# -*- coding: utf-8 -*-
"""图4-7 驿站服务预约模块时序图 —— 图定义（由 tmp/gen_specs.py 批量生成）

简化原则（延续图4-1）：生命线 4 条、消息 12 条；不用组合片段 alt/else、
不用激活条、不用自调用；消息文字全部为中文短句；异常分支交由正文描述。

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
技术依据：论文第4章 4.3 节
"""

NAME = "图4-7 驿站服务预约模块时序图"

LIFELINES = [
    ('usr', '普通用户', '新就业群体用户', 0.95),
    ('ui', '前端页面', 'Vue', 3.1),
    ('srv', '后端服务', 'SpringBoot', 5.25),
    ('db', '数据库', 'MySQL', 7.35),
]

MSGS = [
    ('usr', 'ui', '填写预约信息', 'sync'),
    ('ui', 'srv', '提交预约申请', 'sync'),
    ('srv', 'db', '校验服务并保存预约', 'sync'),
    ('db', 'srv', '返回预约编号', 'ret'),
    ('srv', 'ui', '返回提交结果', 'ret'),
    ('ui', 'usr', '提示预约已提交', 'ret'),
    ('usr', 'ui', '查看我的预约', 'sync'),
    ('ui', 'srv', '查询本人预约记录', 'sync'),
    ('srv', 'db', '按用户查询预约记录', 'sync'),
    ('db', 'srv', '返回预约及状态', 'ret'),
    ('srv', 'ui', '返回预约记录', 'ret'),
    ('ui', 'usr', '展示状态与审核意见', 'ret'),
]

FRAMES = []
BARS = []

STYLE = dict(
    page_w=8.27, page_h=6.6,
    fs_msg=12.25, fs_life=12.25, fs_sub=8.0,
    box_w=1.55, box_h=0.70,
    box_top_margin=0.28,
    dy_msg=0.42,
    life_bottom_pad=0.10,
    insert_width_cm=15.5,
)
