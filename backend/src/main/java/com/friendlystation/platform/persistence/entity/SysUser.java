package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String phone;
    /** 邮箱：用于收发验证码（改账号 ID / 改密码 / 改联系方式时校验身份）。可为空。 */
    private String email;
    /** 凭据版本：改账号 ID 或密码时 +1，用于让已签发的令牌立即失效。 */
    private Integer credentialVersion;
    private String avatar;
    private String userType;
    private String jobType;
    private String area;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
