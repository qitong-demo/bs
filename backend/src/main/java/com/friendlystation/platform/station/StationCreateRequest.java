package com.friendlystation.platform.station;

import jakarta.validation.constraints.NotBlank;

/**
 * 驿站新增请求。
 *
 * @param cityCode    所属城市行政区划代码；留空时记为平台默认城市
 * @param stationType 驿站类型，例如「小哥驿站」「城管驿站」；留空时记为「未分类」
 */
public record StationCreateRequest(
        @NotBlank(message = "驿站名称不能为空") String stationName,
        @NotBlank(message = "所属区域不能为空") String area,
        @NotBlank(message = "详细地址不能为空") String address,
        String contactPhone,
        String openTime,
        String status,
        String stationType,
        String cityCode
) {
}
