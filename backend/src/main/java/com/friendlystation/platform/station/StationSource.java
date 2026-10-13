package com.friendlystation.platform.station;

/**
 * 驿站数据的来源与核对信息，用于说明该条记录可以回溯到哪里、核对到什么程度。
 *
 * <p>坐标单独标注：公开来源一般不提供点位坐标，本项目的坐标由高德地图地理编码派生，
 * 因此用 {@code geocodeSource} 与 {@code geocodeAt} 记录，**不参与 {@code dataStatus} 的判定**
 * （{@code dataStatus} 只表示"字段能否从公开来源逐条核对"）。</p>
 *
 * @param sourceName        来源名称，例如「杭州城管驿站开放名单」
 * @param sourceUrl         来源访问地址
 * @param sourcePublishedAt 来源发布日期（yyyy-MM-dd）
 * @param verifiedAt        本次核对日期（yyyy-MM-dd）
 * @param dataStatus        VERIFIED：名称/地址/区域/开放时间/服务设施都能逐条核对；
 *                          PARTIAL：有字段来源未逐条给出；MANUAL：后台录入
 * @param dataNote          需要特别说明的边界，例如「服务设施为来源对该批驿站的统一描述」
 * @param geocodeSource     坐标来源：AMAP_POI（按名称匹配高德 POI）/ AMAP_GEOCODE（按地址地理编码）；
 *                          空字符串表示来源给出或尚未定位
 * @param geocodeAt         坐标获取日期（yyyy-MM-dd）；未做地理编码时为空字符串
 * @param businessSource    营业信息（营业时间/联系电话）来源：SOURCE = 公开来源文本；AMAP_POI = 高德地图；
 *                          空字符串表示未知
 * @param businessSyncedAt  营业信息同步日期（yyyy-MM-dd）
 */
public record StationSource(
        String sourceName,
        String sourceUrl,
        String sourcePublishedAt,
        String verifiedAt,
        String dataStatus,
        String dataNote,
        String geocodeSource,
        String geocodeAt,
        String businessSource,
        String businessSyncedAt
) {
    /** 来源信息缺失时的占位值，避免前端出现 null。 */
    public static StationSource empty() {
        return new StationSource("", "", "", "", "UNKNOWN", "", "", "", "", "");
    }
}
