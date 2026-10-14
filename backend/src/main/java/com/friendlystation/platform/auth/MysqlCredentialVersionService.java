package com.friendlystation.platform.auth;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.friendlystation.platform.persistence.entity.SysUser;
import com.friendlystation.platform.persistence.mapper.SysUserMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** mysql profile 下的凭据版本：存在 sys_user.credential_version。 */
@Service
@Profile("mysql")
public class MysqlCredentialVersionService implements CredentialVersionService {

    private final SysUserMapper userMapper;

    public MysqlCredentialVersionService(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public int versionOf(long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return -1;
        }
        return user.getCredentialVersion() == null ? 0 : user.getCredentialVersion();
    }

    @Override
    public void bump(long userId) {
        // 用 SQL 自增而不是"读出来 +1 再写回"，避免并发改密时两处都读到旧值、只加一次
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .setSql("credential_version = credential_version + 1"));
    }
}
