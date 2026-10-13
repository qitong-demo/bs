package com.friendlystation.platform.admin;

import com.friendlystation.platform.common.PageResult;

import java.util.List;
import java.util.Map;

/** 管理端的账号与角色权限服务。 */
public interface AdminUserService {
    PageResult<Map<String, Object>> userPage(long current, long size, String role, String keyword);

    Map<String, Object> userDetail(long userId);

    Map<String, Object> updateUser(long userId, Map<String, Object> payload);

    Map<String, Object> updateStatus(long userId, String status);

    Map<String, Object> setRoles(long userId, List<String> roleCodes);

    List<Map<String, Object>> roles();
}
