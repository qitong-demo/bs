package com.friendlystation.platform.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送。
 *
 * <p>本项目用邮箱验证码代替短信：个人开发者申请不到短信资质
 * （阿里云 2025-06-06 公告明确「短信服务不支持申请个人自用资质」），
 * 而邮箱用普通 SMTP 授权码即可真实送达。</p>
 *
 * <p><b>未配置 SMTP 时的诚实降级</b>：不假装发送成功，而是返回 {@code false}，
 * 由调用方把验证码写进后端日志，并在界面明确标注「演示环境未真实发送邮件」。
 * 把没发出去的说成发了，会让演示者以为功能可用而实际收不到——这比不做更糟。</p>
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String from;
    private final String host;

    public MailService(ObjectProvider<JavaMailSender> senderProvider,
                       @Value("${spring.mail.username:}") String from,
                       @Value("${spring.mail.host:}") String host) {
        this.senderProvider = senderProvider;
        this.from = from;
        this.host = host;
    }

    /**
     * 是否配置了 SMTP，前端据此提示用户"能不能真收到邮件"。
     *
     * <p>注意不能只看 {@code JavaMailSender} bean 是否存在：Spring Boot 在
     * {@code spring.mail.host} 为空时**依然会创建该 bean**，于是"已配置"会被误判为真，
     * 用户以为能收到邮件、实际发不出去。这里必须判断 host 本身。</p>
     */
    public boolean isConfigured() {
        return host != null && !host.isBlank();
    }

    /**
     * 发送邮件。
     *
     * @return true 表示确实交给了 SMTP；false 表示未配置或发送失败（调用方需降级处理）
     */
    public boolean send(String to, String subject, String text) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("未配置 SMTP（spring.mail.host 为空），邮件未真实发送。收件人={}，主题={}", to, subject);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            sender.send(message);
            return true;
        } catch (Exception exception) {
            // 发信失败不能让整个请求 500：降级为"未发出"，由调用方提示用户稍后重试
            log.warn("邮件发送失败，收件人={}，原因={}", to, exception.getMessage());
            return false;
        }
    }
}
