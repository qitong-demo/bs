package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("news")
public class News {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String category;
    private String coverUrl;
    private String content;
    private Integer viewCount;
    private String status;
    /** 来源类型：OFFICIAL 官方 / PLATFORM 平台企业。 */
    private String sourceType;
    /** 来源等级：AUTHORITATIVE 一手文件 / OFFICIAL_MEDIA 官方媒体 / SECONDARY 第三方来源。 */
    private String sourceLevel;
    /** 发布方名称。 */
    private String publisher;
    /** 平台名称（仅平台来源有值）。 */
    private String platform;
    /** 归属城市；null 表示不限城市，在所有城市都可见。 */
    private String cityCode;
    /** 来源发布日期。 */
    private LocalDate publishedAt;
    /** 原文链接，便于逐条核对。 */
    /** 平台声明：管理员对政策的立场、适用范围与免责说明，用户可见。 */
    private String statement;
    /** 行动建议：管理员给新就业群体的具体可执行建议，用户可见。 */
    private String advice;
    /** 整理人署名。 */
    private String editorName;    private String sourceUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
