# -*- coding: utf-8 -*-
"""
图4-1 用户认证与个人中心模块时序图 —— 图定义

UML 画法（本次修订）：
  * 参与者「用户」画小人图形，其余生命线画对象框；
  * 消息按发送顺序编号（1:、2: …）；
  * 对象生命线上画激活条，表示该对象在该时段内正在执行；
  * 消息文字全部用中文短句，不放接口路径和英文类名。

技术依据：论文第4章 4.1 节
    POST /api/auth/login      查账号 → 比对密码散列 → 签发令牌
    GET  /api/user/info       携带令牌取个人资料（AuthInterceptor 校验令牌与角色）
    数据落库：sys_user（用户表）

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
"""

NAME = "图4-1 用户认证与个人中心模块时序图"

LIFELINES = [
    ('user', '用户',     '新就业群体用户', 0.95),
    ('ui',   '前端页面', 'Vue',           3.10),
    ('srv',  '后端服务', 'SpringBoot',    5.25),
    ('db',   'MySQL数据库', 'MySQL',       7.35),
]

ACTORS = ['user']
NUMBER_MSGS = True
AUTO_FIT_PAGE = True

# (源, 目标, 文字, 类型)  类型：sync 同步消息 / ret 返回消息 / self 自调用
MSGS = [
    # —— 登录认证 ——
    ('user', 'ui',  '输入账号密码并登录', 'sync'),
    ('ui',   'srv', '提交登录请求',       'sync'),
    ('srv',  'db',  '按用户名查询账号',   'sync'),
    ('db',   'srv', '返回账号与密码散列', 'ret'),
    ('srv',  'srv', '比对并签发',         'self'),
    ('srv',  'ui',  '返回登录令牌与角色', 'ret'),
    ('ui',   'ui',  '保存令牌',           'self'),
    ('ui',   'user', '按角色跳转对应页面', 'ret'),
    # —— 个人中心 ——
    ('user', 'ui',  '打开个人中心',       'sync'),
    ('ui',   'srv', '携带令牌请求资料',   'sync'),
    ('srv',  'srv', '校验令牌',           'self'),
    ('srv',  'db',  '查询用户资料',       'sync'),
    ('db',   'srv', '返回资料数据',       'ret'),
    ('srv',  'ui',  '返回个人资料',       'ret'),
    ('ui',   'user', '展示个人资料',       'ret'),
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
