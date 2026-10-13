package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("appeal_record")
public class AppealRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appealId;
    private Long handlerId;
    private String action;
    private String content;
    private LocalDateTime createTime;
}
