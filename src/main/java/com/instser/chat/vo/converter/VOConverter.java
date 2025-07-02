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
import com.instser.chat.service.FileUploadService;
import com.instser.chat.service.ExchangeRateService;
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
     * AI模型实体转VO
     */
    public static AiModelVO toAiModelVO(AiModel aiModel) {
        if (aiModel == null) {
            return null;
        }
        AiModelVO aiModelVO = new AiModelVO();
        BeanUtils.copyProperties(aiModel, aiModelVO);
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
        if (packageEntity == null) {
            return null;
        }
        FrontendPackageVO frontendPackageVO = new FrontendPackageVO();

        // 基本信息映射
        frontendPackageVO.setId(packageEntity.getId().intValue());
        frontendPackageVO.setName(packageEntity.getPackageName());
        frontendPackageVO.setDescription(packageEntity.getDescription());
        frontendPackageVO.setCurrency(currency);

        // 智能价格转换逻辑
        frontendPackageVO.setPrice(calculatePrice(packageEntity, currency, exchangeRateService));

        // 直接映射有效期天数
        frontendPackageVO.setPeriod(packageEntity.getValidityDays());

        // 解析功能特性 (暂时使用模型权限作为功能特性)
        if (packageEntity.getModelPermissions() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                List<String> permissions = mapper.readValue(packageEntity.getModelPermissions(),
                    new TypeReference<List<String>>() {});
                frontendPackageVO.setFeatures(permissions);
            } catch (Exception e) {
                // 如果解析失败，提供默认功能特性
                frontendPackageVO.setFeatures(getDefaultFeatures(packageEntity.getPackageType()));
            }
        } else {
            frontendPackageVO.setFeatures(getDefaultFeatures(packageEntity.getPackageType()));
        }

        return frontendPackageVO;
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
        if (packages == null) {
            return null;
        }

        return packages.stream()
                .map(pkg -> toFrontendPackageVO(pkg, currency, exchangeRateService))
                .collect(Collectors.toList());
    }





    /**
     * 智能价格计算方法
     * 根据请求的货币类型和汇率服务进行价格转换
     */
    private static java.math.BigDecimal calculatePrice(Package packageEntity, String currency, ExchangeRateService exchangeRateService) {
        if (packageEntity == null || currency == null) {
            return java.math.BigDecimal.ZERO;
        }

        // 如果请求CNY，直接返回CNY价格
        if ("CNY".equalsIgnoreCase(currency)) {
            return packageEntity.getPriceCny() != null ? packageEntity.getPriceCny() : java.math.BigDecimal.ZERO;
        }

        // 如果请求USD，直接返回USD价格
        if ("USD".equalsIgnoreCase(currency)) {
            return packageEntity.getPriceUsd() != null ? packageEntity.getPriceUsd() : java.math.BigDecimal.ZERO;
        }

        // 对于其他货币，需要使用汇率服务进行转换
        if (exchangeRateService != null) {
            try {
                // 优先使用USD价格进行转换（因为USD是国际通用货币）
                java.math.BigDecimal basePrice = packageEntity.getPriceUsd();
                if (basePrice != null && basePrice.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    java.math.BigDecimal exchangeRate = exchangeRateService.getExchangeRate("USD", currency);
                    if (exchangeRate != null && exchangeRate.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        return basePrice.multiply(exchangeRate).setScale(2, java.math.RoundingMode.HALF_UP);
                    }
                }

                // 如果USD转换失败，尝试使用CNY价格进行转换
                java.math.BigDecimal cnyPrice = packageEntity.getPriceCny();
                if (cnyPrice != null && cnyPrice.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    java.math.BigDecimal exchangeRate = exchangeRateService.getExchangeRate("CNY", currency);
                    if (exchangeRate != null && exchangeRate.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        return cnyPrice.multiply(exchangeRate).setScale(2, java.math.RoundingMode.HALF_UP);
                    }
                }
            } catch (Exception e) {
                // 汇率转换失败时的日志记录（这里不能使用log，因为是静态方法）
                System.err.println("汇率转换失败: " + e.getMessage());
            }
        }

        // 如果汇率服务不可用或转换失败，返回CNY价格作为默认值
        return packageEntity.getPriceCny() != null ? packageEntity.getPriceCny() : java.math.BigDecimal.ZERO;
    }

    /**
     * 获取默认功能特性
     */
    private static List<String> getDefaultFeatures(String packageType) {
        List<String> features = new ArrayList<>();
        if (packageType == null) {
            return features;
        }

        switch (packageType.toLowerCase()) {
            case "basic":
                features.add("Basic AI models");
                features.add("Limited messages");
                features.add("Standard support");
                break;
            case "premium":
                features.add("Advanced AI models");
                features.add("Unlimited messages");
                features.add("Priority support");
                features.add("Chat history export");
                break;
            case "enterprise":
                features.add("All AI models");
                features.add("Unlimited messages");
                features.add("24/7 support");
                features.add("Custom integrations");
                features.add("Team management");
                break;
            default:
                features.add("Standard features");
                break;
        }
        return features;
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
