package com.friendlystation.platform.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.persistence.entity.SysUser;
import com.friendlystation.platform.persistence.entity.SysUserPermission;
import com.friendlystation.platform.persistence.mapper.SysUserMapper;
import com.friendlystation.platform.persistence.mapper.SysUserPermissionMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** mysql profile 下的权限分配：sys_user_permission 表。 */
@Service
@Profile("mysql")
public class MysqlAdminPermissionService implements AdminPermissionService {

    private final SysUserPermissionMapper userPermissionMapper;
    private final SysUserMapper userMapper;

    public MysqlAdminPermissionService(SysUserPermissionMapper userPermissionMapper, SysUserMapper userMapper) {
        this.userPermissionMapper = userPermissionMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<Map<String, Object>> catalog() {
        return PermissionCatalog.DEFINITIONS;
    }

    @Override
    public List<String> grantedOf(long userId) {
        return userPermissionMapper.selectList(new LambdaQueryWrapper<SysUserPermission>()
                        .eq(SysUserPermission::getUserId, userId))
                .stream()
                .map(SysUserPermission::getPermissionCode)
                .sorted()
                .toList();
    }

    @Override
    @Transactional
    public Map<String, Object> grant(long userId, List<String> codes) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        List<String> cleaned = PermissionCatalog.validateGrant(codes);
        // 覆盖式：先清空再写入。子账号权限集合小，这样比逐个 diff 更不易出错。
        userPermissionMapper.delete(new LambdaQueryWrapper<SysUserPermission>()
                .eq(SysUserPermission::getUserId, userId));
        for (String code : cleaned) {
            SysUserPermission row = new SysUserPermission();
            row.setUserId(userId);
            row.setPermissionCode(code);
            row.setCreateTime(LocalDateTime.now());
            userPermissionMapper.insert(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("permissions", cleaned);
        out.put("count", cleaned.size());
        out.put("dataStatus", "系统计算结果");
        return out;
    }

    @Override
    public List<String> effectiveOf(long userId, String roleCode) {
        // 驿站管理员走角色固定权限，不查授权表——他的职责由角色决定，
        // 若也走授权表，新建的驿站管理员默认没有任何权限，反而要管理员手工配一遍
        if ("STATION_ADMIN".equalsIgnoreCase(roleCode)) {
            return PermissionCatalog.STATION_ADMIN_PERMISSIONS;
        }
        if ("STATION_STAFF".equalsIgnoreCase(roleCode)) {
            return PermissionCatalog.STATION_STAFF_PERMISSIONS;
        }
        if ("STATION_VOLUNTEER".equalsIgnoreCase(roleCode)) {
            return PermissionCatalog.STATION_VOLUNTEER_PERMISSIONS;
        }
        // 主管理员不受授权表限制，避免误删自己权限后无人能恢复
        if (isPrimaryAdmin(userId)) {
            return PermissionCatalog.DEFINITIONS.stream()
                    .map(item -> String.valueOf(item.get("permissionCode")))
                    .toList();
        }
        return grantedOf(userId);
    }

    /** 主管理员：账号 ID 为 platform_admin 的内置账号。 */
    private boolean isPrimaryAdmin(long userId) {
        SysUser user = userMapper.selectById(userId);
        return user != null && PermissionCatalog.PRIMARY_ADMIN_USERNAME.equalsIgnoreCase(user.getUsername());
    }
}
