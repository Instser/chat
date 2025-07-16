package com.instser.chat.common.util;

import org.springframework.util.StringUtils;

/**
 * 日志工具类
 * 提供日志记录相关的工具方法，主要用于内容截断等
 *
 * @author instser
 * @since 2025-07-15
 */
public class LogUtils {

    /**
     * 内容截断
     * 对长文本内容进行截断，保留前15个和后15个字符，中间用...代替
     * 例如：这是一个很长的内容，需要进行截断处理 -> 这是一个很长的内...断处理
     *
     * @param content 原始内容
     * @return 截断后的内容
     */
    public static String truncateContent(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() <= 30) {
            return content;
        }
        return content.substring(0, 15) + "..." + content.substring(content.length() - 15);
    }

    /**
     * 格式化异常信息
     * 将异常信息格式化为简洁的形式
     *
     * @param e 异常对象
     * @return 格式化后的异常信息
     */
    public static String formatException(Throwable e) {
        if (e == null) {
            return "null";
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /**
     * 格式化请求参数
     * 将请求参数格式化为字符串，并对敏感字段进行处理
     *
     * @param params 请求参数
     * @return 格式化后的参数字符串
     */
    public static String formatRequestParams(Object params) {
        if (params == null) {
            return "null";
        }
        String paramsStr = params.toString();
        // 对包含敏感信息的字段进行处理
        paramsStr = paramsStr.replaceAll("\"password\":\"[^\"]*\"", "\"password\":\"******\"");
        paramsStr = paramsStr.replaceAll("\"token\":\"[^\"]*\"", "\"token\":\"******\"");
        return truncateContent(paramsStr);
    }

    /**
     * 判断字符串是否为空或空白
     *
     * @param str 字符串
     * @return 是否为空或空白
     */
    public static boolean isEmpty(String str) {
        return !StringUtils.hasText(str);
    }
}
