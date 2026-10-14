package com.friendlystation.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.persistence.entity.SysUser;
import com.friendlystation.platform.persistence.entity.VerificationCode;
import com.friendlystation.platform.persistence.mapper.SysUserMapper;
import com.friendlystation.platform.persistence.mapper.VerificationCodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** mysql profile 下的邮箱验证码实现：验证码落库，重启后仍有效。 */
@Service
@Profile("mysql")
public class MysqlVerificationCodeService implements VerificationCodeService {
    private static final Logger log = LoggerFactory.getLogger(MysqlVerificationCodeService.class);

    private final VerificationCodeMapper codeMapper;
    private final SysUserMapper userMapper;
    private final MailService mailService;
    private final SecureRandom random = new SecureRandom();

    public MysqlVerificationCodeService(VerificationCodeMapper codeMapper, SysUserMapper userMapper,
                                        MailService mailService) {
        this.codeMapper = codeMapper;
        this.userMapper = userMapper;
        this.mailService = mailService;
    }

    @Override
    @Transactional
    public Map<String, Object> issue(long userId, String email, String purpose, boolean exposeCode) {
        String target = VerificationCodeSupport.requireEmail(email);
        String normalizedPurpose = VerificationCodeSupport.requirePurpose(purpose);
        LocalDateTime now = LocalDateTime.now();

        // 限频：同一邮箱 + 用途在 60 秒内不可重复发送
        Long recent = codeMapper.selectCount(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getTarget, target)
                .eq(VerificationCode::getPurpose, normalizedPurpose)
                .gt(VerificationCode::getCreateTime, now.minusSeconds(VerificationCodeSupport.RESEND_INTERVAL_SECONDS)));
        if (recent != null && recent > 0) {
            throw new BusinessException("验证码发送过于频繁，请 "
                    + VerificationCodeSupport.RESEND_INTERVAL_SECONDS + " 秒后再试");
        }

        String code = VerificationCodeSupport.randomCode(random);
        boolean delivered = send(target, normalizedPurpose, code);

        VerificationCode record = new VerificationCode();
        record.setPurpose(normalizedPurpose);
        record.setTarget(target);
        record.setUserId(userId);
        record.setCodeHash(VerificationCodeSupport.hash(code));
        record.setExpireAt(now.plusSeconds(VerificationCodeSupport.CODE_TTL_SECONDS));
        record.setUsed(0);
        record.setAttemptCount(0);
        record.setDelivered(delivered ? 1 : 0);
        record.setCreateTime(now);
        codeMapper.insert(record);
        return result(delivered, target, code, exposeCode);
    }

    @Override
    @Transactional
    public void verify(long userId, String email, String purpose, String code) {
        String target = VerificationCodeSupport.requireEmail(email);
        String normalizedPurpose = VerificationCodeSupport.requirePurpose(purpose);
        String input = code == null ? "" : code.trim();
        if (input.isEmpty()) {
            throw new BusinessException("请填写邮箱验证码");
        }

        VerificationCode record = codeMapper.selectOne(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getTarget, target)
                .eq(VerificationCode::getPurpose, normalizedPurpose)
                .eq(VerificationCode::getUsed, 0)
                .orderByDesc(VerificationCode::getId)
                .last("LIMIT 1"));
        if (record == null) {
            throw new BusinessException("请先获取邮箱验证码");
        }
        if (record.getExpireAt() != null && record.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (record.getAttemptCount() != null && record.getAttemptCount() >= VerificationCodeSupport.MAX_ATTEMPTS) {
            throw new BusinessException("验证码尝试次数过多，请重新获取");
        }
        if (record.getUserId() != null && record.getUserId() != userId) {
            throw new BusinessException("验证码与当前账号不匹配，请重新获取");
        }
        if (!VerificationCodeSupport.hash(input).equals(record.getCodeHash())) {
            codeMapper.update(null, new LambdaUpdateWrapper<VerificationCode>()
                    .eq(VerificationCode::getId, record.getId())
                    .setSql("attempt_count = attempt_count + 1"));
            throw new BusinessException("验证码不正确");
        }
        codeMapper.update(null, new LambdaUpdateWrapper<VerificationCode>()
                .eq(VerificationCode::getId, record.getId())
                .set(VerificationCode::getUsed, 1));
    }

    @Override
    public String boundEmailOf(long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException("当前账号尚未绑定邮箱，请先绑定后再进行该操作");
        }
        return user.getEmail();
    }

    private boolean send(String target, String purpose, String code) {
        String label = VerificationCodeSupport.PURPOSE_LABELS.getOrDefault(purpose, "身份验证");
        boolean delivered = mailService.send(target, "友好驿站平台 · " + label + "验证码",
                "你的验证码是 " + code + "，" + (VerificationCodeSupport.CODE_TTL_SECONDS / 60) + " 分钟内有效。\n"
                        + "如果这不是你本人的操作，请忽略本邮件并尽快修改密码。");
        if (!delivered) {
            // 未配置 SMTP：写日志让演示环境能继续，界面必须显著标注"未真实发送"
            log.info("[演示环境] 未配置 SMTP，验证码未真实发送。邮箱={}，用途={}，验证码={}", target, purpose, code);
        }
        return delivered;
    }

    /** 组装返回值，两种实现共用同一结构。 */
    static Map<String, Object> result(boolean delivered, String target, String code, boolean exposeCode) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("delivered", delivered);
        out.put("expireSeconds", VerificationCodeSupport.CODE_TTL_SECONDS);
        out.put("target", VerificationCodeSupport.maskEmail(target));
        if (!delivered) {
            out.put("demoHint", "演示环境未配置邮件服务，验证码未真实发送；请查看后端控制台日志。");
            if (exposeCode) {
                out.put("demoCode", code);
            }
        }
        return out;
    }
}
