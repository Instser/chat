# GitHub OAuth用户名处理优化

**文档创建时间**: 2025年07月18日 22:30  
**版本**: v1.0  
**作者**: Augment Agent  

## 问题描述

在GitHub OAuth登录时发现，OAuth绑定记录中的Name字段是正确的（如"Instser"），但在user表中新建的关联用户的name字段却变成了"user"。

## 问题分析

### 原因定位

1. **GitHub API返回的字段**：
   - `login`: 用户的唯一标识符，不可变（如"Instser"）
   - `name`: 用户的显示名称，可变（可能为null或包含特殊字符）

2. **原始代码问题**：
   ```java
   // 原始逻辑：优先使用name，如果为null则使用login
   userInfo.setName(jsonNode.has("name") && !jsonNode.get("name").isNull() ?
       jsonNode.get("name").asText() : jsonNode.get("login").asText());
   ```

3. **用户名清理问题**：
   ```java
   // 原始清理逻辑：移除所有非英文字符
   String cleanName = baseName.replaceAll("[^a-zA-Z0-9_]", "");
   if (cleanName.isEmpty()) {
       cleanName = "user"; // 问题所在：如果name包含中文等字符，清理后变空
   }
   ```

## 解决方案

### 1. 优化字段选择策略

**修改前**：
```java
userInfo.setName(jsonNode.has("name") && !jsonNode.get("name").isNull() ?
    jsonNode.get("name").asText() : jsonNode.get("login").asText());
```

**修改后**：
```java
// GitHub用户名处理：login是不可变的用户标识，name是可变的显示名称
String githubLogin = jsonNode.get("login").asText();
String githubName = jsonNode.has("name") && !jsonNode.get("name").isNull() ? 
    jsonNode.get("name").asText() : null;

// OAuth绑定记录中保存login（不可变），用于生成系统用户名
userInfo.setName(githubLogin);
```

### 2. 优化用户名清理逻辑

**修改前**：
```java
private String generateUniqueUsername(String baseName) {
    if (baseName == null || baseName.trim().isEmpty()) {
        baseName = "user";
    }

    // 清理用户名，只保留字母数字和下划线
    String cleanName = baseName.replaceAll("[^a-zA-Z0-9_]", "");
    if (cleanName.isEmpty()) {
        cleanName = "user"; // 问题：直接变成user
    }
    // ...
}
```

**修改后**：
```java
private String cleanUsername(String baseName) {
    if (baseName == null || baseName.trim().isEmpty()) {
        return "user";
    }

    // 1. GitHub login通常只包含字母、数字、连字符和下划线，将连字符转为下划线
    String cleanName = baseName.replace("-", "_").replaceAll("[^a-zA-Z0-9_]", "");
    
    // 2. 如果清理后为空（极少情况），使用默认策略
    if (cleanName.isEmpty()) {
        cleanName = "user_" + System.currentTimeMillis() % 10000;
    }

    // 3. 确保用户名长度合理（3-30字符）
    if (cleanName.length() > 30) {
        cleanName = cleanName.substring(0, 30);
    } else if (cleanName.length() < 3) {
        cleanName = cleanName + "_" + System.currentTimeMillis() % 1000;
    }

    // 4. 确保不以数字开头
    if (cleanName.matches("^[0-9].*")) {
        cleanName = "u_" + cleanName;
    }

    return cleanName;
}
```

## 技术优势

### 1. 数据一致性
- **OAuth记录**：保存GitHub的`login`（不可变标识符）
- **用户表**：基于`login`生成稳定的用户名
- **避免冲突**：`login`本身就符合用户名规范

### 2. 稳定性提升
- **不可变性**：GitHub `login`不会改变，确保数据稳定
- **规范性**：GitHub `login`只包含字母、数字、连字符，易于处理
- **唯一性**：GitHub保证`login`的全局唯一性

### 3. 用户体验
- **可预测**：用户名基于GitHub用户名生成，用户容易理解
- **一致性**：多次登录生成的用户名保持一致
- **可读性**：保留原始用户名的可读性

## 测试用例

### 1. 正常GitHub用户名
```
GitHub login: "Instser"
生成用户名: "Instser"
```

### 2. 包含连字符的用户名
```
GitHub login: "user-name"
生成用户名: "user_name"
```

### 3. 纯数字开头的用户名
```
GitHub login: "123user"
生成用户名: "u_123user"
```

### 4. 用户名冲突处理
```
GitHub login: "testuser"
已存在用户名: "testuser"
生成用户名: "testuser_1"
```

## 日志增强

添加了详细的调试日志，便于问题排查：

```java
log.debug("【GitHub用户信息】login: {}, name: {}, OAuth记录保存login", githubLogin, githubName);
log.debug("【用户名生成】原始名称: {}, 清理后: {}", baseName, cleanName);
log.debug("【用户名生成】最终用户名: {}", username);
```

## 总结

通过这次优化，我们解决了GitHub OAuth登录时用户名变成"user"的问题：

1. **根本原因**：使用GitHub的`name`字段（可能包含特殊字符）而不是`login`字段
2. **解决方案**：改用GitHub的`login`字段作为用户名基础
3. **额外优化**：改进用户名清理逻辑，增加调试日志
4. **长期效益**：提高数据稳定性和用户体验

现在GitHub OAuth登录的用户将获得基于其GitHub用户名的稳定、可读的系统用户名。
