package com.friendlystation.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.persistence.entity.SysRole;
import com.friendlystation.platform.persistence.entity.SysUser;
import com.friendlystation.platform.persistence.entity.SysUserRole;
import com.friendlystation.platform.persistence.mapper.SysRoleMapper;
import com.friendlystation.platform.persistence.mapper.SysUserMapper;
import com.friendlystation.platform.persistence.mapper.SysUserRoleMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class MysqlAccountService implements AccountService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordService passwordService;

    public MysqlAccountService(
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            PasswordService passwordService
    ) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordService = passwordService;
    }

    @Override
    public AccountUser findByUsername(String username) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getStatus, 1));
        return user == null ? null : toAccountUser(user);
    }

    @Override
    @Transactional
    public AccountUser register(String username, String phone, String password) {
        Long duplicate = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .or()
                .eq(SysUser::getPhone, phone));
        if (duplicate > 0) {
            throw new BusinessException("用户名或手机号已注册");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPhone(phone);
        user.setPassword(passwordService.encode(password));
        user.setUserType("USER");
        user.setStatus(1);
        userMapper.insert(user);

        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "USER"));
        if (role == null) {
            throw new BusinessException("默认用户角色未初始化");
        }
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
        return toAccountUser(user);
    }

    @Override
    @Transactional
    public AccountUser createAdminSubAccount(String username, String phone, String password) {
        Long duplicate = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .or()
                .eq(SysUser::getPhone, phone));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException("账号 ID 或手机号已被占用");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPhone(phone);
        user.setPassword(passwordService.encode(password));
        // 角色是管理员，但能做哪些事由 sys_user_permission 决定
        user.setUserType("ADMIN");
        user.setStatus(1);
        userMapper.insert(user);

        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "PLATFORM_ADMIN"));
        if (role == null) {
            throw new BusinessException("管理员角色未初始化");
        }
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
        return toAccountUser(user);
    }

    // ------------------------------------------------------------ 凭据维护

    @Override
    public boolean usernameExists(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        // 不区分大小写查重：MySQL 默认排序规则本身不区分，这里显式说明意图
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim()));
        return count != null && count > 0;
    }

    @Override
    public boolean usernameTakenByOther(String username, long selfId) {
        if (username == null || username.isBlank()) {
            return false;
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim())
                .ne(SysUser::getId, selfId));
        return count != null && count > 0;
    }

    @Override
    public boolean exists(long userId) {
        return userMapper.selectById(userId) != null;
    }

    @Override
    public String usernameOf(long userId) {
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getUsername();
    }

    @Override
    public String emailOf(long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return null;
        }
        return user.getEmail();
    }

    @Override
    @Transactional
    public void updateUsername(long userId, String newUsername) {
        int updated = userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getUsername, newUsername.trim()));
        if (updated == 0) {
            throw new BusinessException("账号不存在");
        }
    }

    @Override
    @Transactional
    public void updatePassword(long userId, String encodedPassword) {
        int updated = userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPassword, encodedPassword));
        if (updated == 0) {
            throw new BusinessException("账号不存在");
        }
    }

    @Override
    @Transactional
    public void updateEmail(long userId, String email) {
        int updated = userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getEmail, email));
        if (updated == 0) {
            throw new BusinessException("账号不存在");
        }
    }

    private AccountUser toAccountUser(SysUser user) {
        SysUserRole userRole = userRoleMapper.selectOne(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, user.getId())
                .last("LIMIT 1"));
        String roleCode = "USER";
        if (userRole != null) {
            SysRole role = roleMapper.selectById(userRole.getRoleId());
            if (role != null) {
                roleCode = role.getRoleCode();
            }
        }
        return new AccountUser(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getPhone(),
                roleCode,
                user.getRealName(),
                user.getJobType(),
                user.getArea()
        );
    }
}
