# 07-07 14:10 - Retry和Edit-Resend联网搜索功能完善

## 更新时间
2025年7月7日 14:10 (北京时间)

## 版本标识
v1.5.0-retry-editresend-network-support

## 功能概述
完善retry和edit-resend功能的联网搜索支持，使这两个功能也能根据network参数进入联网分支，实现与正常聊天一致的联网搜索体验。

## 问题背景
在之前的实现中，虽然正常聊天可以进入联网分支，但是retry和edit-resend功能没有进行联网检测，这两个方法中只是添加了注释但没有实际实现联网搜索逻辑。

## 核心修改

### 1. 控制器层扩展

#### ChatApiController新增方法
```java
/**
 * 提取并验证network参数
 */
private Boolean extractNetwork(Map<String, Object> request) {
    Object networkObj = request.get("network");
    if (networkObj == null) {
        return false; // 默认禁用网络搜索
    }

    if (networkObj instanceof Boolean) {
        return (Boolean) networkObj;
    } else if (networkObj instanceof String) {
        return "true".equalsIgnoreCase((String) networkObj) || "1".equals(networkObj);
    } else if (networkObj instanceof Number) {
        return ((Number) networkObj).intValue() == 1;
    }
    
    return false; // 默认禁用网络搜索
}
```

#### retry接口参数扩展
```java
// 提取参数 - 前端传入chatId，后端映射为sessionId
String chatId = (String) request.get("chatId");
String messageId = (String) request.get("messageId");
Long modelId = extractModelId(request, response);
Boolean network = extractNetwork(request); // 新增

// 调用ChatService处理重新生成逻辑（chatId实际对应sessionId）
chatService.processRetryChat(chatId, messageId, modelId, network, authentication, response);
```

#### edit-resend接口参数扩展
```java
// 提取参数 - 前端传入chatId，后端映射为sessionId
String chatId = (String) request.get("chatId");
String messageId = (String) request.get("messageId");
String newContent = (String) request.get("newContent");
String filesId = (String) request.get("filesId");
Long modelId = extractModelId(request, response);
Boolean network = extractNetwork(request); // 新增

// 调用ChatService处理编辑重发逻辑（chatId实际对应sessionId）
chatService.processEditResendChat(chatId, messageId, newContent, filesId, modelId, network, authentication, response);
```

### 2. 服务层接口扩展

#### ChatService接口修改
```java
/**
 * 处理重新生成AI回复请求
 *
 * @param sessionId 会话ID（前端传入的chatId参数）
 * @param messageId 要重新生成的AI消息ID
 * @param modelId 模型ID（可选，为null时使用会话原有模型）
 * @param network 是否启用网络搜索（可选，为null时默认禁用）
 * @param authentication 认证信息
 * @param response HTTP响应对象
 */
void processRetryChat(String sessionId, String messageId, Long modelId, Boolean network, Authentication authentication, HttpServletResponse response);

/**
 * 处理编辑用户消息并重新生成AI回复请求
 *
 * @param sessionId 会话ID（前端传入的chatId参数）
 * @param messageId 要编辑的用户消息ID
 * @param newContent 新的用户消息内容
 * @param filesId 文件ID列表（逗号分隔，可选）
 * @param modelId 模型ID（可选，为null时使用会话原有模型）
 * @param network 是否启用网络搜索（可选，为null时默认禁用）
 * @param authentication 认证信息
 * @param response HTTP响应对象
 */
void processEditResendChat(String sessionId, String messageId, String newContent, String filesId, Long modelId, Boolean network, Authentication authentication, HttpServletResponse response);
```

### 3. 服务层实现完善

#### retry方法联网搜索处理
```java
// 处理联网搜索内容（如果启用）
if (network != null && network) {
    // 提取用户查询内容（从最后一条用户消息中获取）
    String userQuery = extractUserQuery(messages);
    if (userQuery != null && !userQuery.trim().isEmpty()) {
        log.info("重试请求检测到联网搜索请求，开始处理联网内容，查询：{}", userQuery);
        List<Map<String, String>> processedMessages = processNetworkContent(messages, userQuery);
        messages = processedMessages;
        log.info("重试请求联网内容处理完成");
    } else {
        log.warn("重试请求联网搜索但无法提取用户查询内容");
    }
} else {
    log.debug("重试请求没有启用联网搜索");
}
```

