package com.friendlystation.platform.content;

import com.friendlystation.platform.common.PageResult;

import java.util.Map;

/**
 * 活动服务。内存实现与 MySQL 实现对外行为一致。
 * 输出 Map 字段固定为：id, title, stationId, stationName, activityTime, address, maxCount,
 * signedCount, remaining, content, status, statusLabel, createTime。
 */
public interface ActivityService {
    /** 前台分页，仅返回已发布活动，stationId 非空时按驿站过滤。 */
    PageResult<Map<String, Object>> publicPage(long current, long size, Long stationId, String keyword);

    /** 后台分页，返回全部状态，status 非空时按状态过滤。 */
    PageResult<Map<String, Object>> adminPage(long current, long size, String status, String keyword);

    /** 活动详情，不存在抛出业务异常。 */
    Map<String, Object> detail(Long id);

    /** 新增活动。 */
    Map<String, Object> create(Map<String, Object> payload);

    /** 修改活动。 */
    Map<String, Object> update(Long id, Map<String, Object> payload);

    /** 物理删除活动。 */
    void delete(Long id);

    /** 剩余名额，活动不存在抛出业务异常。 */
    int remainingSlots(Long activityId);
}
