package com.friendlystation.platform.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.SysRole;
import com.friendlystation.platform.persistence.entity.SysUser;
import com.friendlystation.platform.persistence.entity.SysUserRole;
import com.friendlystation.platform.persistence.mapper.SysRoleMapper;
import com.friendlystation.platform.persistence.mapper.SysUserMapper;
import com.friendlystation.platform.persistence.mapper.SysUserRoleMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** mysql profile 的账号与角色权限管理，角色来自 sys_role / sys_user_role 关系表。 */
@Service
@Profile("mysql")
public class MysqlAdminUserService implements AdminUserService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    public MysqlAdminUserService(SysUserMapper userMapper, SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public PageResult<Map<String, Object>> userPage(long current, long size, String role, String keyword) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
        String word = keyword == null ? "" : keyword.trim();
        if (!word.isEmpty()) {
            query.and(wrapper -> wrapper.like(SysUser::getUsername, word)
                    .or().like(SysUser::getRealName, word)
                    .or().like(SysUser::getPhone, word)
                    .or().like(SysUser::getArea, word));
        }
        if (role != null && !role.isBlank()) {
            SysRole target = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, role.trim()));
            if (target == null) {
                return new PageResult<>(safeCurrent, safeSize, 0, List.of());
            }
            List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getRoleId, target.getId())).stream()
                    .map(SysUserRole::getUserId)
                    .toList();
            if (userIds.isEmpty()) {
                return new PageResult<>(safeCurrent, safeSize, 0, List.of());
            }
            query.in(SysUser::getId, userIds);
        }
        query.orderByAsc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), views(result.getRecords()));
    }

    @Override
    public Map<String, Object> userDetail(long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return views(List.of(user)).get(0);
    }

    @Override
    @Transactional
    public Map<String, Object> updateUser(long userId, Map<String, Object> payload) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        String phone = FormatUtils.text(payload.get("phone"), null);
        if (phone != null && !phone.equals(user.getPhone())) {
            Long duplicated = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, phone)
                    .ne(SysUser::getId, userId));
            if (duplicated != null && duplicated > 0) {
                throw new BusinessException("手机号已被其他账号使用");
            }
            user.setPhone(phone);
        }
        String realName = FormatUtils.text(payload.get("realName"), null);
        if (realName != null) {
            user.setRealName(realName);
        }
        String jobType = FormatUtils.text(payload.get("jobType"), null);
        if (jobType != null) {
            user.setJobType(jobType);
        }
        String area = FormatUtils.text(payload.get("area"), null);
        if (area != null) {
            user.setArea(area);
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return views(List.of(user)).get(0);
    }

    @Override
    @Transactional
    public Map<String, Object> updateStatus(long userId, String status) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setStatus("停用".equals(normalizeStatus(status)) ? 0 : 1);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return views(List.of(user)).get(0);
    }

    @Override
    @Transactional
    public Map<String, Object> setRoles(long userId, List<String> roleCodes) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        List<String> codes = sanitize(roleCodes);
        List<SysRole> roles = new ArrayList<>();
        for (String code : codes) {
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, code));
            if (role == null) {
                throw new BusinessException("角色未初始化：" + code);
            }
            roles.add(role);
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        for (SysRole role : roles) {
            SysUserRole link = new SysUserRole();
            link.setUserId(userId);
            link.setRoleId(role.getId());
            userRoleMapper.insert(link);
        }
        user.setUserType(codes.get(0));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return views(List.of(user)).get(0);
    }

    @Override
    public List<Map<String, Object>> roles() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
        Map<Long, Long> counts = userRoleMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.groupingBy(SysUserRole::getRoleId, Collectors.counting()));
        return roles.stream().map(role -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", role.getId());
            out.put("roleCode", role.getRoleCode());
            out.put("roleName", role.getRoleName());
            out.put("remark", role.getRemark() == null ? "" : role.getRemark());
            out.put("userCount", counts.getOrDefault(role.getId(), 0L));
            return out;
        }).toList();
    }

    // ------------------------------------------------------------ 内部工具

    private List<Map<String, Object>> views(List<SysUser> users) {
        Map<Long, List<String>> roleCodes = rolesByUser(users.stream().map(SysUser::getId).toList());
        return users.stream().map(user -> {
            List<String> codes = roleCodes.getOrDefault(user.getId(), List.of());
            String primary = codes.isEmpty() ? (user.getUserType() == null ? "USER" : user.getUserType()) : codes.get(0);
            boolean enabled = user.getStatus() == null || user.getStatus() == 1;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", user.getId());
            out.put("username", user.getUsername());
            out.put("realName", user.getRealName() == null ? "" : user.getRealName());
            out.put("phone", user.getPhone() == null ? "" : user.getPhone());
            out.put("jobType", user.getJobType() == null ? "" : user.getJobType());
            out.put("area", user.getArea() == null ? "" : user.getArea());
            out.put("role", primary);
            out.put("roleName", RoleCatalog.roleName(primary));
            out.put("roles", codes.isEmpty() ? List.of(primary) : codes);
            out.put("status", enabled ? 1 : 0);
            out.put("statusLabel", enabled ? "正常" : "停用");
            out.put("createTime", FormatUtils.dateTime(user.getCreateTime()));
            out.put("dataStatus", "系统计算结果");
            return out;
        }).toList();
    }

    private Map<Long, List<String>> rolesByUser(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, ids).orderByAsc(SysUserRole::getId));
        if (links.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = links.stream().map(SysUserRole::getRoleId).filter(Objects::nonNull).distinct().toList();
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> codeById = roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleCode, (first, second) -> first));
        Map<Long, List<String>> grouped = new LinkedHashMap<>();
        for (SysUserRole link : links) {
            String code = codeById.get(link.getRoleId());
            if (code != null) {
                grouped.computeIfAbsent(link.getUserId(), key -> new ArrayList<>()).add(code);
            }
        }
        return grouped;
    }

    private static List<String> sanitize(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BusinessException("至少需要保留一个角色");
        }
        List<String> codes = roleCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            throw new BusinessException("至少需要保留一个角色");
        }
        for (String code : codes) {
            if (!RoleCatalog.isValid(code)) {
                throw new BusinessException("角色编码无效：" + code);
            }
        }
        return codes;
    }

    private static String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim();
        if (value.equals("正常") || value.equals("1") || value.equalsIgnoreCase("ENABLED")) {
            return "正常";
        }
        if (value.equals("停用") || value.equals("0") || value.equalsIgnoreCase("DISABLED")) {
            return "停用";
        }
        throw new BusinessException("账号状态只能是 正常 或 停用");
    }
}
