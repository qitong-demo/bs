package com.friendlystation.platform.content;

import com.friendlystation.platform.common.PageResult;

import java.util.Map;

/**
 * 资讯服务。内存实现与 MySQL 实现对外行为一致。
 * 输出 Map 字段固定为：id, title, category, content, viewCount, status, statusLabel, date, createTime,
 * sourceType, sourceTypeLabel, publisher, platform, cityCode, national, publishedAt, sourceUrl, source。
 */
public interface NewsService {
    /** 前台分页，仅返回已发布资讯（不限城市与来源）。 */
    PageResult<Map<String, Object>> publicPage(long current, long size, String category, String keyword);

    /**
     * 前台分页（分城市与来源）。
     *
     * @param cityCode   当前城市代码；非空时只返回「该城市 + 不限城市」的资讯，且该城市的排在前面
     * @param sourceType OFFICIAL / PLATFORM；空表示不限
     */
    PageResult<Map<String, Object>> publicPage(long current, long size, String category, String keyword,
                                               String cityCode, String sourceType, String platform);

    /** 后台分页，返回全部状态，status 非空时按状态过滤。 */
    PageResult<Map<String, Object>> adminPage(long current, long size, String category, String keyword, String status);

    /** 资讯详情，不存在抛出业务异常。 */
    Map<String, Object> detail(Long id);

    /** 用户打开详情：返回详情并让阅读数 +1。 */
    Map<String, Object> view(Long id);

    /** 新增资讯。 */
    Map<String, Object> create(Map<String, Object> payload);

    /** 修改资讯，可改标题、分类、正文与状态。 */
    Map<String, Object> update(Long id, Map<String, Object> payload);

    /** 物理删除资讯。 */
    void delete(Long id);
}
