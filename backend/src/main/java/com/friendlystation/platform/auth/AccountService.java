package com.friendlystation.platform.auth;

public interface AccountService {
    AccountUser findByUsername(String username);

    AccountUser register(String username, String phone, String password);

    // ---------------------------------------------------------------- 凭据维护
    // 供「账号安全」使用。两个实现（mysql / 内存）行为必须一致。

    /** 账号 ID 是否已被占用（不区分大小写）。 */
    boolean usernameExists(String username);

    /** 账号 ID 是否被**其他人**占用；用于改自己账号时的唯一性校验。 */
    boolean usernameTakenByOther(String username, long selfId);

    /** 是否存在该账号。 */
    boolean exists(long userId);

    /** 读取账号 ID；不存在返回 null。 */
    String usernameOf(long userId);

    /** 读取绑定邮箱；未绑定或账号不存在返回 null。 */
    String emailOf(long userId);

    /** 改账号 ID。调用方需先完成邮箱验证码校验与唯一性校验。 */
    void updateUsername(long userId, String newUsername);

    /** 改密码。传入的必须是**已加密**的密码，明文不落库。 */
    void updatePassword(long userId, String encodedPassword);

    /** 绑定或更换邮箱。 */
    void updateEmail(long userId, String email);

    /**
     * 创建管理员子账号。
     *
     * <p>与 {@link #register} 的区别：子账号的角色是管理员（`PLATFORM_ADMIN`），
     * 但能否做具体的事由授权表决定，而不是角色。</p>
     *
     * <p>{@code password} 传**明文**，编码由实现内部完成——与 {@code register} 一致。
     * 调用方若先编码，会被再编码一次，密码双重哈希后永远登录不上。</p>
     */
    AccountUser createAdminSubAccount(String username, String phone, String password);
}
