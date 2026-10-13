package com.friendlystation.platform.auth;

import com.friendlystation.platform.common.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile("!mysql")
public class MockAccountService implements AccountService {
    private final PasswordService passwordService;
    private final AtomicLong idGenerator = new AtomicLong(3);
    private final Map<String, AccountUser> users = new ConcurrentHashMap<>();
    /** 被停用的账号 id：保留记录以便管理端展示与恢复启用，但不再允许登录。 */
    private final Set<Long> disabledUserIds = ConcurrentHashMap.newKeySet();

    public MockAccountService(PasswordService passwordService) {
        this.passwordService = passwordService;
        users.put("rider_xiaowang", new AccountUser(
                1L, "rider_xiaowang", passwordService.encode("password"), "13800002088",
                "USER", "骑手小王", "外卖骑手", "西湖区"
        ));
        users.put("station_admin_01", new AccountUser(
                2L, "station_admin_01", passwordService.encode("password"), "13800002089",
                "STATION_ADMIN", "陈管理员", "驿站管理员", "拱墅区"
        ));
        users.put("platform_admin", new AccountUser(
                3L, "platform_admin", passwordService.encode("password"), "13800002090",
                "PLATFORM_ADMIN", "平台管理员", "平台管理员", "杭州市"
        ));
    }

    @Override
    public AccountUser findByUsername(String username) {
        AccountUser user = users.get(username);
        if (user == null || disabledUserIds.contains(user.id())) {
            return null;
        }
        return user;
    }

    public AccountUser findById(long id) {
        return users.values().stream().filter(user -> user.id() == id).findFirst().orElse(null);
    }

    /** 供管理端列出全部演示账号，按 id 升序，包含已停用账号。 */
    public List<AccountUser> list() {
        return users.values().stream().sorted(Comparator.comparingLong(AccountUser::id)).toList();
    }

    public boolean isEnabled(long id) {
        return !disabledUserIds.contains(id);
    }

    public AccountUser updateStatus(long id, String status) {
        AccountUser current = findById(id);
        if (current == null) {
            throw new BusinessException("用户不存在");
        }
        if ("停用".equals(status)) {
            disabledUserIds.add(id);
        } else {
            disabledUserIds.remove(id);
        }
        return current;
    }

    /** 更新演示账号资料；手机号不能与其他账号重复。 */
    public AccountUser updateProfile(long id, String realName, String phone, String jobType, String area) {
        AccountUser current = findById(id);
        if (current == null) {
            throw new BusinessException("用户不存在");
        }
        if (phone != null && !phone.isBlank() && !phone.equals(current.phone())
                && users.values().stream().anyMatch(user -> user.id() != id && phone.equals(user.phone()))) {
            throw new BusinessException("手机号已被其他账号使用");
        }
        AccountUser updated = new AccountUser(
                current.id(),
                current.username(),
                current.passwordHash(),
                phone == null || phone.isBlank() ? current.phone() : phone,
                current.role(),
                realName == null ? current.realName() : realName,
                jobType == null ? current.jobType() : jobType,
                area == null ? current.area() : area
        );
        users.put(updated.username(), updated);
        return updated;
    }

    /** 内存演示账号每个账号只保存一个角色。 */
    public AccountUser updateRole(long id, String roleCode) {
        AccountUser current = findById(id);
        if (current == null) {
            throw new BusinessException("用户不存在");
        }
        AccountUser updated = new AccountUser(
                current.id(), current.username(), current.passwordHash(), current.phone(),
                roleCode, current.realName(), current.jobType(), current.area()
        );
        users.put(updated.username(), updated);
        return updated;
    }

    @Override
    public AccountUser register(String username, String phone, String password) {
        if (users.containsKey(username)) {
            throw new BusinessException("用户名已存在");
        }
        if (users.values().stream().anyMatch(user -> user.phone().equals(phone))) {
            throw new BusinessException("手机号已注册");
        }
        AccountUser user = new AccountUser(
                idGenerator.incrementAndGet(),
                username,
                passwordService.encode(password),
                phone,
                "USER",
                username,
                null,
                null
        );
        users.put(username, user);
        return user;
    }
}
