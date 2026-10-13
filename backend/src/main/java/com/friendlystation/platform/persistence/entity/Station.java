package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("station")
public class Station {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String stationName;
    /** 所属城市行政区划代码，例如杭州市 330100。 */
    private String cityCode;
    private String area;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String contactPhone;
    private String openTime;
    private String imageUrl;
    private String status;
    private Long managerId;
    /** 驿站类型，例如「小哥驿站」「城管驿站」。 */
    private String stationType;
    /** 数据来源与核对信息。 */
    private String sourceName;
    private String sourceUrl;
    private LocalDate sourcePublishedAt;
    private LocalDate verifiedAt;
    private String dataStatus;
    private String dataNote;
    /** 坐标来源：AMAP_POI / AMAP_GEOCODE；为空表示来源给出或未定位。 */
    private String geocodeSource;
    private LocalDate geocodeAt;
    /** 营业信息（营业时间/联系电话）来源：SOURCE（公开来源文本）/ AMAP_POI（高德地图）。 */
    private String businessSource;
    private LocalDate businessSyncedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
