package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("city")
public class City {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String cityCode;
    private String cityName;
    private String province;
    /** 城市中心点经纬度，用于「定位后判断用户所在城市」与地图初始定位。 */
    private BigDecimal centerLng;
    private BigDecimal centerLat;
    private String status;
    private Integer sortNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
