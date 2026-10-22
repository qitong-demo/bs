package com.friendlystation.platform.station;

import com.friendlystation.platform.common.PageResult;

import java.util.List;
import java.util.Map;

/** 驿站入驻申请：用户提交，平台管理员审核通过后创建驿站并升级账号。 */
public interface StationApplicationService {
    Map<String, Object> submit(Map<String, Object> request, long userId);

    List<Map<String, Object>> mine(long userId);

    PageResult<Map<String, Object>> page(long current, long size, String status, String keyword);

    Map<String, Object> approve(long id, long reviewerId, String reviewNote);

    Map<String, Object> reject(long id, long reviewerId, String reviewNote);

    Map<String, Object> cancel(long id, long userId);

    long pendingCount();
}
