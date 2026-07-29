package com.instser.chat.vo.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.entity.FileUpload;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.User;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.ExchangeRateService;
import com.instser.chat.service.FileUploadService;

import com.instser.chat.vo.*;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * VO转换工具类
 *
 * @author instser
 * @since 2024-01-01
 */
public class VOConverter {

    /**
     * 用户实体转VO
     */
    public static UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 用户实体转VO（包含套餐信息）
     */
    public static UserVO toUserVO(User user, String packageName) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        userVO.setPackageName(packageName);
        return userVO;
    }

    /**
     * AI模型实体转VO
     */
    public static AiModelVO toAiModelVO(AiModel aiModel) {
        if (aiModel == null) {
            return null;
        }
        AiModelVO aiModelVO = new AiModelVO();
        // 仅拷贝前端可见字段；apiKey/baseUrl 不在 AiModelVO 中，避免误序列化泄露
        aiModelVO.setId(aiModel.getId());
        aiModelVO.setModelCode(aiModel.getModelCode());
        aiModelVO.setModelName(aiModel.getModelName());
        aiModelVO.setDescription(aiModel.getDescription());
        aiModelVO.setProvider(aiModel.getProvider());
        aiModelVO.setVersion(aiModel.getVersion());
        aiModelVO.setTokenLimit(aiModel.getTokenLimit());
        aiModelVO.setPricePer1kTokens(aiModel.getPricePer1kTokens());
        aiModelVO.setIsAvailable(aiModel.getIsAvailable());
        return aiModelVO;
    }

    /**
     * 聊天会话实体转VO
     */
    public static ChatSessionVO toChatSessionVO(ChatSession chatSession) {
        if (chatSession == null) {
            return null;
        }
        ChatSessionVO chatSessionVO = new ChatSessionVO();
        BeanUtils.copyProperties(chatSession, chatSessionVO);
        
        // 设置前端需要的字段
        chatSessionVO.setId(chatSession.getSessionId()); // 前端使用sessionId作为id
        chatSessionVO.setCreatedTime(chatSession.getCreatedTime()); // 统一使用createdTime
        chatSessionVO.setUpdateTime(chatSession.getUpdateTime()); // 统一使用updateTime
        
        return chatSessionVO;
    }

    /**
     * 聊天会话实体转VO（包含模型信息）
     */
    public static ChatSessionVO toChatSessionVO(ChatSession chatSession, AiModel aiModel) {
        ChatSessionVO chatSessionVO = toChatSessionVO(chatSession);
        if (chatSessionVO != null && aiModel != null) {
            chatSessionVO.setModel(toAiModelVO(aiModel));
        }
        return chatSessionVO;
    }

    /**
     * 聊天消息实体转VO
     */
    public static ChatMessageVO toChatMessageVO(ChatMessage chatMessage) {
        if (chatMessage == null) {
            return null;
        }
        ChatMessageVO chatMessageVO = new ChatMessageVO();
        BeanUtils.copyProperties(chatMessage, chatMessageVO);
        
        // 设置前端需要的字段
        chatMessageVO.setId(chatMessage.getMessageId()); // 前端使用messageId作为id
        chatMessageVO.setChatId(chatMessage.getSessionId()); // 前端使用chatId
        chatMessageVO.setCreatedTime(chatMessage.getCreatedTime()); // 统一使用createdTime
        chatMessageVO.setUpdateTime(chatMessage.getUpdateTime()); // 统一使用updateTime
        chatMessageVO.setHasAttachments(chatMessage.getHasAttachments() != null && chatMessage.getHasAttachments() > 0); // 设置附件标识
        
        return chatMessageVO;
    }

    /**
     * 聊天消息实体转VO（包含模型信息）
     */
    public static ChatMessageVO toChatMessageVO(ChatMessage chatMessage, AiModel aiModel) {
        ChatMessageVO chatMessageVO = toChatMessageVO(chatMessage);
        if (chatMessageVO != null && aiModel != null) {
            chatMessageVO.setModel(toAiModelVO(aiModel));
        }
        return chatMessageVO;
    }

    /**
     * 聊天消息实体转VO（包含文件名信息）
     */
    public static ChatMessageVO toChatMessageVO(ChatMessage chatMessage, FileUploadService fileUploadService) {
        ChatMessageVO chatMessageVO = toChatMessageVO(chatMessage);
        if (chatMessageVO != null && fileUploadService != null) {
            // 查询关联的文件并设置文件名列表
            List<FileUpload> files = fileUploadService.getByMessageId(chatMessage.getMessageId());
            if (files != null && !files.isEmpty()) {
                String filesName = files.stream()
                        .map(FileUpload::getFileName)
                        .collect(Collectors.joining(", "));
                chatMessageVO.setFilesName(filesName);
            }
        }
        return chatMessageVO;
    }

    /**
     * 聊天消息实体转VO（包含模型信息和文件名信息）
     */
    public static ChatMessageVO toChatMessageVO(ChatMessage chatMessage, AiModel aiModel, FileUploadService fileUploadService) {
        ChatMessageVO chatMessageVO = toChatMessageVO(chatMessage, fileUploadService);
        if (chatMessageVO != null && aiModel != null) {
            chatMessageVO.setModel(toAiModelVO(aiModel));
        }
        return chatMessageVO;
    }

    /**
     * 聊天消息实体转VO（包含文件名信息和网络搜索结果）
     */
    public static ChatMessageVO toChatMessageVO(ChatMessage chatMessage, FileUploadService fileUploadService,
                                              com.instser.chat.service.WebSearchContentService webSearchContentService) {
        ChatMessageVO chatMessageVO = toChatMessageVO(chatMessage, fileUploadService);
        if (chatMessageVO != null && webSearchContentService != null) {
            // 检查是否有网络搜索结果
            if (chatMessage.getHasWebSearch() != null && chatMessage.getHasWebSearch() == 1) {
                com.instser.chat.entity.WebSearchContent webSearchContent =
                    webSearchContentService.getByMessageId(chatMessage.getMessageId());
                if (webSearchContent != null && webSearchContent.getContent() != null) {
                    chatMessageVO.setNetworkResult(webSearchContent.getContent());
                }
            }
        }
        return chatMessageVO;
    }

    /**
     * 批量转换用户列表
     */
    public static List<UserVO> toUserVOList(List<User> users) {
        if (users == null) {
            return null;
        }
        return users.stream()
                .map(VOConverter::toUserVO)
                .collect(Collectors.toList());
    }

    /**
     * 批量转换AI模型列表
     */
    public static List<AiModelVO> toAiModelVOList(List<AiModel> aiModels) {
        if (aiModels == null) {
            return null;
        }
        return aiModels.stream()
                .map(VOConverter::toAiModelVO)
                .collect(Collectors.toList());
    }

    /**
     * 批量转换聊天会话列表
     */
    public static List<ChatSessionVO> toChatSessionVOList(List<ChatSession> chatSessions) {
        if (chatSessions == null) {
            return null;
        }
        return chatSessions.stream()
                .map(VOConverter::toChatSessionVO)
                .collect(Collectors.toList());
    }

    /**
     * 批量转换聊天消息列表
     */
    public static List<ChatMessageVO> toChatMessageVOList(List<ChatMessage> chatMessages) {
        if (chatMessages == null) {
            return null;
        }
        return chatMessages.stream()
                .map(VOConverter::toChatMessageVO)
                .collect(Collectors.toList());
    }

    /**
     * 批量转换聊天消息列表（包含文件名信息）
     */
    public static List<ChatMessageVO> toChatMessageVOList(List<ChatMessage> chatMessages, FileUploadService fileUploadService) {
        if (chatMessages == null) {
            return null;
        }
        return chatMessages.stream()
                .map(chatMessage -> toChatMessageVO(chatMessage, fileUploadService))
                .collect(Collectors.toList());
    }

    /**
     * 批量转换聊天消息列表（包含文件名信息和网络搜索结果）
     */
    public static List<ChatMessageVO> toChatMessageVOList(List<ChatMessage> chatMessages, FileUploadService fileUploadService,
                                                        com.instser.chat.service.WebSearchContentService webSearchContentService) {
        if (chatMessages == null) {
            return null;
        }
        return chatMessages.stream()
                .map(chatMessage -> toChatMessageVO(chatMessage, fileUploadService, webSearchContentService))
                .collect(Collectors.toList());
    }

    /**
     * 套餐实体转VO
     */
    public static PackageVO toPackageVO(Package packageEntity) {
        if (packageEntity == null) {
            return null;
        }
        PackageVO packageVO = new PackageVO();
        BeanUtils.copyProperties(packageEntity, packageVO);

        // 转换ID为字符串
        packageVO.setId(packageEntity.getId().toString());
        packageVO.setIsEnabled(packageEntity.getIsEnabled() == 1);

        // 使用兼容方法设置已删除的字段值
        packageVO.setChatCount(packageEntity.getChatCount());
        packageVO.setDailyChatLimit(packageEntity.getDailyChatLimit());

        // 设置价格字段（数据库存储USD价格）
        packageVO.setPriceUsd(packageEntity.getPrice());
        // 暂时将CNY价格设置为null，如需要可通过汇率转换获得
        packageVO.setPriceCny(null);

        // 设置优惠价格
        packageVO.setDiscountPrice(packageEntity.getDiscountPrice());

        // 解析模型权限JSON
        if (packageEntity.getModelPermissions() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                List<String> permissions = mapper.readValue(packageEntity.getModelPermissions(),
                    new TypeReference<List<String>>() {});
                packageVO.setModelPermissions(permissions);
            } catch (Exception e) {
                packageVO.setModelPermissions(new ArrayList<>());
            }
        }

        // 设置功能特性列表
        packageVO.setFeatures(packageEntity.getFeaturesList());

        return packageVO;
    }

    /**
     * 用户订单实体转VO
     */
    public static UserOrderVO toUserOrderVO(UserOrder userOrder) {
        if (userOrder == null) {
            return null;
        }
        UserOrderVO userOrderVO = new UserOrderVO();
        BeanUtils.copyProperties(userOrder, userOrderVO);

        // 转换ID为字符串
        userOrderVO.setId(userOrder.getId().toString());
        userOrderVO.setUserId(userOrder.getUserId().toString());
        userOrderVO.setPackageId(userOrder.getPackageId().toString());

        // 设置支付状态描述
        userOrderVO.setPaymentStatusDesc(getPaymentStatusDesc(userOrder.getPaymentStatus()));

        return userOrderVO;
    }

    /**
     * 用户订单实体转VO（包含套餐信息）
     */
    public static UserOrderVO toUserOrderVO(UserOrder userOrder, Package packageEntity) {
        UserOrderVO userOrderVO = toUserOrderVO(userOrder);
        if (userOrderVO != null && packageEntity != null) {
            userOrderVO.setPackageName(packageEntity.getPackageName());
        }
        return userOrderVO;
    }

    /**
     * 批量转换套餐列表
     */
    public static List<PackageVO> toPackageVOList(List<Package> packages) {
        if (packages == null) {
            return null;
        }
        return packages.stream()
                .map(VOConverter::toPackageVO)
                .collect(Collectors.toList());
    }

    /**
     * 批量转换订单列表
     */
    public static List<UserOrderVO> toUserOrderVOList(List<UserOrder> userOrders) {
        if (userOrders == null) {
            return null;
        }
        return userOrders.stream()
                .map(VOConverter::toUserOrderVO)
                .collect(Collectors.toList());
    }

    /**
     * 套餐实体转前端套餐VO（不使用汇率转换）
     */
    public static FrontendPackageVO toFrontendPackageVO(Package packageEntity, String currency) {
        return toFrontendPackageVO(packageEntity, currency, null);
    }

    /**
     * 套餐实体转前端套餐VO（支持汇率转换）
     */
    public static FrontendPackageVO toFrontendPackageVO(Package packageEntity, String currency, ExchangeRateService exchangeRateService) {
        return toFrontendPackageVO(packageEntity, currency, exchangeRateService, true);
    }

    /**
     * 套餐实体转前端套餐VO（支持汇率转换和优惠价格控制）
     */
    public static FrontendPackageVO toFrontendPackageVO(Package packageEntity, String currency, ExchangeRateService exchangeRateService, boolean showDiscountPrice) {
        if (packageEntity == null) {
            return null;
        }
        FrontendPackageVO frontendPackageVO = new FrontendPackageVO();

        // 基本信息映射
        frontendPackageVO.setId(packageEntity.getId().intValue());
        frontendPackageVO.setName(packageEntity.getPackageName());
        frontendPackageVO.setDescription(packageEntity.getDescription());
        frontendPackageVO.setCurrency(currency);

        // 根据请求货币进行价格转换
        frontendPackageVO.setPrice(calculatePrice(packageEntity, currency, exchangeRateService));

        // 根据showDiscountPrice参数决定是否设置优惠价格
        if (showDiscountPrice && packageEntity.getDiscountPrice() != null) {
            frontendPackageVO.setDiscountPrice(packageEntity.getDiscountPrice());
        } else {
            frontendPackageVO.setDiscountPrice(null);
        }

        // 直接映射有效期天数
        frontendPackageVO.setPeriod(packageEntity.getValidityDays());

        // 设置功能特性列表
        frontendPackageVO.setFeatures(packageEntity.getFeaturesList());

        return frontendPackageVO;
    }

    /**
     * 价格转换方法
     * 数据库存储USD价格，根据请求货币类型进行汇率转换
     */
    private static java.math.BigDecimal calculatePrice(Package packageEntity, String currency, ExchangeRateService exchangeRateService) {
        if (packageEntity == null || currency == null) {
            return java.math.BigDecimal.ZERO;
        }

        // 获取数据库中的USD价格
        java.math.BigDecimal usdPrice = packageEntity.getPrice();
        if (usdPrice == null || usdPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return java.math.BigDecimal.ZERO;
        }

        // 如果请求USD或未指定货币，直接返回数据库价格（美元）
        if ("USD".equalsIgnoreCase(currency) || currency == null) {
            return usdPrice;
        }

        // 如果请求CNY，进行汇率转换
        if ("CNY".equalsIgnoreCase(currency) && exchangeRateService != null) {
            try {
                return exchangeRateService.convertPrice(usdPrice, "USD", "CNY");
            } catch (Exception e) {
                // 汇率转换失败时返回USD价格
                System.err.println("汇率转换失败: " + e.getMessage());
                return usdPrice;
            }
        }

        // 其他货币或汇率服务不可用时，返回USD价格
        return usdPrice;
    }

    /**
     * 批量转换套餐列表为前端格式（不使用汇率转换）
     */
    public static List<FrontendPackageVO> toFrontendPackageVOList(List<Package> packages, String currency) {
        return toFrontendPackageVOList(packages, currency, null);
    }

    /**
     * 批量转换套餐列表为前端格式（支持汇率转换）
     */
    public static List<FrontendPackageVO> toFrontendPackageVOList(List<Package> packages, String currency, ExchangeRateService exchangeRateService) {
        return toFrontendPackageVOList(packages, currency, exchangeRateService, true);
    }

    /**
     * 批量转换套餐列表为前端格式（支持汇率转换和优惠价格控制）
     */
    public static List<FrontendPackageVO> toFrontendPackageVOList(List<Package> packages, String currency, ExchangeRateService exchangeRateService, boolean showDiscountPrice) {
        if (packages == null) {
            return null;
        }

        return packages.stream()
                .map(pkg -> toFrontendPackageVO(pkg, currency, exchangeRateService, showDiscountPrice))
                .collect(Collectors.toList());
    }







    /**
     * 获取支付状态描述
     */
    private static String getPaymentStatusDesc(Integer paymentStatus) {
        if (paymentStatus == null) {
            return "未知";
        }
        switch (paymentStatus) {
            case 0:
                return "未支付";
            case 1:
                return "已支付";
            case 2:
                return "已取消";
            case 3:
                return "已退款";
            default:
                return "未知";
        }
    }
}
