# -*- coding: utf-8 -*-
"""图4-10 诉求反馈与响应模块时序图 —— 图定义

UML 画法：参与者「普通用户」「驿站管理员」都画小人图形；消息按发送顺序编号；
对象生命线画激活条；消息文字全部为中文短句。

为在 1.62 英寸的生命线间距内放下带序号的消息文字，第 13 条消息
由「更新状态并写记录」压缩为「更新状态并记录」（同义），其余文字不变。

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
技术依据：论文第4章 4.4 节（诉求提交、诉求分派与处理、诉求处理回复、诉求评价）
"""

NAME = "图4-10 诉求反馈与响应模块时序图"

LIFELINES = [
    ('usr', '普通用户', '新就业群体', 0.9),
    ('adm', '驿站管理员', 'StationAdmin', 2.52),
    ('ui', '前端页面', 'Vue', 4.14),
    ('srv', '后端服务', 'SpringBoot', 5.76),
    ('db', 'MySQL数据库', 'MySQL', 7.38),
]

ACTORS = ['usr', 'adm']
NUMBER_MSGS = True
AUTO_FIT_PAGE = True

MSGS = [
    ('usr', 'ui', '填写诉求并提交', 'sync'),
    ('ui', 'srv', '提交诉求信息', 'sync'),
    ('srv', 'srv', '校验必填项', 'self'),
    ('srv', 'db', '保存诉求待受理', 'sync'),
    ('db', 'srv', '返回诉求编号', 'ret'),
    ('srv', 'ui', '返回提交结果', 'ret'),
    ('ui', 'usr', '提示提交成功', 'ret'),
    ('adm', 'ui', '查看待处理诉求', 'sync'),
    ('ui', 'srv', '请求待处理诉求', 'sync'),
    ('srv', 'srv', '按范围过滤', 'self'),
    ('srv', 'ui', '返回诉求列表', 'ret'),
    ('ui', 'adm', '展示诉求列表', 'ret'),
    ('adm', 'ui', '分派处理人', 'sync'),
    ('ui', 'srv', '提交分派结果', 'sync'),
    ('srv', 'db', '更新状态并记录', 'sync'),
    ('db', 'srv', '返回分派结果', 'ret'),
    ('adm', 'ui', '处理并办结', 'sync'),
    ('ui', 'srv', '提交处理结果', 'sync'),
    ('srv', 'db', '更新并记录', 'sync'),
    ('db', 'srv', '返回办理结果', 'ret'),
    ('srv', 'ui', '返回办理结果', 'ret'),
    ('ui', 'adm', '提示办理完成', 'ret'),
    ('usr', 'ui', '提交满意度评价', 'sync'),
    ('ui', 'srv', '保存评价状态不变', 'sync'),
    ('srv', 'ui', '返回评价结果', 'ret'),
]

FRAMES = []

# 激活条由 build_sequence_uml_vsdx 按每个参与者的首末消息自动生成，此表不再手工维护。
BARS = []

STYLE = dict(
    page_w=8.27,
    fs_msg=12.25, fs_life=12.25, fs_sub=8.0,
    box_w=1.5, box_h=0.70,
    box_top_margin=0.28,
    dy_msg=0.34,
    life_bottom_pad=0.10,
    bottom_margin=0.22,
    insert_width_cm=15.5,
)

