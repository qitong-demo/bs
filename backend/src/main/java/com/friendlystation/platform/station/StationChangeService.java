package com.friendlystation.platform.station;

import com.friendlystation.platform.auth.CurrentUser;
import com.friendlystation.platform.common.BusinessException;

import java.util.List;
import java.util.Map;

/**
 * 驿站信息变更申请与审批。
 *
 * <p>哪些字段走审批、哪些可以直接改，由这里集中判定——两个 profile 与前端都据此，
 * 避免"页面上能改、后端拒绝"这类不一致。</p>
 */
public interface StationChangeService {

    /**
     * 需要审批才能改的字段。
     *
     * <p>位置属于权威数据：现有坐标由高德 POI 派生并经三重置信度校验，
     * 手工改动不留痕会让来源标注失真。因此驿站不能自改，必须走审批。</p>
     */
    List<String> REVIEW_REQUIRED_TYPES = List.of("LOCATION");

    /** 可以直接改、但需要邮箱验证码的字段。 */
    List<String> DIRECT_TYPES = List.of("BUSINESS_HOURS", "PHONE", "ADDRESS");

    /** 驿站提交变更申请。 */
    Map<String, Object> submit(CurrentUser applicant, Map<String, Object> payload);

    /** 某驿站的全部申请（驿站侧查看自己的）。 */
    List<Map<String, Object>> listByStation(CurrentUser viewer, long stationId);

    /** 当前账号负责驿站的全部申请；平台级返回全部。 */
    List<Map<String, Object>> mine(CurrentUser viewer);

    /** 待审批列表（管理员侧）；驿站管理员只看到自己负责驿站的申请。 */
    List<Map<String, Object>> pendingList(CurrentUser viewer);

    /** 审批通过：写入驿站对应字段并留痕。 */
    Map<String, Object> approve(CurrentUser reviewer, long requestId, String reviewNote);

    /** 审批驳回：需要写明原因。 */
    Map<String, Object> reject(CurrentUser reviewer, long requestId, String reviewNote);

    /** 申请人撤回仍待审批的申请。 */
    Map<String, Object> cancel(CurrentUser applicant, long requestId);

    /** 校验变更类型是否合法。 */
    static String requireType(String type) {
        String text = type == null ? "" : type.trim().toUpperCase();
        if (!REVIEW_REQUIRED_TYPES.contains(text) && !DIRECT_TYPES.contains(text)) {
            throw new BusinessException("变更类型不合法，只能是 "
                    + String.join("、", REVIEW_REQUIRED_TYPES) + " 或 " + String.join("、", DIRECT_TYPES));
        }
        return text;
    }
}
