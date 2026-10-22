# -*- coding: utf-8 -*-
"""图4-19 后台统计分析模块时序图 —— 图定义

UML 画法：参与者「平台管理员」画小人图形；消息按发送顺序编号；对象生命线画激活条；
消息文字全部为中文短句。

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
技术依据：论文第4章 4.7 节（数据统计看板、服务数据分析）
"""

NAME = "图4-19 后台统计分析模块时序图"

LIFELINES = [
    ('adm', '平台管理员', 'PlatformAdmin', 0.95),
    ('ui', '前端页面', 'Vue', 3.1),
    ('srv', '后端服务', 'SpringBoot', 5.25),
    ('db', 'MySQL数据库', 'MySQL', 7.35),
]

ACTORS = ['adm']
NUMBER_MSGS = True
AUTO_FIT_PAGE = True

MSGS = [
    ('adm', 'ui', '进入数据统计页面', 'sync'),
    ('ui', 'srv', '请求统计概览', 'sync'),
    ('srv', 'srv', '确定范围', 'self'),
    ('srv', 'db', '聚合查询业务数据', 'sync'),
    ('db', 'srv', '返回汇总数据', 'ret'),
    ('srv', 'ui', '返回统计指标', 'ret'),
    ('ui', 'adm', '展示指标卡片', 'ret'),
    ('adm', 'ui', '查看服务使用排行', 'sync'),
    ('ui', 'srv', '请求服务排行', 'sync'),
    ('srv', 'srv', '汇总人次', 'self'),
    ('srv', 'db', '按服务类别分组统计', 'sync'),
    ('db', 'srv', '返回分组结果', 'ret'),
    ('srv', 'ui', '返回排行数据', 'ret'),
    ('ui', 'adm', '展示服务使用排行', 'ret'),
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
