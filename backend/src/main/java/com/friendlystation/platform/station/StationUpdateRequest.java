package com.friendlystation.platform.station;

import jakarta.validation.constraints.NotBlank;

/**
 * 驿站编辑请求，字段与新增请求保持一致：名称/区域/地址必填，其余可空。
 * 可空字段为 null 时表示保留原值。
 */
public record StationUpdateRequest(
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
