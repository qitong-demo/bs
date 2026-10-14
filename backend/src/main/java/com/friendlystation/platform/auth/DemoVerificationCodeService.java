package com.friendlystation.platform.auth;

import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.workflow.DemoWorkflowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存 profile 下的邮箱验证码实现。
 *
 * <p>演示模式没有数据库，因此验证码存在内存里（重启即失效，这对演示可接受）。
 * 与 {@link MysqlVerificationCodeService} **同一套校验规则**：限频、限次、过期、归属绑定，
 * 保证两种 profile 行为一致。</p>
 */
@Service
@Profile("!mysql")
public class DemoVerificationCodeService implements VerificationCodeService {
    private static final Logger log = LoggerFactory.getLogger(DemoVerificationCodeService.class);

    /** 内存中的验证码记录。字段名与实体保持一致，便于对照。 */
    private final List<Map<String, Object>> codes = new ArrayList<>();
    private final SecureRandom random = new SecureRandom();
    private final DemoWorkflowStore store;
    private final MailService mailService;

    public DemoVerificationCodeService(DemoWorkflowStore store, MailService mailService) {
        this.store = store;
        this.mailService = mailService;
    }

    @Override
    public synchronized Map<String, Object> issue(long userId, String email, String purpose, boolean exposeCode) {
        String target = VerificationCodeSupport.requireEmail(email);
        String normalizedPurpose = VerificationCodeSupport.requirePurpose(purpose);
        LocalDateTime now = LocalDateTime.now();

        boolean tooSoon = codes.stream().anyMatch(item ->
                target.equals(item.get("target"))
                        && normalizedPurpose.equals(item.get("purpose"))
                        && ((LocalDateTime) item.get("createTime"))
                        .isAfter(now.minusSeconds(VerificationCodeSupport.RESEND_INTERVAL_SECONDS)));
        if (tooSoon) {
            throw new BusinessException("验证码发送过于频繁，请 "
                    + VerificationCodeSupport.RESEND_INTERVAL_SECONDS + " 秒后再试");
        }

        String code = VerificationCodeSupport.randomCode(random);
        boolean delivered = send(target, normalizedPurpose, code);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", (long) (codes.size() + 1));
        record.put("purpose", normalizedPurpose);
        record.put("target", target);
        record.put("userId", userId);
        record.put("codeHash", VerificationCodeSupport.hash(code));
        record.put("expireAt", now.plusSeconds(VerificationCodeSupport.CODE_TTL_SECONDS));
        record.put("used", 0);
        record.put("attemptCount", 0);
        record.put("delivered", delivered ? 1 : 0);
        record.put("createTime", now);
        codes.add(record);
        return MysqlVerificationCodeService.result(delivered, target, code, exposeCode);
    }

    @Override
    public synchronized void verify(long userId, String email, String purpose, String code) {
        String target = VerificationCodeSupport.requireEmail(email);
        String normalizedPurpose = VerificationCodeSupport.requirePurpose(purpose);
        String input = code == null ? "" : code.trim();
        if (input.isEmpty()) {
            throw new BusinessException("请填写邮箱验证码");
        }

        Map<String, Object> record = null;
        for (int i = codes.size() - 1; i >= 0; i--) {
            Map<String, Object> item = codes.get(i);
            if (target.equals(item.get("target")) && normalizedPurpose.equals(item.get("purpose"))
                    && Integer.valueOf(0).equals(item.get("used"))) {
                record = item;
                break;
            }
        }
        if (record == null) {
            throw new BusinessException("请先获取邮箱验证码");
        }
        if (((LocalDateTime) record.get("expireAt")).isBefore(LocalDateTime.now())) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (((Integer) record.get("attemptCount")) >= VerificationCodeSupport.MAX_ATTEMPTS) {
            throw new BusinessException("验证码尝试次数过多，请重新获取");
        }
        Object owner = record.get("userId");
        if (owner instanceof Long ownerId && ownerId != userId) {
            throw new BusinessException("验证码与当前账号不匹配，请重新获取");
        }
        if (!VerificationCodeSupport.hash(input).equals(record.get("codeHash"))) {
            record.put("attemptCount", ((Integer) record.get("attemptCount")) + 1);
            throw new BusinessException("验证码不正确");
        }
        record.put("used", 1);
    }

    @Override
    public String boundEmailOf(long userId) {
        String email = store.emailOf(userId);
        if (email == null || email.isBlank()) {
            throw new BusinessException("当前账号尚未绑定邮箱，请先绑定后再进行该操作");
        }
        return email;
    }

    private boolean send(String target, String purpose, String code) {
        String label = VerificationCodeSupport.PURPOSE_LABELS.getOrDefault(purpose, "身份验证");
        boolean delivered = mailService.send(target, "友好驿站平台 · " + label + "验证码",
                "你的验证码是 " + code + "，" + (VerificationCodeSupport.CODE_TTL_SECONDS / 60) + " 分钟内有效。\n"
                        + "如果这不是你本人的操作，请忽略本邮件并尽快修改密码。");
        if (!delivered) {
            log.info("[演示环境] 未配置 SMTP，验证码未真实发送。邮箱={}，用途={}，验证码={}", target, purpose, code);
        }
        return delivered;
    }
}
