package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("appeal")
public class Appeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    /** 分类：REQUEST 反映问题 / PRAISE 表扬感谢 / SUGGESTION 提出建议。 */
    private String category;
    /** 反馈对象：STATION 驿站 / PLATFORM 平台管理员。 */
    private String receiverTarget;
    private String appealType;
    private String content;
    private String area;
    private String imageUrl;
    private Boolean anonymous;
    private String status;
    private Long handlerId;
    private LocalDateTime finishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
