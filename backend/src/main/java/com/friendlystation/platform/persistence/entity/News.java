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
    /** 发布方名称。 */
    private String publisher;
    /** 平台名称（仅平台来源有值）。 */
    private String platform;
    /** 归属城市；null 表示不限城市，在所有城市都可见。 */
    private String cityCode;
    /** 来源发布日期。 */
    private LocalDate publishedAt;
    /** 原文链接，便于逐条核对。 */
    private String sourceUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
