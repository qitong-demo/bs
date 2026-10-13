package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("appeal_evaluation")
public class AppealEvaluation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appealId;
    private Long userId;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
