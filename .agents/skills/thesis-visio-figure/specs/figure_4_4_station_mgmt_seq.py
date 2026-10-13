# -*- coding: utf-8 -*-
"""图4-4 友好驿站管理模块时序图 —— 图定义（由 tmp/gen_specs.py 批量生成）

简化原则（延续图4-1）：生命线 4 条、消息 12 条；不用组合片段 alt/else、
不用激活条、不用自调用；消息文字全部为中文短句；异常分支交由正文描述。

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
技术依据：论文第4章 4.2 节
"""

NAME = "图4-4 友好驿站管理模块时序图"

LIFELINES = [
    ('adm', '驿站管理员', 'StationAdmin', 0.95),
    ('ui', '前端页面', 'Vue', 3.1),
    ('srv', '后端服务', 'SpringBoot', 5.25),
    ('db', '数据库', 'MySQL', 7.35),
]

MSGS = [
    ('adm', 'ui', '进入驿站管理页面', 'sync'),
    ('ui', 'srv', '请求驿站列表', 'sync'),
    ('srv', 'db', '按管理员查询驿站', 'sync'),
    ('db', 'srv', '返回驿站记录', 'ret'),
    ('srv', 'ui', '返回驿站列表', 'ret'),
    ('ui', 'adm', '展示驿站列表', 'ret'),
    ('adm', 'ui', '新增或修改驿站信息', 'sync'),
    ('ui', 'srv', '提交驿站信息', 'sync'),
    ('srv', 'db', '校验并保存驿站信息', 'sync'),
    ('db', 'srv', '返回保存结果', 'ret'),
    ('srv', 'ui', '返回保存结果', 'ret'),
    ('ui', 'adm', '刷新列表并提示成功', 'ret'),
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
