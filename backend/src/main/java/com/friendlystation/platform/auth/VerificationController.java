package com.friendlystation.platform.auth;

import com.friendlystation.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮箱验证码：发送与配置查询。
 *
 * <p>校验（verify）不单独开接口——它由改账号 ID / 改密码等接口内部调用，
 * 避免验证码校验与业务操作分成两步后被绕过。</p>
 */
@RestController
@RequestMapping("/api/user/verification")
public class VerificationController {
    private final VerificationCodeService verificationCodeService;
    private final MailService mailService;

    public VerificationController(VerificationCodeService verificationCodeService, MailService mailService) {
        this.verificationCodeService = verificationCodeService;
        this.mailService = mailService;
    }

    /**
     * 邮件服务是否已配置。
     *
     * <p>前端据此决定是否显示「演示环境未真实发送邮件」的提示——
     * 让用户知道能不能真收到，而不是发完干等。</p>
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean configured = mailService.isConfigured();
        out.put("mailConfigured", configured);
        if (!configured) {
            out.put("hint", "演示环境未配置邮件服务：验证码不会真实发送，请查看后端控制台日志。");
        }
        return ApiResponse.ok(out);
    }

    /**
     * 发送验证码。
     *
     * <p>收件邮箱的取法分两种：
     * 绑定邮箱（purpose=BIND_EMAIL）时用请求里填的地址；
     * 其余用途一律用**账号已绑定的邮箱**，不接受请求指定，
     * 否则任何人都能把验证码发到自己的邮箱再改别人的账号。</p>
     */
    @PostMapping("/send")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> send(@RequestBody Map<String, Object> payload) {
        CurrentUser user = CurrentUserContext.require();
        Map<String, Object> data = payload == null ? Map.of() : payload;
        // 先校验用途：否则用途写错时会先报出"尚未绑定邮箱"，让人以为是邮箱问题
        String purpose = VerificationCodeSupport.requirePurpose(
                String.valueOf(data.getOrDefault("purpose", "")));
        boolean binding = VerificationCodeSupport.PURPOSE_BIND_EMAIL.equals(purpose);
        String email = binding
                ? String.valueOf(data.getOrDefault("email", ""))
                : verificationCodeService.boundEmailOf(user.id());
        return ApiResponse.ok(verificationCodeService.issue(user.id(), email, purpose, !mailService.isConfigured()));
    }
}
