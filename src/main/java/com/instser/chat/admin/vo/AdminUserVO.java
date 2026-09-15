package com.instser.chat.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员账号信息VO（返回给管理后台前端，不包含密码）
 *
 * @author instser
 * @since 2026-09-15
 */
@Data
@Builder
public class AdminUserVO {

    private Long id;

    private String username;

    private LocalDateTime lastLoginTime;
}
