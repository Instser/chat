package com.instser.chat.vo.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.ChatMessage;
import com.instser.chat.entity.ChatSession;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.User;
import com.instser.chat.entity.UserOrder;
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
