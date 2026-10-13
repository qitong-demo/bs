# -*- coding: utf-8 -*-
"""图4-19 后台统计分析模块时序图 —— 图定义（由 tmp/gen_specs.py 批量生成）

简化原则（延续图4-1）：生命线 4 条、消息 12 条；不用组合片段 alt/else、
不用激活条、不用自调用；消息文字全部为中文短句；异常分支交由正文描述。

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
技术依据：论文第4章 4.7 节
"""

NAME = "图4-19 后台统计分析模块时序图"

LIFELINES = [
    ('adm', '平台管理员', 'PlatformAdmin', 0.95),
    ('ui', '前端页面', 'Vue', 3.1),
    ('srv', '后端服务', 'SpringBoot', 5.25),
    ('db', '数据库', 'MySQL', 7.35),
]

MSGS = [
    ('adm', 'ui', '进入数据统计页面', 'sync'),
    ('ui', 'srv', '请求统计概览', 'sync'),
    ('srv', 'db', '按角色范围聚合统计', 'sync'),
    ('db', 'srv', '返回汇总数据', 'ret'),
    ('srv', 'ui', '返回统计指标', 'ret'),
    ('ui', 'adm', '展示指标卡片', 'ret'),
    ('adm', 'ui', '查看服务使用排行', 'sync'),
    ('ui', 'srv', '请求服务排行', 'sync'),
    ('srv', 'db', '按服务类别分组统计', 'sync'),
    ('db', 'srv', '返回分组结果', 'ret'),
    ('srv', 'ui', '返回排行数据', 'ret'),
    ('ui', 'adm', '展示服务使用排行', 'ret'),
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
