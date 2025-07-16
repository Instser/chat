# 07-16 03:11 - Gemini API密钥轮询管理方案

## 更新时间
2025年7月16日 03:11 (北京时间)

## 版本标识
v5.0.0-gemini-api-key-rotation-system

## 方案概述

设计一个灵活的Gemini API密钥轮询管理系统，支持：
- 多个API密钥的轮询使用
- 动态添加/删除密钥
- 密钥健康状态监控
- 失败密钥的自动禁用和重试
- 配置热更新
- 多种轮询策略（轮询、随机、加权）

## 当前配置状态

### 硬编码参数
- **模型代码**: `gemini-2.5-flash`（已更新）
- **API密钥**: `your-google-api-key`
- **基础URL**: `https://generativelanguage.googleapis.com/v1beta`
- **超时时间**: `10000ms`

## 核心组件设计

### 1. 密钥管理配置类

#### 1.1 GeminiApiKeyConfig.java
```java
@Data
@Component
@ConfigurationProperties(prefix = "gemini.api")
public class GeminiApiKeyConfig {
    
    /**
     * API密钥列表
     */
    private List<String> keys = new ArrayList<>();
    
    /**
     * 密钥轮询策略：ROUND_ROBIN(轮询), RANDOM(随机), WEIGHTED(加权)
     */
    private String strategy = "ROUND_ROBIN";
    
    /**
     * 密钥健康检查间隔（秒）
     */
    private int healthCheckInterval = 300;
    
    /**
     * 密钥失败重试次数
     */
    private int maxRetries = 3;
    
    /**
     * 密钥禁用时间（秒）
     */
    private int disableTimeout = 600;
}
```

#### 1.2 ApiKeyStatus.java
```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiKeyStatus {
    
    /**
     * API密钥
     */
    private String apiKey;
    
    /**
     * 是否可用
     */
    private boolean available = true;
    
    /**
     * 失败次数
     */
    private int failureCount = 0;
    
    /**
     * 最后使用时间
     */
    private LocalDateTime lastUsed;
    
    /**
     * 最后失败时间
     */
    private LocalDateTime lastFailure;
    
    /**
     * 禁用到期时间
     */
    private LocalDateTime disabledUntil;
    
    /**
     * 总请求次数
     */
    private long totalRequests = 0;
    
    /**
     * 成功请求次数
     */
    private long successRequests = 0;
    
    /**
     * 权重（用于加权轮询）
     */
    private int weight = 1;
}
```

### 2. 密钥轮询管理器