#### edit-resend方法联网搜索处理
```java
// 处理联网搜索内容（如果启用）
if (network != null && network) {
    // 提取用户查询内容（从最后一条用户消息中获取）
    String userQuery = extractUserQuery(messages);
    if (userQuery != null && !userQuery.trim().isEmpty()) {
        log.info("编辑重发请求检测到联网搜索请求，开始处理联网内容，查询：{}", userQuery);
        List<Map<String, String>> processedMessages = processNetworkContent(messages, userQuery);
        messages = processedMessages;
        log.info("编辑重发请求联网内容处理完成");
    } else {
        log.warn("编辑重发请求联网搜索但无法提取用户查询内容");
    }
} else {
    log.debug("编辑重发请求没有启用联网搜索");
}
```

#### ChatRequestDTO参数设置
```java
// retry方法
retryRequest.setNetwork(network != null ? network : false); // 使用传入的network参数

// edit-resend方法
editResendRequest.setNetwork(network != null ? network : false); // 使用传入的network参数
```

## 技术特点

### 参数处理一致性
- **类型支持**: 支持Boolean、String、Number多种类型的network参数
- **默认值**: 未传递network参数时默认为false，保持向后兼容
- **空值处理**: 正确处理null值，避免空指针异常

### 联网搜索逻辑复用
- **方法复用**: 复用processNetworkContent和extractUserQuery方法
- **处理流程**: 与正常聊天保持一致的联网搜索处理流程
- **内容拼接**: 遵循相同的内容拼接顺序：用户内容 + 文件内容 + 联网内容

### 错误处理完善
- **优雅降级**: 联网搜索失败不影响基础功能
- **日志记录**: 完整的操作日志便于问题排查
- **异常处理**: 完善的异常处理机制

## 使用示例

### retry接口联网搜索
```json
{
  "chatId": "session-123",
  "messageId": "msg-456",
  "modelId": 1,
  "network": true
}
```

### edit-resend接口联网搜索
```json
{
  "chatId": "session-123",
  "messageId": "msg-456",
  "newContent": "请搜索最新的AI技术发展",
  "filesId": "file1,file2",
  "modelId": 1,
  "network": true
}
```

## 功能对比

### 修改前
- ✅ **正常聊天**: 支持联网搜索
- ❌ **retry**: 不支持联网搜索
- ❌ **edit-resend**: 不支持联网搜索
- ✅ **续写**: 继承原始请求设置

### 修改后
- ✅ **正常聊天**: 支持联网搜索
- ✅ **retry**: 支持联网搜索
- ✅ **edit-resend**: 支持联网搜索
- ✅ **续写**: 继承原始请求设置

## 设计理念

### 功能一致性
- **统一体验**: 所有聊天场景都支持联网搜索
- **参数统一**: 使用相同的network参数控制联网搜索
- **处理统一**: 使用相同的联网搜索处理逻辑

### 用户控制
- **灵活选择**: 用户可以在任何场景下选择是否启用联网搜索
- **场景适配**: 不同场景下的联网搜索都能正确工作
- **参数透明**: 联网搜索参数传递透明，便于调试

### 系统稳定性
- **向后兼容**: 不传递network参数时保持原有行为
- **错误隔离**: 联网搜索错误不影响基础聊天功能
- **性能考虑**: 按需启用联网搜索，不影响默认性能

## 后续优化

### 功能增强
1. **智能推荐**: 根据消息内容智能推荐是否启用联网搜索
2. **缓存优化**: 对相同查询的搜索结果进行缓存
3. **并发处理**: 优化联网搜索的并发处理能力

### 用户体验
1. **搜索状态**: 提供联网搜索的进度状态反馈
2. **结果预览**: 在AI回答前提供搜索结果预览
3. **来源标注**: 在AI回答中标注信息来源

## 注意事项

1. **API配额**: 联网搜索会消耗Tavily API配额，需要合理控制使用
2. **响应时间**: 联网搜索会增加总体响应时间
3. **内容质量**: 搜索结果质量依赖于网络内容和Tavily服务
4. **参数传递**: 前端需要正确传递network参数
5. **错误处理**: 需要完善的错误处理确保服务稳定性
