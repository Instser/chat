package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("timestamp", LocalDateTime.now());
        data.put("application", "jinyu-chat");
        data.put("version", "1.0.0");
        
        log.info("健康检查接口被调用");
        return Result.success(data, "系统运行正常");
    }

    /**
     * 测试接口
     */
    @GetMapping("/hello")
    public Result<String> hello() {
        log.info("测试接口被调用");
        return Result.success("Hello, Jinyu Chat Backend!", "测试成功");
    }
}