#### 2.1 GeminiApiKeyManager.java
```java
@Component
@Slf4j
public class GeminiApiKeyManager {
    
    private final GeminiApiKeyConfig config;
    private final Map<String, ApiKeyStatus> keyStatusMap = new ConcurrentHashMap<>();
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final Random random = new Random();
    
    @Autowired
    public GeminiApiKeyManager(GeminiApiKeyConfig config) {
        this.config = config;
        initializeKeys();
        startHealthCheckScheduler();
    }
    
    /**
     * 初始化密钥状态
     */
    private void initializeKeys() {
        for (String key : config.getKeys()) {
            keyStatusMap.put(key, new ApiKeyStatus(key, true, 0, null, null, null, 0, 0, 1));
        }
        log.info("【密钥管理】初始化{}个API密钥", config.getKeys().size());
    }
    
    /**
     * 获取下一个可用的API密钥
     */
    public String getNextApiKey() {
        List<String> availableKeys = getAvailableKeys();
        
        if (availableKeys.isEmpty()) {
            log.error("【密钥管理】没有可用的API密钥");
            throw new RuntimeException("没有可用的API密钥");
        }
        
        String selectedKey = selectKeyByStrategy(availableKeys);
        updateKeyUsage(selectedKey);
        
        log.debug("【密钥管理】选择API密钥：{}...", maskApiKey(selectedKey));
        return selectedKey;
    }
    
    /**
     * 根据策略选择密钥
     */
    private String selectKeyByStrategy(List<String> availableKeys) {
        switch (config.getStrategy().toUpperCase()) {
            case "RANDOM":
                return availableKeys.get(random.nextInt(availableKeys.size()));
                
            case "WEIGHTED":
                return selectWeightedKey(availableKeys);
                
            case "ROUND_ROBIN":
            default:
                int index = currentIndex.getAndIncrement() % availableKeys.size();
                return availableKeys.get(index);
        }
    }
    
    /**
     * 加权选择密钥
     */
    private String selectWeightedKey(List<String> availableKeys) {
        int totalWeight = availableKeys.stream()
            .mapToInt(key -> keyStatusMap.get(key).getWeight())
            .sum();
            
        int randomWeight = random.nextInt(totalWeight);
        int currentWeight = 0;
        
        for (String key : availableKeys) {
            currentWeight += keyStatusMap.get(key).getWeight();
            if (randomWeight < currentWeight) {
                return key;
            }
        }
        
        return availableKeys.get(0); // fallback
    }
    
    /**
     * 获取可用密钥列表
     */
    private List<String> getAvailableKeys() {
        LocalDateTime now = LocalDateTime.now();
        
        return keyStatusMap.entrySet().stream()
            .filter(entry -> {
                ApiKeyStatus status = entry.getValue();
                // 检查是否在禁用期内
                if (status.getDisabledUntil() != null && now.isBefore(status.getDisabledUntil())) {
                    return false;
                }
                // 重新启用之前被禁用的密钥
                if (status.getDisabledUntil() != null && now.isAfter(status.getDisabledUntil())) {
                    status.setAvailable(true);
                    status.setDisabledUntil(null);
                    status.setFailureCount(0);
                    log.info("【密钥管理】密钥重新启用：{}...", maskApiKey(entry.getKey()));
                }
                return status.isAvailable();
            })
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * 更新密钥使用统计
     */
    private void updateKeyUsage(String apiKey) {
        ApiKeyStatus status = keyStatusMap.get(apiKey);
        if (status != null) {
            status.setLastUsed(LocalDateTime.now());
            status.setTotalRequests(status.getTotalRequests() + 1);
        }
    }
    
    /**
     * 报告密钥使用成功
     */
    public void reportSuccess(String apiKey) {
        ApiKeyStatus status = keyStatusMap.get(apiKey);
        if (status != null) {
            status.setSuccessRequests(status.getSuccessRequests() + 1);
            status.setFailureCount(0); // 重置失败计数
            log.debug("【密钥管理】密钥使用成功：{}...", maskApiKey(apiKey));
        }
    }
    
    /**
     * 报告密钥使用失败
     */
    public void reportFailure(String apiKey, Exception exception) {
        ApiKeyStatus status = keyStatusMap.get(apiKey);
        if (status != null) {
            status.setFailureCount(status.getFailureCount() + 1);
            status.setLastFailure(LocalDateTime.now());
            
            log.warn("【密钥管理】密钥使用失败：{}...，失败次数：{}，错误：{}", 
                    maskApiKey(apiKey), status.getFailureCount(), exception.getMessage());
            
            // 达到最大失败次数时禁用密钥
            if (status.getFailureCount() >= config.getMaxRetries()) {
                status.setAvailable(false);
                status.setDisabledUntil(LocalDateTime.now().plusSeconds(config.getDisableTimeout()));
                log.error("【密钥管理】密钥已禁用：{}...，禁用时间：{}秒", 
                        maskApiKey(apiKey), config.getDisableTimeout());
            }
        }
    }
    
    /**
     * 动态更新密钥列表
     */
    public void updateApiKeys(List<String> newKeys) {
        log.info("【密钥管理】更新API密钥列表，新密钥数量：{}", newKeys.size());
        
        // 移除不在新列表中的密钥
        keyStatusMap.entrySet().removeIf(entry -> {
            if (!newKeys.contains(entry.getKey())) {
                log.info("【密钥管理】移除密钥：{}...", maskApiKey(entry.getKey()));
                return true;
            }
            return false;
        });
        
        // 添加新密钥
        for (String key : newKeys) {
            if (!keyStatusMap.containsKey(key)) {
                keyStatusMap.put(key, new ApiKeyStatus(key, true, 0, null, null, null, 0, 0, 1));
                log.info("【密钥管理】添加新密钥：{}...", maskApiKey(key));
            }
        }
        
        // 更新配置
        config.setKeys(new ArrayList<>(newKeys));
        
        // 重置轮询索引
        currentIndex.set(0);
    }
    
    /**
     * 获取密钥状态统计
     */
    public Map<String, Object> getKeyStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        List<Map<String, Object>> keyStats = keyStatusMap.entrySet().stream()
            .map(entry -> {
                String key = entry.getKey();
                ApiKeyStatus status = entry.getValue();
                
                Map<String, Object> keyInfo = new HashMap<>();
                keyInfo.put("apiKey", maskApiKey(key));
                keyInfo.put("available", status.isAvailable());
                keyInfo.put("totalRequests", status.getTotalRequests());
                keyInfo.put("successRequests", status.getSuccessRequests());
                keyInfo.put("failureCount", status.getFailureCount());
                keyInfo.put("successRate", status.getTotalRequests() > 0 ? 
                    (double) status.getSuccessRequests() / status.getTotalRequests() : 0.0);
                keyInfo.put("lastUsed", status.getLastUsed());
                keyInfo.put("weight", status.getWeight());
                
                return keyInfo;
            })
            .collect(Collectors.toList());
        
        stats.put("keys", keyStats);
        stats.put("totalKeys", keyStatusMap.size());
        stats.put("availableKeys", getAvailableKeys().size());
        stats.put("strategy", config.getStrategy());
        
        return stats;
    }
    
    /**
     * 启动健康检查调度器
     */
    private void startHealthCheckScheduler() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::performHealthCheck, 
            config.getHealthCheckInterval(), 
            config.getHealthCheckInterval(), 
            TimeUnit.SECONDS);
    }
    
    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        log.debug("【密钥管理】开始执行健康检查");
        
        for (Map.Entry<String, ApiKeyStatus> entry : keyStatusMap.entrySet()) {
            String apiKey = entry.getKey();
            ApiKeyStatus status = entry.getValue();
            
            // 检查长时间未使用的密钥
            if (status.getLastUsed() != null && 
                status.getLastUsed().isBefore(LocalDateTime.now().minusHours(1))) {
                // 可以在这里执行简单的API测试
                log.debug("【密钥管理】密钥长时间未使用：{}...", maskApiKey(apiKey));
            }
        }
    }
    
    /**
     * 掩码API密钥用于日志
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 10) {
            return "***";
        }
        return apiKey.substring(0, 10) + "...";
    }
}
```

