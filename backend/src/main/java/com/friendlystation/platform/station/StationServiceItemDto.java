package com.friendlystation.platform.station;

/**
 * 驿站服务项目展示对象。
 *
 * @param status 1=启用，0=停用
 * @param createTime 创建时间，格式 yyyy-MM-dd HH:mm:ss
 */
public record StationServiceItemDto(
        Long id,
        Long stationId,
        String stationName,
        String serviceName,
        String serviceType,
        Boolean needAppoint,
        Integer status,
        String createTime
) {
}
