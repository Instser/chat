# 07-11 10:05 Edit-resend操作Token计算修复报告

## 问题描述

在使用edit-resend方法进行聊天时，发现保存到数据库中的用户消息Token数量始终为0，导致Token统计不准确。

## 问题分析

### 根本原因

在`ChatServiceImpl.processEditResendChat`方法中，创建新用户消息时缺少Token计算和设置步骤：

**问题代码**:
```java
// 保存新的用户原始消息（不包含文件内容）
ChatMessage newUserMessage = new ChatMessage();
newUserMessage.setMessageId(java.util.UUID.randomUUID().toString());
newUserMessage.setSessionId(sessionId);
newUserMessage.setRole("user");
newUserMessage.setContent(newContent); // 保存原始内容
newUserMessage.setMessageType("text");
newUserMessage.setHasAttachments(hasAttachments ? 1 : 0);
newUserMessage.setHasWebSearch(0);
newUserMessage.setUserId(userId);
newUserMessage.setModelId(chatSession.getModelId());
newUserMessage.setStatus(1);
newUserMessage.setCreatedTime(LocalDateTime.now());
newUserMessage.setUpdateTime(LocalDateTime.now());
// 缺少 setTokenCount() 调用！
```

### 对比正常流程

在正常聊天流程中，`ChatMessageServiceImpl.sendMessageWithTokenInfo`方法会正确计算Token：

```java
// 计算Token数量
Integer tokenCount = calculateTokenCount(content);
// ...
message.setTokenCount(tokenCount);
```

## 修复方案

### 修复内容

**文件**: `src/main/java/com/instser/chat/service/serviceimpl/ChatServiceImpl.java`  
**方法**: `processEditResendChat`  
**行号**: 3970-3986

**修复后的代码**:
```java
// 保存新的用户原始消息（不包含文件内容）
ChatMessage newUserMessage = new ChatMessage();
newUserMessage.setMessageId(java.util.UUID.randomUUID().toString());
newUserMessage.setSessionId(sessionId);
newUserMessage.setRole("user");
newUserMessage.setContent(newContent); // 保存原始内容
newUserMessage.setMessageType("text");
newUserMessage.setHasAttachments(hasAttachments ? 1 : 0);
newUserMessage.setHasWebSearch(0);
newUserMessage.setUserId(userId);
newUserMessage.setModelId(chatSession.getModelId());
// 计算并设置Token数量
int tokenCount = calculateTokenCount(newContent);
newUserMessage.setTokenCount(tokenCount);
newUserMessage.setStatus(1);
newUserMessage.setCreatedTime(LocalDateTime.now());
newUserMessage.setUpdateTime(LocalDateTime.now());
```

### 日志优化

同时优化了相关日志信息，增加Token数量的记录：

**修复前**:
```java
log.info("成功保存新的用户消息，messageId：{}，附件：{}", 
    newUserMessage.getMessageId(), hasAttachments ? "有" : "无");
```

**修复后**:
```java
log.info("成功保存新的用户消息，messageId：{}，附件：{}，Token数：{}", 
    newUserMessage.getMessageId(), hasAttachments ? "有" : "无", tokenCount);
```

## Token计算逻辑

使用项目中统一的Token计算方法`calculateTokenCount()`：

```java
// Token计算规则：
// - 中文字符：每个字符约1.2个token
// - 英文字母：每4个字符约1个token
// - 数字：每4个字符约1个token
// - 标点符号：每3个字符约1个token
// - 空白字符：每8个字符约1个token
int totalTokens = (int) Math.ceil(
    chineseCount * 1.2 +
    englishCount / 4.0 +
    digitCount / 4.0 +
    punctuationCount / 3.0 +
    whitespaceCount / 8.0
);

// 最小值为1，避免返回0
return Math.max(1, totalTokens);
```

## 修复效果

### 修复前
- Edit-resend操作创建的用户消息Token数量为0
- 数据库中token_count字段值为NULL或0
- Token统计不准确

### 修复后
- Edit-resend操作正确计算用户消息Token数量
- 数据库中正确保存token_count字段值
- Token统计准确，与正常聊天流程保持一致

## 测试建议

1. **功能测试**：
   - 执行edit-resend操作
   - 检查数据库中新创建的用户消息token_count字段
   - 验证Token数量不为0且合理

2. **对比测试**：
   - 对比正常聊天和edit-resend的Token计算结果
   - 确保计算逻辑一致

3. **日志验证**：
   - 查看日志中Token数量的记录
   - 确认日志信息完整

## 总结

此次修复解决了edit-resend操作中Token计算缺失的问题，确保了：

1. **数据一致性**：edit-resend和正常聊天使用相同的Token计算逻辑
2. **统计准确性**：用户消息的Token数量正确保存到数据库
3. **日志完整性**：增加了Token数量的日志记录，便于调试和监控

修复后的edit-resend功能能够正确计算和保存用户消息的Token数量，与系统其他部分保持一致。
