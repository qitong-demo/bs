package com.friendlystation.platform.content;

import com.friendlystation.platform.common.PageResult;

import java.util.Map;

/**
 * 公告服务。内存实现与 MySQL 实现对外行为一致。
 * 输出 Map 字段固定为：id, title, noticeType, targetType, content, status, statusLabel, date, createTime, source。
 */
public interface NoticeService {
    /** 前台分页，仅返回已发布公告。 */
    PageResult<Map<String, Object>> publicPage(long current, long size, String noticeType, String keyword);

    /** 后台分页，返回全部状态，status 非空时按状态过滤。 */
    PageResult<Map<String, Object>> adminPage(long current, long size, String noticeType, String keyword, String status);

    /** 公告详情，不存在抛出业务异常。 */
    Map<String, Object> detail(Long id);

    /** 新增公告。 */
    Map<String, Object> create(Map<String, Object> payload);

    /** 修改公告。 */
    Map<String, Object> update(Long id, Map<String, Object> payload);

    /** 物理删除公告。 */
    void delete(Long id);
}
