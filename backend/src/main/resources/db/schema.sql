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
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    audit_reason VARCHAR(255),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_appointment_user (user_id),
    KEY idx_appointment_date (appoint_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appeal (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
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
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    handler_id BIGINT,
    finish_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_appeal_status (status),
    KEY idx_appeal_area (area),
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
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_station FOREIGN KEY (station_id) REFERENCES station (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS activity_signup (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    phone VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'SIGNED_UP',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_activity_user (activity_id, user_id),
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
    CONSTRAINT fk_station_evaluation_station FOREIGN KEY (station_id) REFERENCES station (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO sys_role (role_code, role_name, remark) VALUES
    ('USER', '普通用户', '新就业群体用户'),
    ('STATION_ADMIN', '驿站管理员', '负责本驿站运营'),
    ('PLATFORM_ADMIN', '平台管理员', '负责平台全局管理'),
    ('SUPER_ADMIN', '超级管理员', '负责系统最高权限');
