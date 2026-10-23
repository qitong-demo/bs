package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorId;
    private String actorName;
    private String actorRole;
    private String action;
    private String targetType;
    private Long targetId;
    private String reason;
    private String detail;
    private LocalDateTime createTime;
}
