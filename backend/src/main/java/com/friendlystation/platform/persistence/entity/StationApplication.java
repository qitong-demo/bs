package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 驿站入驻申请：用户提交后由平台管理员审核。 */
@Data
@TableName("station_application")
public class StationApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicantId;
    private String stationName;
    private String area;
    private String address;
    private String contactName;
    private String contactPhone;
    private String openTime;
    private String stationType;
    private String cityCode;
    private String description;
    private String licenseUrl;
    private String photoUrls;
    private String status;
    private String reviewNote;
    private Long reviewerId;
    private Long createdStationId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
