package com.friendlystation.platform.common;

/**
 * 经纬度计算工具。
 */
public final class GeoUtils {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtils() {
    }

    /**
     * 两个经纬度点之间的大圆距离（公里）。任一参数为 null 时返回 {@link Double#MAX_VALUE}，
     * 便于在「找最近城市」时把缺少坐标的城市排到最后。
     */
    public static double distanceKm(Double lng1, Double lat1, Double lng2, Double lat2) {
        if (lng1 == null || lat1 == null || lng2 == null || lat2 == null) {
            return Double.MAX_VALUE;
        }
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
