USE friendly_station;

-- 演示账号数据（mysql profile）。两个账号的密码均为 password。
-- 说明：驿站数据不在此文件，由 stations.sql 导入（来源见 docs/驿站数据来源.md）。
-- 导入顺序：schema.sql -> stations.sql -> data.sql，因为活动记录依赖驿站编号。
INSERT IGNORE INTO sys_user
    (id, username, password, real_name, phone, user_type, job_type, area, status)
VALUES
    (1, 'rider_xiaowang', '$2b$10$nD.EljE4aLGhfnn0Tga/E.7bmMhm3nlJCLbruT2Ojneea4WmwYSie', '骑手小王', '13800002088', 'USER', '外卖骑手', '上城区', 1),
    (2, 'station_admin_01', '$2b$10$nD.EljE4aLGhfnn0Tga/E.7bmMhm3nlJCLbruT2Ojneea4WmwYSie', '陈管理员', '13800002089', 'STATION_ADMIN', '驿站管理员', '拱墅区', 1),
    (3, 'platform_admin', '$2b$10$nD.EljE4aLGhfnn0Tga/E.7bmMhm3nlJCLbruT2Ojneea4WmwYSie', '平台管理员', '13800002090', 'PLATFORM_ADMIN', '平台管理员', '杭州市', 1);

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT 1, id FROM sys_role WHERE role_code = 'USER';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT 2, id FROM sys_role WHERE role_code = 'STATION_ADMIN';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT 3, id FROM sys_role WHERE role_code = 'PLATFORM_ADMIN';

-- 演示活动：挂在 1、2 号「小哥驿站」下（编号与名称来自 stations.sql 中的真实驿站）。
INSERT IGNORE INTO activity (id, title, station_id, activity_time, address, max_count, content, status)
VALUES
    (1, '夏季送清凉公益活动', 1, '2026-06-15 09:00:00', '上城区四季青街道顺福路 137 号（钱江苑小哥驿站）', 100, '为新就业群体提供防暑物资和健康咨询。', 'PUBLISHED'),
    (2, '新就业群体法律咨询专场', 2, '2026-06-20 14:00:00', '拱墅区祥符街道莫干山路与月亮路交叉口（大悦城小哥驿站）', 50, '提供劳动权益和职业保障方面的公益咨询。', 'PUBLISHED');
