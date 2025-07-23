# 07-06 13:45 - Tavily Extract功能扩展

## 更新时间
2025年7月6日 13:45 (北京时间)

## 版本标识
v1.1.0-websearch-extract

## 功能概述
在现有Tavily搜索功能基础上，新增Tavily Extract内容提取功能，实现从搜索结果URL中获取详细内容的能力。

## 新增文件

### 实体类 (Entity)
1. **TavilyExtractRequest.java** - Tavily Extract请求实体
   - 支持URL数组输入
   - 可配置图片包含、网站图标、提取深度、输出格式等参数
   - 默认输出Markdown格式

2. **TavilyExtractResult.java** - 单个提取结果实体
   - 包含URL、原始内容、图片列表、网站图标等信息
   - 支持Markdown格式的详细内容

3. **TavilyExtractResponse.java** - 完整提取响应实体
   - 包含成功结果列表、失败结果列表、响应时间
   - 支持批量URL处理结果统计

### 服务层扩展
1. **WebSearchService.java** - 扩展联网搜索服务接口
   - 新增extract方法，支持URL数组和自定义请求参数
   - 保持向后兼容性

2. **WebSearchServiceImpl.java** - 扩展服务实现
   - 实现Tavily Extract API调用
   - 完整的异常处理和日志记录
   - 支持批量URL内容提取

### 综合服务
1. **WebSearchExtractService.java** - 搜索和提取综合服务接口
   - 提供搜索+提取的一站式服务
   - 支持自定义搜索结果数量和提取数量
   - 内置结果类WebSearchExtractResult

2. **WebSearchExtractServiceImpl.java** - 综合服务实现
   - 先执行搜索获取URL列表
   - 自动提取前N个结果的详细内容
   - 智能参数验证和调整

## 技术特点

### API集成
- **Tavily Extract API**: https://api.tavily.com/extract
- **认证方式**: Bearer Token认证（复用搜索API Token）
- **请求格式**: JSON格式，支持URL数组
- **响应格式**: 包含原始内容、图片、网站图标等详细信息

### 配置参数
- **URLs**: 支持多个URL同时提取
- **include_images**: 是否包含图片（默认false）
- **include_favicon**: 是否包含网站图标（默认false）
- **extract_depth**: 提取深度（默认basic）
- **format**: 输出格式（默认markdown）

### 错误处理
- **参数验证**: URL数组非空检查
- **批量处理**: 支持部分成功、部分失败的场景
- **失败统计**: 记录失败的URL和原因
- **超时处理**: 网络请求异常捕获

## 使用示例

### 单独使用Extract功能
```java
@Autowired
private WebSearchService webSearchService;

// 提取单个URL内容
String[] urls = {"https://example.com"};
TavilyExtractResponse response = webSearchService.extract(urls);

// 自定义提取参数
TavilyExtractRequest request = TavilyExtractRequest.builder()
    .urls(new String[]{"https://example1.com", "https://example2.com"})
    .includeImages(true)
    .includeFavicon(true)
    .extractDepth("advanced")
    .format("markdown")
    .build();
TavilyExtractResponse response = webSearchService.extract(request);
```

### 使用综合搜索+提取服务
```java
@Autowired
private WebSearchExtractService webSearchExtractService;

// 搜索并提取（使用默认参数：搜索10个结果，提取前3个）
WebSearchExtractResult result = webSearchExtractService.searchAndExtract("Spring Boot tutorial");

// 自定义参数搜索并提取
WebSearchExtractResult result = webSearchExtractService.searchAndExtract(
    "AI technology trends", 
    20,  // 最大搜索结果数
    5    // 提取详细内容的数量
);

// 获取搜索和提取结果
TavilySearchResponse searchResponse = result.getSearchResponse();
TavilyExtractResponse extractResponse = result.getExtractResponse();
```

## 测试覆盖

### 单元测试
1. **WebSearchServiceTest** - 扩展现有测试
   - Extract功能成功场景测试
   - 参数验证异常测试
   - 网络异常处理测试

2. **WebSearchExtractServiceTest** - 新增综合服务测试
   - 搜索+提取成功场景
   - 无搜索结果处理
   - 参数验证和边界条件
   - 提取数量自动调整

### 测试场景
- ✅ 正常搜索和提取流程
- ✅ 空搜索结果处理
- ✅ 无效URL处理
- ✅ 参数验证异常
- ✅ 网络异常处理
- ✅ 批量URL处理

## 架构优势

### 模块化设计
- **独立功能**: Extract功能可单独使用
- **组合服务**: 提供搜索+提取的一站式服务
- **向后兼容**: 不影响现有搜索功能

### 灵活配置
- **参数可选**: 所有Extract参数都有合理默认值
- **批量处理**: 支持多URL同时提取
- **格式选择**: 支持多种输出格式

### 错误容错
- **部分失败**: 支持批量处理中的部分成功
- **智能调整**: 自动调整不合理的参数组合
- **详细日志**: 完整的操作日志和错误信息

## 后续计划
1. **控制器集成**: 创建REST API端点暴露功能
2. **AI聊天集成**: 将详细内容整合到AI对话中
3. **缓存优化**: 添加URL内容缓存机制
4. **性能优化**: 并发处理多个URL提取
5. **内容处理**: 添加内容清理和格式化功能

## 注意事项
- Extract功能依赖搜索功能的基础设施
- 大量URL提取可能影响API配额
- 建议根据实际需求调整提取数量
- 内容提取结果较大，注意内存使用
