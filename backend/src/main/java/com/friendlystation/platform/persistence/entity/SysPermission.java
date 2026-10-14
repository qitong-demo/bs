package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 功能权限目录：管理端的一个标签页或一类关键操作对应一个权限。 */
@Data
@TableName("sys_permission")
public class SysPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String permissionCode;
    private String permissionName;
    private String remark;
    /** 是否可授予子账号。0 = 仅管理员本人可用（如账号管理，否则子账号能自我提权）。 */
    private Integer grantable;
    private Integer sortNo;
}
