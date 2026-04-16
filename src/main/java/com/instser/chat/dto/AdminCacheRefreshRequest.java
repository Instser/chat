package com.instser.chat.dto;

import lombok.Data;

/**
 * 管理员缓存刷新请求
 */
@Data
public class AdminCacheRefreshRequest {

    private String account;

    private String password;
}
