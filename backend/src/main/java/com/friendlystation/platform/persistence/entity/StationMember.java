package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 驿站工作人员/志愿者。 */
@Data
@TableName("station_member")
public class StationMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stationId;
    private Long userId;
    /** STAFF / VOLUNTEER */
    private String memberRole;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
