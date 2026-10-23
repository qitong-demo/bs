package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("station_rectification")
public class StationRectification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stationId;
    private String title;
    private String content;
    private String level;
    private String status;
    private LocalDate deadline;
    private String stationReply;
    private Long reviewerId;
    private String reviewNote;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
