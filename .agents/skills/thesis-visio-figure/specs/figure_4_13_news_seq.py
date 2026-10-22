# -*- coding: utf-8 -*-
"""图4-13 政策资讯与通知公告模块时序图 —— 图定义

UML 画法：参与者「平台管理员」「普通用户」都画小人图形；消息按发送顺序编号；
对象生命线画激活条；消息文字全部为中文短句。

为在 1.62 英寸的生命线间距内放下带序号的消息文字，第 3 条消息
由「写入资讯并置草稿」压缩为「资讯写入为草稿」（同义），其余文字不变。

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
技术依据：论文第4章 4.5 节（政策资讯发布、通知公告管理、资讯公告查看）
"""

NAME = "图4-13 政策资讯与通知公告模块时序图"

LIFELINES = [
    ('adm', '平台管理员', 'PlatformAdmin', 0.9),
    ('usr', '普通用户', '新就业群体', 2.52),
    ('ui', '前端页面', 'Vue', 4.14),
    ('srv', '后端服务', 'SpringBoot', 5.76),
    ('db', 'MySQL数据库', 'MySQL', 7.38),
]

ACTORS = ['adm', 'usr']
NUMBER_MSGS = True
AUTO_FIT_PAGE = True

MSGS = [
    ('adm', 'ui', '编辑并保存资讯', 'sync'),
    ('ui', 'srv', '提交资讯内容', 'sync'),
    ('srv', 'srv', '校验正文', 'self'),
    ('srv', 'db', '保存资讯草稿', 'sync'),
    ('db', 'srv', '返回草稿编号', 'ret'),
    ('srv', 'ui', '返回保存结果', 'ret'),
    ('ui', 'adm', '提示保存成功', 'ret'),
    ('adm', 'ui', '确认发布资讯', 'sync'),
    ('ui', 'srv', '提交发布请求', 'sync'),
    ('srv', 'srv', '校验发布', 'self'),
    ('srv', 'db', '更新为已发布', 'sync'),
    ('db', 'srv', '返回更新结果', 'ret'),
    ('srv', 'ui', '返回发布结果', 'ret'),
    ('ui', 'adm', '提示发布成功', 'ret'),
    ('usr', 'ui', '分类浏览资讯', 'sync'),
    ('ui', 'srv', '请求资讯列表', 'sync'),
    ('srv', 'db', '查询发布资讯', 'sync'),
    ('db', 'srv', '返回资讯列表', 'ret'),
    ('srv', 'ui', '返回资讯公告', 'ret'),
    ('ui', 'usr', '展示资讯列表', 'ret'),
]

FRAMES = []

# 激活条由 build_sequence_uml_vsdx 按每个参与者的首末消息自动生成，此表不再手工维护。
BARS = []

STYLE = dict(
    page_w=8.27,
    fs_msg=12.25, fs_life=12.25, fs_sub=8.0,
    box_w=1.5, box_h=0.70,
    box_top_margin=0.28,
    dy_msg=0.37,
    life_bottom_pad=0.10,
    bottom_margin=0.22,
    insert_width_cm=15.5,
)

