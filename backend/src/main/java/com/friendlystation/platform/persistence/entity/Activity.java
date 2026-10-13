package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity")
public class Activity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private Long stationId;
    private LocalDateTime activityTime;
    private String address;
    private Integer maxCount;
    private String content;
    private String status;
    /** 承办方：STATION 驿站 / PLATFORM 平台管理员 / EXTERNAL 第三方外部机构。 */
    private String organizerType;
    /** 仅 EXTERNAL 有值：跳转到对方平台的详情或报名页。 */
    private String externalUrl;
    private LocalDateTime createTime;
}
