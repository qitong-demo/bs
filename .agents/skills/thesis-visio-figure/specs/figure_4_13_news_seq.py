# -*- coding: utf-8 -*-
"""图4-13 政策资讯与通知公告模块时序图 —— 图定义（由 tmp/gen_specs.py 批量生成）

简化原则（延续图4-1）：生命线 5 条、消息 16 条；不用组合片段 alt/else、
不用激活条、不用自调用；消息文字全部为中文短句；异常分支交由正文描述。

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
技术依据：论文第4章 4.5 节
"""

NAME = "图4-13 政策资讯与通知公告模块时序图"

LIFELINES = [
    ('adm', '平台管理员', 'PlatformAdmin', 0.9),
    ('usr', '普通用户', '新就业群体用户', 2.52),
    ('ui', '前端页面', 'Vue', 4.14),
    ('srv', '后端服务', 'SpringBoot', 5.76),
    ('db', '数据库', 'MySQL', 7.38),
]

MSGS = [
    ('adm', 'ui', '编辑并保存资讯', 'sync'),
    ('ui', 'srv', '提交资讯内容', 'sync'),
    ('srv', 'db', '写入资讯并置草稿', 'sync'),
    ('srv', 'ui', '返回保存结果', 'ret'),
    ('ui', 'adm', '提示保存成功', 'ret'),
    ('adm', 'ui', '确认发布资讯', 'sync'),
    ('ui', 'srv', '提交发布请求', 'sync'),
    ('srv', 'db', '更新为已发布', 'sync'),
    ('srv', 'ui', '返回发布结果', 'ret'),
    ('ui', 'adm', '提示发布成功', 'ret'),
    ('usr', 'ui', '按分类浏览资讯', 'sync'),
    ('ui', 'srv', '请求资讯列表', 'sync'),
    ('srv', 'db', '查询已发布资讯', 'sync'),
    ('db', 'srv', '返回资讯列表', 'ret'),
    ('srv', 'ui', '返回资讯与公告', 'ret'),
    ('ui', 'usr', '展示资讯列表', 'ret'),
]

FRAMES = []
BARS = []

STYLE = dict(
    page_w=8.27, page_h=7.4,
    fs_msg=12.25, fs_life=12.25, fs_sub=8.0,
    box_w=1.5, box_h=0.70,
    box_top_margin=0.28,
    dy_msg=0.37,
    life_bottom_pad=0.10,
    insert_width_cm=15.5,
)
