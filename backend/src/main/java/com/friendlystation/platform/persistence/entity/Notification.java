package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内通知：业务状态变化后写给用户的一条消息。 */
@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** APPOINTMENT / SIGNUP / APPEAL / SYSTEM。 */
    private String type;
    private String title;
    private String content;
    /** 前端跳转路径，可为空。 */
    private String link;
    /** 0 未读 / 1 已读。 */
    private Integer readFlag;
    private String bizType;
    private Long bizId;
    private LocalDateTime createTime;
}
