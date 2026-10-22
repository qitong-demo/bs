package com.friendlystation.platform.station;

import java.util.List;

/**
 * 驿站展示对象。
 *
 * @param cityCode    所属城市行政区划代码，例如杭州市 330100
 * @param stationType 驿站类型，例如「小哥驿站」「城管驿站」
 * @param longitude   经度；来源未提供时为 null，前端地图据此决定是否打点
 * @param latitude    纬度；来源未提供时为 null
 * @param managerId   负责该驿站的账号编号，用于驿站管理员的数据范围判定；未分配时为 null
 * @param source      数据来源与核对信息
 * @param createTime  创建时间，格式 yyyy-MM-dd HH:mm:ss（内存实现固定为空字符串）
 */
public record StationDto(
        Long id,
        String cityCode,
        String stationName,
        String stationType,
        String area,
        String address,
        Double longitude,
        Double latitude,
        String contactPhone,
        String openTime,
        String distance,
        List<String> services,
        String status,
        Long managerId,
        StationSource source,
        /** 暂停服务开始/结束时间，格式 yyyy-MM-dd HH:mm:ss；未暂停时为空串。 */
        String suspendedFrom,
        String suspendedTo,
        String suspendReason,
        String createTime
) {
}
