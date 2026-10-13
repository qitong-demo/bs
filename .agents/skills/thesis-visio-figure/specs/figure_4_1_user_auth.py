# -*- coding: utf-8 -*-
"""
图4-1 用户认证与个人中心模块时序图（简化版）—— 图定义

简化原则（用户要求“小模块能简则简”）：
  * 生命线只保留 4 条：用户 / 前端页面 / 后端服务 / 数据库（与第1章技术介绍一致）；
  * 消息只保留登录、个人中心两条主流程共 12 条，全部为正常流程；
  * 不用组合片段（alt/else）、不用激活条、不用自调用，异常分支交由正文描述；
  * 消息文字全部用中文短句，不放接口路径和英文类名。

规范依据：.agents/skills/thesis-visio-figure/SKILL.md
技术依据：论文第4章 4.1 节
    POST /api/auth/login      查账号 → 比对密码散列 → 签发令牌
    GET  /api/user/info       携带令牌取个人资料（AuthInterceptor 校验令牌与角色）
    数据落库：sys_user（用户表）

版式：页面 8.27 × 6.60 英寸，按 15.5 cm 宽插入后图高约 12.4 cm，正好半页；字号线间距 2.15 英寸，
      相邻生命线之间的消息文字可放到 12 个汉字仍不压线。
"""

NAME = "图4-1 用户认证与个人中心模块时序图"

LIFELINES = [
    ('user', '用户',     '新就业群体用户', 0.95),
    ('ui',   '前端页面', 'Vue',           3.10),
    ('srv',  '后端服务', 'SpringBoot',    5.25),
    ('db',   '数据库',   'MySQL',         7.35),
]

# (源, 目标, 文字, 类型)  类型：sync 同步消息 / ret 返回消息 / self 自调用
MSGS = [
    # —— 登录认证 ——
    ('user', 'ui',  '输入账号密码并登录', 'sync'),
    ('ui',   'srv', '提交登录请求',       'sync'),
    ('srv',  'db',  '按用户名查询账号',   'sync'),
    ('db',   'srv', '返回账号与密码散列', 'ret'),
    ('srv',  'ui',  '返回登录令牌与角色', 'ret'),
    ('ui',   'user', '按角色跳转对应页面', 'ret'),
    # —— 个人中心 ——
    ('user', 'ui',  '打开个人中心',       'sync'),
    ('ui',   'srv', '携带令牌请求资料',   'sync'),
    ('srv',  'db',  '查询用户资料',       'sync'),
    ('db',   'srv', '返回资料数据',       'ret'),
    ('srv',  'ui',  '返回个人资料',       'ret'),
    ('ui',   'user', '展示个人资料',       'ret'),
]

FRAMES = []
BARS = []

STYLE = dict(
    page_w=8.27, page_h=6.60,
    fs_msg=12.25, fs_life=12.25, fs_sub=8.0,
    box_w=1.55, box_h=0.70,
    box_top_margin=0.28,
    dy_msg=0.42,
    life_bottom_pad=0.10,
    insert_width_cm=15.5,
)
