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
    /** 城市编码，与 station/news 一致，用于按当前城市过滤。 */
    private String cityCode;
    /** 主办/发布机构；驿站自办活动留空。 */
    private String publisher;
    /** 活动分类：技能培训 / 权益保障 / 健康关爱 / 法律援助 / 文体活动 / 子女关爱。 */
    private String category;
    /** 一句话摘要，取自原文。 */
    private String summary;
    /** 来源等级：GOV / MEDIA / STATION / PLATFORM。 */
    private String sourceLevel;
    private LocalDateTime createTime;
}
