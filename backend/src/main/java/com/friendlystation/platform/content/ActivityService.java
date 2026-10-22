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
    /**
     * 前台活动分页。
     *
     * @param cityCode 当前城市编码；非空时只返回「本市 + 全国性」活动，并把本市排在前面。
     *                 与政策资讯同一套口径。
     */
    PageResult<Map<String, Object>> publicPage(long current, long size, Long stationId, String keyword, String cityCode);

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

    /**
     * 报名成功后的计数回调。
     *
     * <p>MySQL 实现的报名人数实时从 activity_signup 统计，无需维护；内存实现没有这张表，
     * 必须在这里累加，否则列表会一直显示「剩余满额」。</p>
     */
    default void onSignup(long activityId) {
    }

    /** 报名取消后的计数回调；与 {@link #onSignup(long)} 成对，避免内存计数只增不减。 */
    default void onSignupCancel(long activityId) {
    }
}
