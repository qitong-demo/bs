package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮箱验证码。
 *
 * <p>为什么要验证码：改账号 ID、改密码、改联系方式这类操作一旦被冒用，损失不可逆。
 * 短信通道个人申请不到（阿里云 2025-06-06 公告：短信服务不支持个人自用资质），
 * 因此用邮箱验证码——这是本项目能真实送达的验证通道。</p>
 *
 * <p>安全约定：只存哈希不存明文；同一邮箱限频；校验失败限次；过期即作废。</p>
 */
@Data
@TableName("verification_code")
public class VerificationCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用途：CHANGE_USERNAME / CHANGE_PASSWORD / CHANGE_CONTACT / BIND_EMAIL */
    private String purpose;
    /** 收件邮箱 */
    private String target;
    /** 申请该验证码的账号，防止 A 的验证码被用于改 B */
    private Long userId;
    /** 验证码哈希（不存明文） */
    private String codeHash;
    private LocalDateTime expireAt;
    private Integer used;
    /** 校验失败次数，超过阈值作废 */
    private Integer attemptCount;
    /**
     * 是否真实发出。
     * 演示环境未配置 SMTP 时为 0，此时验证码只写后端日志，
     * 界面必须显著标注「演示环境未真实发送邮件」——不能把没发出去的说成发了。
     */
    private Integer delivered;
    private LocalDateTime createTime;
}