### 3. 配置管理接口

#### 3.1 GeminiApiKeyController.java
```java
@RestController
@RequestMapping("/api/admin/gemini")
@Slf4j
public class GeminiApiKeyController {

    private final GeminiApiKeyManager keyManager;

    @Autowired
    public GeminiApiKeyController(GeminiApiKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    /**
     * 获取密钥统计信息
     */
    @GetMapping("/keys/stats")
    public Result<Map<String, Object>> getKeyStatistics() {
        Map<String, Object> stats = keyManager.getKeyStatistics();
        return Result.success(stats, "获取密钥统计成功");
    }

    /**
     * 更新API密钥列表
     */
    @PostMapping("/keys/update")
    public Result<String> updateApiKeys(@RequestBody UpdateKeysRequest request) {
        try {
            keyManager.updateApiKeys(request.getKeys());
            log.info("【管理接口】API密钥列表更新成功，新密钥数量：{}", request.getKeys().size());
            return Result.success("API密钥列表更新成功");
        } catch (Exception e) {
            log.error("【管理接口】更新API密钥列表失败：{}", e.getMessage(), e);
            return Result.failed("更新失败：" + e.getMessage());
        }
    }

    /**
     * 手动禁用密钥
     */
    @PostMapping("/keys/{keyIndex}/disable")
    public Result<String> disableKey(@PathVariable int keyIndex) {
        // 实现密钥禁用逻辑
        return Result.success("密钥已禁用");
    }

    /**
     * 手动启用密钥
     */
    @PostMapping("/keys/{keyIndex}/enable")
    public Result<String> enableKey(@PathVariable int keyIndex) {
        // 实现密钥启用逻辑
        return Result.success("密钥已启用");
    }

    @Data
    public static class UpdateKeysRequest {
        private List<String> keys;
    }
}
```

### 4. 修改GeminiNetworkServiceImpl

#### 4.1 集成密钥轮询管理器
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiNetworkServiceImpl implements GeminiNetworkService {

    @Qualifier("geminiRestTemplate")
    private final RestTemplate restTemplate;

    private final GeminiConfig geminiConfig;
    private final GeminiResponseParser responseParser;
    private final GeminiApiKeyManager apiKeyManager; // 新增

    // 硬编码的Gemini模型参数
    private static final String GEMINI_MODEL_CODE = "gemini-2.5-flash";

    @Override
    public GeminiGroundingResponse queryWithGrounding(String userContext) {
        if (userContext == null || userContext.trim().isEmpty()) {
            log.warn("【Gemini联网】用户上下文为空，返回空结果");
            return null;
        }

        String currentApiKey = null;
        try {
            log.info("【Gemini联网】开始处理联网查询，用户上下文长度：{}", userContext.length());

            // 获取下一个可用的API密钥
            currentApiKey = apiKeyManager.getNextApiKey();

            // 构建请求体，基于gemini-test.py的实现
            Map<String, Object> requestBody = buildGeminiRequest(userContext);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

            // 构建请求URL，使用轮询的API密钥
            String requestUrl = String.format("%s/models/%s:generateContent?key=%s",
                    geminiConfig.getBaseUrl(), GEMINI_MODEL_CODE, currentApiKey);

            log.info("【Gemini联网】发送请求到：{}，模型：{}", geminiConfig.getBaseUrl(), GEMINI_MODEL_CODE);

            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.POST,
                    httpEntity,
                    String.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                log.error("【Gemini联网】请求失败，状态码：{}，响应：{}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Gemini API请求失败：" + response.getStatusCode());
            }

            log.info("【Gemini联网】API调用成功，开始解析响应");

            // 报告成功
            apiKeyManager.reportSuccess(currentApiKey);

            // 解析响应
            return responseParser.parseResponse(response.getBody());

        } catch (Exception e) {
            log.error("【Gemini联网】查询失败：{}", e.getMessage(), e);

            // 报告失败
            if (currentApiKey != null) {
                apiKeyManager.reportFailure(currentApiKey, e);
            }

            return null;
        }
    }

    // ... 其他方法保持不变
}
```

## 配置文件更新

### application.yml
```yaml
gemini:
  api:
    # 多个API密钥列表
    keys:
      - your-google-api-key
      - your-google-api-key2  # 示例第二个密钥
      - your-google-api-key3  # 示例第三个密钥

    # 轮询策略：ROUND_ROBIN, RANDOM, WEIGHTED
    strategy: ROUND_ROBIN

    # 基础URL
    base-url: https://generativelanguage.googleapis.com/v1beta

    # 请求超时时间（毫秒）
    timeout: 10000

    # 密钥健康检查间隔（秒）
    health-check-interval: 300

    # 密钥失败重试次数
    max-retries: 3

    # 密钥禁用时间（秒）
    disable-timeout: 600
