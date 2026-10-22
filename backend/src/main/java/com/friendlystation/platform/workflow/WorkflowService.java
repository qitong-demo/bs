package com.friendlystation.platform.workflow;

import com.friendlystation.platform.common.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 预约、诉求与活动报名的业务服务。内存实现与 MySQL 实现必须对外返回一致的结构。 */
public interface WorkflowService {
    Map<String, Object> addAppointment(Map<String, Object> request, long userId);

    List<Map<String, Object>> appointments(long userId);

    /**
     * 管理端预约分页。{@code stationIds} 为 null 表示不限制数据范围（平台级角色），
     * 空集合表示当前账号范围内没有可见数据。
     */
    PageResult<Map<String, Object>> appointmentPage(long current, long size, String status, String keyword,
                                                    List<Long> stationIds);

    /**
     * 审核预约。
     *
     * @param stationIds 当前操作者可管理的驿站；null 表示平台级不限制，空集合表示没有任何可见数据
     */
    Map<String, Object> auditAppointment(Map<String, Object> request, long operatorId, List<Long> stationIds);

    Map<String, Object> cancelAppointment(long id, long userId);

    /**
     * 标记预约爽约：用户预约了但未按时到场。
     *
     * <p>驿站可标记；同一用户累计爽约会体现在返回的 noShowCount 里，
     * 供驿站与管理员判断是否值得继续接受该用户的预约。</p>
     *
     * @param remark 备注，可选
     * @param stationIds 当前操作者可管理的驿站；null 表示平台级不限制
     */
    Map<String, Object> markNoShow(long id, long operatorId, String remark, List<Long> stationIds);

    /** 完成预约：只有已确认的预约可以标记为已完成。 */
    Map<String, Object> completeAppointment(long id, long operatorId, List<Long> stationIds);

    /**
     * 驿站设置暂停服务后，通知该时段内已提交预约的用户。
     *
     * @return 实际写入的通知条数
     */
    int notifySuspension(long stationId, LocalDateTime from, LocalDateTime to, String reason);

    Map<String, Object> addAppeal(Map<String, Object> request, long userId);

    List<Map<String, Object>> appeals(long userId);

    /**
     * 管理端诉求分页。
     *
     * @param stationIds 定向到驿站的诉求按编号过滤；null 表示平台级不限制
     * @param areas      未定向驿站的历史诉求按区域兜底；null 表示不限
     */
    PageResult<Map<String, Object>> appealPage(long current, long size, String status, String keyword,
                                               List<Long> stationIds, List<String> areas);

    /** 诉求详情。viewerId 用于判断是否本人；匿名诉求对非本人隐藏提交人。 */
    Map<String, Object> appealDetail(long id, long viewerId);

    /** 诉求处理记录。匿名诉求的 SUBMIT 记录对非本人隐藏提交人。 */
    List<Map<String, Object>> appealRecords(long appealId, long viewerId);

    /** 当前账号能否查看该诉求：本人、平台级、定向驿站，或未定向时负责区域一致。 */
    boolean canAccessAppeal(long id, long viewerId, boolean platformLevel, List<Long> stationIds, List<String> areas);

    Map<String, Object> assignAppeal(Map<String, Object> request, long operatorId);

    Map<String, Object> handleAppeal(Map<String, Object> request, long operatorId);

    Map<String, Object> evaluateAppeal(Map<String, Object> request, long userId);

    Map<String, Object> addSignup(Map<String, Object> request, long userId);

    /**
     * 承办方处理报名：联系 / 审核通过 / 审核未通过，并填写给报名人的反馈。
     *
     * @param request 含 id 与 status（CONTACTED / APPROVED / REJECTED）与 reviewNote
     * @param stationIds 当前操作者可管理的驿站；null 表示平台级不限制
     */
    Map<String, Object> reviewSignup(Map<String, Object> request, long operatorId, List<Long> stationIds);

    /** 报名人取消自己的报名：只允许已报名/已联系状态。 */
    Map<String, Object> cancelSignup(long id, long userId);

    List<Map<String, Object>> mySignups(long userId);

    /** 管理端报名列表；stationIds 为 null 表示平台级不限制，空集合表示没有可见数据。 */
    List<Map<String, Object>> allSignups(List<Long> stationIds);

    Map<String, Object> overview();

    /**
     * 按数据范围统计概览。
     *
     * @param stationIds 可见的驿站编号；**null 表示不限制**（平台级角色），
     *                   空列表表示该账号没有负责的驿站（结果应为 0）
     * @param areas      可见的区域；null 表示不限制。诉求表没有驿站编号，只能按区域近似限定
     */
    Map<String, Object> overview(List<Long> stationIds, List<String> areas);
}
