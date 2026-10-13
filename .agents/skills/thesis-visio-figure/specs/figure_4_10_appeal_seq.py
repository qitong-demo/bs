# -*- coding: utf-8 -*-
"""图4-10 诉求反馈与响应模块时序图 —— 图定义（由 tmp/gen_specs.py 批量生成）

简化原则（延续图4-1）：生命线 5 条、消息 15 条；不用组合片段 alt/else、
不用激活条、不用自调用；消息文字全部为中文短句；异常分支交由正文描述。

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
技术依据：论文第4章 4.4 节
"""

NAME = "图4-10 诉求反馈与响应模块时序图"

LIFELINES = [
    ('usr', '普通用户', '新就业群体用户', 0.9),
    ('adm', '驿站管理员', 'StationAdmin', 2.52),
    ('ui', '前端页面', 'Vue', 4.14),
    ('srv', '后端服务', 'SpringBoot', 5.76),
    ('db', '数据库', 'MySQL', 7.38),
]

MSGS = [
    ('usr', 'ui', '填写诉求并提交', 'sync'),
    ('ui', 'srv', '提交诉求信息', 'sync'),
    ('srv', 'db', '保存诉求待受理', 'sync'),
    ('db', 'srv', '返回诉求编号', 'ret'),
    ('srv', 'ui', '返回提交结果', 'ret'),
    ('ui', 'usr', '提示提交成功', 'ret'),
    ('adm', 'ui', '查看待处理诉求', 'sync'),
    ('ui', 'srv', '请求待处理诉求', 'sync'),
    ('srv', 'ui', '返回诉求列表', 'ret'),
    ('ui', 'adm', '展示诉求列表', 'ret'),
    ('adm', 'ui', '提交处理并办结', 'sync'),
    ('ui', 'srv', '提交处理结果', 'sync'),
    ('srv', 'db', '更新状态并写记录', 'sync'),
    ('srv', 'ui', '返回办理结果', 'ret'),
    ('ui', 'adm', '提示办理完成', 'ret'),
]

FRAMES = []
BARS = []

STYLE = dict(
    page_w=8.27, page_h=7.2,
    fs_msg=12.25, fs_life=12.25, fs_sub=8.0,
    box_w=1.5, box_h=0.70,
    box_top_margin=0.28,
    dy_msg=0.38,
    life_bottom_pad=0.10,
    insert_width_cm=15.5,
)
