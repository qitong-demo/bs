package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("station_evaluation")
public class StationEvaluation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stationId;
    private Long userId;
    private Integer score;
    private Integer serviceScore;
    private Integer environmentScore;
    private Integer facilityScore;
    private String content;
    private LocalDateTime createTime;
}
