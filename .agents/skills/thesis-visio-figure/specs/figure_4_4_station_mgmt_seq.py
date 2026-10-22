# -*- coding: utf-8 -*-
"""图4-4 友好驿站管理模块时序图 —— 图定义

UML 画法：参与者「驿站管理员」画小人图形；消息按发送顺序编号；对象生命线画激活条；
消息文字全部为中文短句。

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
技术依据：论文第4章 4.2 节（驿站信息管理、驿站服务配置、附近驿站查询、驿站详情查看）
"""

NAME = "图4-4 友好驿站管理模块时序图"

LIFELINES = [
    ('adm', '驿站管理员', 'StationAdmin', 0.95),
    ('ui', '前端页面', 'Vue', 3.1),
    ('srv', '后端服务', 'SpringBoot', 5.25),
    ('db', 'MySQL数据库', 'MySQL', 7.35),
]

ACTORS = ['adm']
NUMBER_MSGS = True
AUTO_FIT_PAGE = True

MSGS = [
    ('adm', 'ui', '进入驿站管理页面', 'sync'),
    ('ui', 'srv', '请求驿站列表', 'sync'),
    ('srv', 'srv', '限定范围', 'self'),
    ('srv', 'db', '按管理员查询驿站', 'sync'),
    ('db', 'srv', '返回驿站记录', 'ret'),
    ('srv', 'ui', '返回驿站列表', 'ret'),
    ('ui', 'adm', '展示驿站列表', 'ret'),
    ('adm', 'ui', '新增或修改驿站信息', 'sync'),
    ('ui', 'srv', '提交驿站信息', 'sync'),
    ('srv', 'srv', '校验必填项', 'self'),
    ('srv', 'db', '保存驿站信息', 'sync'),
    ('db', 'srv', '返回保存结果', 'ret'),
    ('srv', 'ui', '返回保存结果', 'ret'),
    ('ui', 'ui', '刷新列表', 'self'),
    ('ui', 'adm', '提示保存成功', 'ret'),
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