```

## 使用方式

### 1. 动态更新密钥
```bash
# 通过API更新密钥列表
curl -X POST http://localhost:8080/api/admin/gemini/keys/update \
  -H "Content-Type: application/json" \
  -d '{
    "keys": [
      "your-google-api-key",
      "your-google-api-key2",
      "your-google-api-key3"
    ]
  }'
```

### 2. 查看密钥统计
```bash
# 获取密钥使用统计
curl http://localhost:8080/api/admin/gemini/keys/stats
```

### 3. 响应示例
```json
{
  "code": 200,
  "message": "获取密钥统计成功",
  "data": {
    "keys": [
      {
        "apiKey": "AIzaSyB8sq...",
        "available": true,
        "totalRequests": 150,
        "successRequests": 148,
        "failureCount": 0,
        "successRate": 0.9867,
        "lastUsed": "2025-07-16T03:10:30",
        "weight": 1
      }
    ],
    "totalKeys": 3,
    "availableKeys": 3,
    "strategy": "ROUND_ROBIN"
  }
}
```

## 轮询策略说明

### 1. ROUND_ROBIN（轮询）
- 按顺序依次使用每个可用密钥
- 适合负载均衡场景
- 默认策略

### 2. RANDOM（随机）
- 随机选择可用密钥
- 适合避免热点问题
- 分布更均匀

### 3. WEIGHTED（加权）
- 根据密钥权重选择
- 可以为高质量密钥分配更高权重
- 适合差异化服务

## 监控和告警

### 1. 密钥状态监控
- 实时监控每个密钥的可用状态
- 记录成功率和失败次数
- 自动禁用和恢复机制

### 2. 健康检查
- 定期检查密钥状态
- 长时间未使用密钥的提醒
- 可扩展为主动API测试

### 3. 统计信息
- 总请求数和成功率
- 每个密钥的使用频率
- 失败模式分析

## 方案优势

1. **高可用性**：多密钥轮询，单个密钥失败不影响服务
2. **动态管理**：支持运行时添加/删除密钥
3. **智能恢复**：失败密钥自动禁用和重新启用
4. **多种策略**：支持轮询、随机、加权等策略
5. **监控友好**：提供详细的使用统计和健康状态
6. **配置灵活**：支持配置文件和API两种更新方式
7. **故障隔离**：单个密钥问题不影响整体服务
8. **负载均衡**：合理分配请求到不同密钥

## 实施建议

### 阶段1：基础实施
1. 实现GeminiApiKeyManager核心功能
2. 集成到现有GeminiNetworkServiceImpl
3. 配置多个测试密钥

### 阶段2：管理接口
1. 实现GeminiApiKeyController
2. 添加密钥统计和更新接口
3. 完善错误处理

### 阶段3：监控优化
1. 添加详细的监控指标
2. 实现主动健康检查
3. 集成告警机制

### 阶段4：高级功能
1. 支持密钥配额管理
2. 实现智能权重调整
3. 添加密钥性能分析

## 风险评估

### 潜在风险
1. **配置复杂性**：多密钥管理增加配置复杂度
2. **状态同步**：多实例部署时的状态同步问题
3. **密钥泄露**：多密钥增加泄露风险

### 缓解措施
1. **简化配置**：提供默认配置和向导
2. **状态管理**：使用Redis等外部存储同步状态
3. **安全措施**：密钥加密存储和访问控制

## 总结

本方案提供了一个完整的Gemini API密钥轮询管理系统，支持动态密钥管理、多种轮询策略、智能故障恢复和详细监控。通过这个系统，可以显著提高API调用的可靠性和可用性，同时提供灵活的管理能力。

**实施状态：📋 设计完成**
**开发状态：⏳ 待实施**
**优先级：🔥 高**
