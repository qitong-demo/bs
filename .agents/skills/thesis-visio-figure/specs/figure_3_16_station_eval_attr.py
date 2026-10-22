# -*- coding: utf-8 -*-
"""图3-16 驿站评价实体属性图 —— 图定义

字段严格取自 表3-14 驿站评价表(station_evaluation)：
evaluation_id(主键)、station_id、user_id、score、service_score、
environment_score、facility_score、content
画的是中文属性名（与全文其他属性图一致）。
"""

NAME = '图3-16 驿站评价实体属性图'

PAGE = (8.27, 5.45)

ENTITY = ('驿站评价', 4.14, 2.72)
ENT_W, ENT_H = 1.60, 0.52

ATTR_W, ATTR_H = 1.80, 0.44
FS = 12.25

# 左栏 4 个、右栏 4 个；第 4 项 True 表示主键（加下划线）
LX, RX = 1.92, 6.36
YS = [4.35, 3.25, 2.15, 1.05]

ATTRS = [
    ('评价ID',       LX, YS[0], True),
    ('被评驿站ID',   LX, YS[1], False),
    ('评价用户ID',   LX, YS[2], False),
    ('综合评分',     LX, YS[3], False),
    ('服务态度评分', RX, YS[0], False),
    ('环境卫生评分', RX, YS[1], False),
    ('设施完善度评分', RX, YS[2], False),
    ('评价内容',     RX, YS[3], False),
]
