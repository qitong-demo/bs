package com.friendlystation.platform.workflow;

import com.friendlystation.platform.common.PageResult;

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

    Map<String, Object> auditAppointment(Map<String, Object> request, long operatorId);

    Map<String, Object> cancelAppointment(long id, long userId);

    Map<String, Object> addAppeal(Map<String, Object> request, long userId);

    List<Map<String, Object>> appeals(long userId);

    /**
     * 管理端诉求分页。{@code areas} 为 null 表示不限制数据范围（平台级角色），
     * 空集合表示当前账号范围内没有可见数据。
     */
    PageResult<Map<String, Object>> appealPage(long current, long size, String status, String keyword,
                                               List<String> areas);

    Map<String, Object> appealDetail(long id);

    List<Map<String, Object>> appealRecords(long appealId);

    Map<String, Object> assignAppeal(Map<String, Object> request, long operatorId);

    Map<String, Object> handleAppeal(Map<String, Object> request, long operatorId);

    Map<String, Object> evaluateAppeal(Map<String, Object> request, long userId);

    Map<String, Object> addSignup(Map<String, Object> request, long userId);

    /**
     * 承办方处理报名：联系 / 审核通过 / 审核未通过，并填写给报名人的反馈。
     *
     * @param request 含 id 与 status（CONTACTED / APPROVED / REJECTED）与 reviewNote
     */
    Map<String, Object> reviewSignup(Map<String, Object> request, long operatorId);

    List<Map<String, Object>> mySignups(long userId);

    List<Map<String, Object>> allSignups();

    Map<String, Object> overview();
}
