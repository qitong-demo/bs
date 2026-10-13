package com.friendlystation.platform.station;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 驿站服务项目新增/编辑请求。
 * serviceType 默认 GENERAL，needAppoint 默认 false，status 默认 1。
 */
public record StationServiceItemRequest(
        @NotNull(message = "驿站不能为空") Long stationId,
        @NotBlank(message = "服务名称不能为空") String serviceName,
        String serviceType,
        Boolean needAppoint,
        Integer status
) {
}
