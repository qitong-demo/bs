package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("station_service")
public class StationServiceItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stationId;
    private String serviceName;
    private String serviceType;
    private Boolean needAppoint;
    private Integer status;
    private LocalDateTime createTime;
}
