CREATE DATABASE IF NOT EXISTS friendly_station
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE friendly_station;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    real_name VARCHAR(50),
    phone VARCHAR(20) NOT NULL UNIQUE,
    -- 邮箱：用于收发验证码（改账号 ID / 改密码 / 改联系方式时校验身份）。
    -- 允许为空；未绑定时敏感操作会被拒绝并提示先绑定。
    email VARCHAR(120),
    -- 凭据版本：改账号 ID 或密码时 +1，并写入新签发的 JWT。
    -- 拦截器比对令牌里的版本与当前值，不一致即 401——这样"改完强制重新登录"才是真的失效，
    -- 而不是只让前端清掉本地令牌、旧令牌却还能继续用满 120 分钟。
    credential_version INT NOT NULL DEFAULT 0,
    avatar VARCHAR(255),
    user_type VARCHAR(30) NOT NULL DEFAULT 'USER',
    job_type VARCHAR(50),
    area VARCHAR(50),
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(50) NOT NULL,
    remark VARCHAR(255),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 平台覆盖的城市。驿站、资讯、活动等业务数据都按城市归属，
-- center_lng / center_lat 是该城市的中心点，用于「定位后判断用户所在城市」与地图初始定位。
CREATE TABLE IF NOT EXISTS city (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    city_code VARCHAR(20) NOT NULL,
    city_name VARCHAR(50) NOT NULL,
    province VARCHAR(50),
    center_lng DECIMAL(10, 6),
    center_lat DECIMAL(10, 6),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_no INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_city_code (city_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS station (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    station_name VARCHAR(100) NOT NULL,
    city_code VARCHAR(20) NOT NULL DEFAULT '330100',
    area VARCHAR(50) NOT NULL,
    address VARCHAR(255) NOT NULL,
    longitude DECIMAL(10, 7),
    latitude DECIMAL(10, 7),
    contact_phone VARCHAR(20),
    open_time VARCHAR(100),
    image_url VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    manager_id BIGINT,
    -- 数据来源与核对信息：用于说明该条驿站记录的出处，便于复核。
    station_type VARCHAR(30),
    source_name VARCHAR(120),
    source_url VARCHAR(500),
    source_published_at DATE,
    verified_at DATE,
    data_status VARCHAR(20) NOT NULL DEFAULT 'DEMO',
    data_note VARCHAR(500),
    -- 坐标来源：公开来源一般不给坐标，坐标由高德地图地理编码派生，因此单独记录来源，
    -- 不参与 data_status 的判定（data_status 只表示“字段能否从公开来源逐条核对”）。
    -- AMAP_POI = 按驿站名称匹配到高德 POI；AMAP_GEOCODE = 按详细地址地理编码；NULL = 来源给出或未定位。
    geocode_source VARCHAR(20),
    geocode_at DATE,
    -- 营业信息（营业时间 / 联系电话）来源：来源文本优先，高德只补来源未给出的字段。
    -- SOURCE = 来自公开来源文本；AMAP_POI = 来自高德地图 POI（第三方数据）；NULL = 未知。
    business_source VARCHAR(20),
    business_synced_at DATE,
    -- 暂停服务时段：驿站临时关门 / 装修时设置，期间不接受新预约，
    -- 并通知该时段内已预约的用户（而不是让人白跑一趟）。
    suspended_from DATETIME,
    suspended_to DATETIME,
    suspend_reason VARCHAR(255),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_station_city (city_code),
    KEY idx_station_area (area),
    KEY idx_station_status (status),
    KEY idx_station_type (station_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 注意：本文件使用 CREATE TABLE IF NOT EXISTS，不会为已存在的表补充新增字段。
-- 若在旧库上增加字段，请使用 scripts/init-mysql.ps1 -Reset 重建数据库后再导入数据。

CREATE TABLE IF NOT EXISTS station_service (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    station_id BIGINT NOT NULL,
    service_name VARCHAR(80) NOT NULL,
    service_type VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    need_appoint TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_station_service_station FOREIGN KEY (station_id) REFERENCES station (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS service_appointment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    -- 预约语义：用户想提前联系驿站、确认某件事是否可行（而不是预约使用饮水/空调这类设施）。
    -- 因此事项由用户自行填写 service_name，不再从固定服务项目里选；
    -- service_id 保留兼容历史数据，新提交可以为空。
    service_id BIGINT,
    service_name VARCHAR(100) NOT NULL,
    contact_phone VARCHAR(30),
    appoint_date DATE NOT NULL,
    appoint_time VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_STATION_CONFIRMATION',
    audit_reason VARCHAR(255),
    -- 爽约标记：用户预约了但未按时到场。驿站可标记；同一用户累计爽约达到阈值后，
    -- 后续预约需要管理员确认（避免有人反复占时段又不来）。
    no_show TINYINT NOT NULL DEFAULT 0,
    no_show_at DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_appointment_user (user_id),
    KEY idx_appointment_date (appoint_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appeal (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    -- 定向反馈给某个驿站时的驿站编号；为空表示未指定（历史数据/平台诉求）。
    station_id BIGINT,
    title VARCHAR(150) NOT NULL,
    -- 分类：REQUEST 反映问题 / PRAISE 表扬感谢 / SUGGESTION 提出建议。
    -- 模块不止承载「诉求」，也承载骑手与驿站之间的正向互助（如补充雨衣、致谢包扎）。
    category VARCHAR(20) NOT NULL DEFAULT 'REQUEST',
    -- 反馈对象：STATION 反馈给驿站（驿站管理员受理）/ PLATFORM 反馈给平台管理员。
    -- 开题报告写明「平台管理员将诉求分派给驿站管理员或相关处理人员」，两种对象都支持。
    receiver_target VARCHAR(20) NOT NULL DEFAULT 'PLATFORM',
    appeal_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    area VARCHAR(50),
    image_url VARCHAR(500),
    anonymous TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_ACCEPTANCE',
    handler_id BIGINT,
    finish_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_appeal_status (status),
    KEY idx_appeal_area (area),
    KEY idx_appeal_station (station_id),
    KEY idx_appeal_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appeal_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    appeal_id BIGINT NOT NULL,
    handler_id BIGINT,
    action VARCHAR(50) NOT NULL,
    content TEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appeal_record_appeal FOREIGN KEY (appeal_id) REFERENCES appeal (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appeal_evaluation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    appeal_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score TINYINT NOT NULL,
    content VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 一个用户对一条诉求只能评价一次；服务层已先查，唯一索引是并发下的最后防线。
    UNIQUE KEY uk_appeal_evaluation_user (appeal_id, user_id),
    CONSTRAINT fk_appeal_evaluation_appeal FOREIGN KEY (appeal_id) REFERENCES appeal (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS news (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(30) NOT NULL,
    cover_url VARCHAR(255),
    content LONGTEXT NOT NULL,
    view_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    -- 平台声明：管理员对政策的立场、适用范围与免责说明，**用户可见**
    statement VARCHAR(1000),
    -- 行动建议：管理员给新就业群体的具体可执行建议，**用户可见**
    advice VARCHAR(1000),
    -- 整理人署名：让用户知道这条政策是谁整理的
    editor_name VARCHAR(50),
    -- 来源类型：OFFICIAL 官方（政府/群团/官方媒体）/ PLATFORM 平台企业自行发布
    source_type VARCHAR(20) NOT NULL DEFAULT 'OFFICIAL',
    -- 来源等级：AUTHORITATIVE 一手规范性文件 / OFFICIAL_MEDIA 官方媒体 / SECONDARY 第三方来源。
    -- 同样的政策内容，出自国务院文件还是商业媒体转述，可信度差别很大，界面要区分展示。
    source_level VARCHAR(20) NOT NULL DEFAULT 'SECONDARY',
    -- 发布方名称，例如「中共中央办公厅、国务院办公厅」「美团」
    publisher VARCHAR(120),
    -- 平台名称（仅 source_type=PLATFORM 时有值），用于按职业/平台筛选
    platform VARCHAR(40),
    -- 归属城市：NULL 表示不限城市（国家层面或全国性平台政策），在所有城市都可见；
    -- 非空时只在该城市可见，并在该城市排在前面。
    city_code VARCHAR(12),
    -- 来源发布日期，与入库时间 create_time 区分开
    published_at DATE,
    -- 原文链接，便于逐条核对
    source_url VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_news_category (category),
    KEY idx_news_city (city_code),
    KEY idx_news_source (source_type),
    KEY idx_news_published (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    notice_type VARCHAR(30) NOT NULL,
    target_type VARCHAR(30) NOT NULL DEFAULT 'ALL',
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    station_id BIGINT,
    activity_time DATETIME NOT NULL,
    address VARCHAR(255),
    max_count INT NOT NULL DEFAULT 50,
    content LONGTEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    -- 驿站提交的活动先进入待审核；平台审核通过后才可发布
    review_note VARCHAR(500),
    reviewer_id BIGINT,
    review_time DATETIME,
    -- 承办方：STATION 驿站 / PLATFORM 平台管理员 / EXTERNAL 第三方（外部机构）。
    -- 第三方活动只在站内展示并提供跳转链接，**不接受站内报名**——
    -- 报名与审核由对方平台负责，我们既不掌握名单也无法联系报名人。
    organizer_type VARCHAR(20) NOT NULL DEFAULT 'STATION',
    -- 仅 organizer_type=EXTERNAL 时有值：跳转到对方平台的详情/报名页。
    -- 采集来的真实活动也走这里：用户点过去看原文并自行报名，我们不代收报名。
    external_url VARCHAR(500),
    -- 以下字段用于「像政策资讯一样接入真实数据」：采集来的活动必须能核对到原文。
    -- 城市编码与 station/news 保持一致，便于按当前城市过滤与排序。
    city_code VARCHAR(10),
    -- 主办/发布机构，如「杭州市总工会」。驿站自办活动留空，由 station_id 表达主办方。
    publisher VARCHAR(120),
    -- 活动分类：技能培训 / 权益保障 / 健康关爱 / 法律援助 / 文体活动 / 子女关爱
    category VARCHAR(30),
    -- 一句话摘要，取自原文，不做发挥
    summary VARCHAR(500),
    -- 来源等级：GOV 政府群团 / MEDIA 官方媒体 / STATION 驿站自办 / PLATFORM 平台自办。
    -- 与 news.source_level 同一套口径，前端据此显示来源标签。
    source_level VARCHAR(20),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_station FOREIGN KEY (station_id) REFERENCES station (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS activity_signup (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    -- 报名时填写的信息。承办方要靠这些去联系报名人，因此姓名必填、电话建议填写。
    contact_name VARCHAR(50),
    phone VARCHAR(20),
    remark VARCHAR(255),
    -- 报名状态：SIGNED_UP 已报名 → CONTACTED 已联系 → APPROVED 通过 / REJECTED 未通过 / CANCELLED 已取消
    status VARCHAR(20) NOT NULL DEFAULT 'SIGNED_UP',
    -- 承办方的审核反馈，会展示给报名人
    review_note VARCHAR(500),
    reviewer_id BIGINT,
    review_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_activity_user (activity_id, user_id),
    KEY idx_signup_status (status),
    CONSTRAINT fk_activity_signup_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS station_evaluation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    station_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score TINYINT NOT NULL,
    service_score TINYINT,
    environment_score TINYINT,
    facility_score TINYINT,
    content VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 一个用户对同一驿站只能评价一次；服务层已先查，唯一索引是并发下的最后防线。
    UNIQUE KEY uk_station_evaluation_user (station_id, user_id),
    CONSTRAINT fk_station_evaluation_station FOREIGN KEY (station_id) REFERENCES station (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 系统只有三个身份。
-- 原 SUPER_ADMIN（超级管理员）已删除：它此前只是定义了角色行，从未分配给任何账号，
-- 却被 27 个文件的 @RequireRole 引用，属于"从未启用的幽灵角色"，徒增权限矩阵的复杂度。
-- 原 PLATFORM_ADMIN 保留编码、显示名改为「管理员」：管理员唯一统筹平台，
-- 通过**子账号 + 功能权限**（sys_user_permission）分工，而不是靠角色分级。
INSERT IGNORE INTO sys_role (role_code, role_name, remark) VALUES
    ('USER', '普通用户', '新就业群体用户'),
    ('STATION_ADMIN', '驿站管理员', '负责本驿站运营'),
    ('STATION_STAFF', '驿站工作人员', '在本站点接待预约、处理诉求与报名'),
    ('STATION_VOLUNTEER', '驿站志愿者', '在本站点协助接待与报名'),
    ('PLATFORM_ADMIN', '管理员', '统一统筹平台，可创建子账号并分发功能权限');

-- 功能权限目录：管理端的一个标签页或一类关键操作对应一个权限。
-- 管理员创建子账号时勾选授予哪些权限；子账号登录后只看到被授权的部分。
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_code VARCHAR(50) NOT NULL UNIQUE,
    permission_name VARCHAR(50) NOT NULL,
    remark VARCHAR(255),
    -- 是否可授予子账号。ACCOUNT_MANAGE 这类为 0：否则子账号能给自己加权限，等于没有权限体系。
    grantable TINYINT NOT NULL DEFAULT 1,
    sort_no INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 账号 ↔ 权限。系统管理员（非子账号）不受此表限制，拥有全部权限。
CREATE TABLE IF NOT EXISTS sys_user_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    permission_code VARCHAR(50) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_permission (user_id, permission_code),
    KEY idx_permission_code (permission_code),
    CONSTRAINT fk_user_permission_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 邮箱验证码：改账号 ID / 改密码 / 改联系方式时校验身份。
-- 只存哈希不存明文；同一邮箱 60 秒内不可重复发送；校验失败累计 5 次作废。
CREATE TABLE IF NOT EXISTS verification_code (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    -- 用途：CHANGE_USERNAME / CHANGE_PASSWORD / CHANGE_CONTACT / BIND_EMAIL
    purpose VARCHAR(30) NOT NULL,
    -- 收件邮箱
    target VARCHAR(120) NOT NULL,
    -- 申请该验证码的账号，防止 A 的验证码被用于改 B
    user_id BIGINT,
    code_hash VARCHAR(100) NOT NULL,
    expire_at DATETIME NOT NULL,
    used TINYINT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    -- 是否真实发送。演示环境未配置 SMTP 时为 0，此时验证码只写后端日志，
    -- 界面必须显著标注「演示环境未真实发送邮件」——不能把没发出去的说成发了。
    delivered TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_code_target_purpose (target, purpose),
    KEY idx_code_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 站内通知：预约审核、报名审核、诉求处理与暂停服务的统一消息出口。
-- 业务表只负责状态，用户看到的“发生了什么”由这里送达，避免用户必须反复刷新列表。
CREATE TABLE IF NOT EXISTS notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    -- APPOINTMENT / SIGNUP / APPEAL / SYSTEM
    type VARCHAR(30) NOT NULL,
    title VARCHAR(150) NOT NULL,
    content VARCHAR(500),
    -- 前端可跳转的路径；为空表示只展示不跳转
    link VARCHAR(255),
    read_flag TINYINT NOT NULL DEFAULT 0,
    biz_type VARCHAR(30),
    biz_id BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_notification_user (user_id, read_flag, create_time),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 驿站入驻申请：普通用户提交，平台管理员审核通过后创建驿站并升级为驿站管理员。
CREATE TABLE IF NOT EXISTS station_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    applicant_id BIGINT NOT NULL,
    station_name VARCHAR(100) NOT NULL,
    area VARCHAR(50) NOT NULL,
    address VARCHAR(255) NOT NULL,
    contact_name VARCHAR(50),
    contact_phone VARCHAR(20),
    open_time VARCHAR(100),
    station_type VARCHAR(50),
    city_code VARCHAR(20),
    description VARCHAR(1000),
    license_url VARCHAR(500),
    photo_urls VARCHAR(1000),
    -- PENDING / APPROVED / REJECTED / CANCELLED
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    review_note VARCHAR(500),
    reviewer_id BIGINT,
    created_station_id BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_station_application_status (status, create_time),
    KEY idx_station_application_applicant (applicant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 驿站工作人员/志愿者账号：只在本站点数据范围内工作。
CREATE TABLE IF NOT EXISTS station_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    station_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    -- STAFF 工作人员 / VOLUNTEER 志愿者
    member_role VARCHAR(20) NOT NULL DEFAULT 'STAFF',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_station_member (station_id, user_id),
    KEY idx_station_member_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 服务质量整改：平台下发，驿站反馈，平台复核关闭。
CREATE TABLE IF NOT EXISTS station_rectification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    station_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    level VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    deadline DATE,
    station_reply VARCHAR(1000),
    reviewer_id BIGINT,
    review_note VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_rectification_station (station_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 敏感操作审计：平台干预、审核、整改、成员变更都写这里。
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(80),
    actor_role VARCHAR(30),
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT,
    reason VARCHAR(500),
    detail VARCHAR(1000),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_create (create_time),
    KEY idx_audit_actor (actor_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 驿站信息变更申请与审批。
-- 驿站最清楚自己门口在哪，但坐标是权威数据（现有 1247/1344 条由高德 POI 派生并经三重校验），
-- 不能自改，因此位置变更走审批；营业时间与联系电话按需求可直接改（需邮箱验证）。
CREATE TABLE IF NOT EXISTS station_change_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    station_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    -- LOCATION 位置 / BUSINESS_HOURS 营业时间 / PHONE 联系电话 / ADDRESS 地址 / OTHER 其他
    change_type VARCHAR(30) NOT NULL,
    old_value VARCHAR(500),
    new_value VARCHAR(500) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    -- PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤回
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_id BIGINT,
    review_note VARCHAR(500),
    review_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_change_station (station_id),
    KEY idx_change_status (status),
    CONSTRAINT fk_change_station FOREIGN KEY (station_id) REFERENCES station (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 功能权限目录种子。grantable=0 的两项不可授予子账号，只能由管理员本人使用。
INSERT IGNORE INTO sys_permission (permission_code, permission_name, remark, grantable, sort_no) VALUES
    ('STATION_MANAGE',    '驿站管理',     '新增、修改、删除驿站',                        1, 10),
    ('SERVICE_MANAGE',    '服务项目管理', '服务项目增删改',                              1, 20),
    ('NEWS_MANAGE',       '资讯公告管理', '政策与公告的增删改、声明与建议创作、状态变更', 1, 30),
    ('ACTIVITY_MANAGE',   '活动管理',     '活动增删改、取消活动',                        1, 40),
    ('SIGNUP_MANAGE',     '报名记录',     '查看报名、联系与审核、给出反馈',              1, 50),
    ('APPOINTMENT_AUDIT', '预约审核',     '确认、拒绝预约、标记爽约',                    1, 60),
    ('APPEAL_HANDLE',     '诉求处理',     '分派、回复、办结诉求',                        1, 70),
    ('CHANGE_APPROVE',    '变更审批',     '审批驿站提交的信息与位置变更申请',            1, 80),
    ('DATA_REVIEW',       '数据复核',     '处理来源待核对、坐标为高德派生的驿站条目',    1, 90),
    ('REPORT_EXPORT',     '报表导出',     '导出各类台账',                                1, 100),
    ('ACCOUNT_MANAGE',    '账号管理',     '新建子账号、分发权限、改账号 ID、重置密码',   0, 110);
