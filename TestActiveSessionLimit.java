import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试用户活跃对话限制功能的核心逻辑
 * 模拟ChatInterruptServiceImpl的hasActiveSession方法
 */
public class TestActiveSessionLimit {
    
    // 模拟活跃会话映射
    private static ConcurrentHashMap<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    
    /**
     * 会话信息类
     */
    private static class SessionInfo {
        private final Long userId;
        private final LocalDateTime startTime;
        
        public SessionInfo(Long userId) {
            this.userId = userId;
            this.startTime = LocalDateTime.now();
        }
        
        public Long getUserId() {
            return userId;
        }
        
        public boolean isTimeout() {
            // 会话超时时间：30分钟
            return startTime.plusMinutes(30).isBefore(LocalDateTime.now());
        }
        
        public LocalDateTime getStartTime() {
            return startTime;
        }
    }
    
    /**
     * 检查用户是否已有活跃会话
     */
    public static boolean hasActiveSession(Long userId) {
        if (userId == null) {
            return false;
        }
        
        // 遍历所有活跃会话，检查是否有该用户的会话
        for (SessionInfo sessionInfo : activeSessions.values()) {
            if (sessionInfo != null && userId.equals(sessionInfo.getUserId())) {
                // 检查会话是否超时，如果超时则不算活跃
                if (!sessionInfo.isTimeout()) {
                    System.out.println("用户" + userId + "已有活跃会话");
                    return true;
                }
            }
        }
        
        System.out.println("用户" + userId + "没有活跃会话");
        return false;
    }
    
    /**
     * 注册活跃会话
     */
    public static void registerActiveSession(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            System.out.println("注册活跃会话失败：sessionId或userId为空");
            return;
        }
        
        activeSessions.put(sessionId, new SessionInfo(userId));
        System.out.println("注册活跃聊天会话：sessionId=" + sessionId + ", userId=" + userId + 
                          ", 当前活跃会话数=" + activeSessions.size());
    }
    
    /**
     * 标记会话完成并清理
     */
    public static void markSessionCompleted(String sessionId) {
        if (sessionId == null) {
            return;
        }
        
        SessionInfo sessionInfo = activeSessions.remove(sessionId);
        
        if (sessionInfo != null) {
            System.out.println("聊天会话已完成并清理：sessionId=" + sessionId + 
                              ", userId=" + sessionInfo.getUserId() + 
                              ", 剩余活跃会话数=" + activeSessions.size());
        }
    }
    
    /**
     * 获取活跃会话数量
     */
    public static int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    /**
     * 清理超时会话
     */
    public static void cleanupTimeoutSessions() {
        int cleanedCount = 0;
        for (String sessionId : activeSessions.keySet()) {
            SessionInfo sessionInfo = activeSessions.get(sessionId);
            if (sessionInfo != null && sessionInfo.isTimeout()) {
                activeSessions.remove(sessionId);
                cleanedCount++;
                System.out.println("清理超时会话：sessionId=" + sessionId + 
                                  ", userId=" + sessionInfo.getUserId());
            }
        }
        
        if (cleanedCount > 0) {
            System.out.println("清理超时会话完成：清理数量=" + cleanedCount + 
                              ", 剩余活跃会话数=" + activeSessions.size());
        }
    }
    
    /**
     * 测试方法
     */
    public static void main(String[] args) {
        System.out.println("=== 开始测试用户活跃对话限制功能 ===\n");
        
        // 测试1: 用户首次发起对话
        System.out.println("测试1: 用户首次发起对话");
        Long userId1 = 1L;
        boolean hasActive1 = hasActiveSession(userId1);
        System.out.println("结果: " + (hasActive1 ? "❌ 失败 - 不应该有活跃会话" : "✅ 通过 - 没有活跃会话"));
        System.out.println();
        
        // 测试2: 注册活跃会话后再次检查
        System.out.println("测试2: 注册活跃会话后再次检查");
        String sessionId1 = "session_001";
        registerActiveSession(sessionId1, userId1);
        boolean hasActive2 = hasActiveSession(userId1);
        System.out.println("结果: " + (hasActive2 ? "✅ 通过 - 检测到活跃会话" : "❌ 失败 - 应该检测到活跃会话"));
        System.out.println();
        
        // 测试3: 同一用户尝试发起第二个对话
        System.out.println("测试3: 同一用户尝试发起第二个对话");
        boolean hasActive3 = hasActiveSession(userId1);
        if (hasActive3) {
            System.out.println("✅ 通过 - 正确阻止了第二个对话，返回错误: Currently chatting, please end the chat and try again");
        } else {
            System.out.println("❌ 失败 - 应该阻止第二个对话");
        }
        System.out.println();
        
        // 测试4: 不同用户的隔离
        System.out.println("测试4: 不同用户的隔离");
        Long userId2 = 2L;
        boolean hasActive4 = hasActiveSession(userId2);
        System.out.println("结果: " + (hasActive4 ? "❌ 失败 - 不同用户不应该受影响" : "✅ 通过 - 不同用户没有活跃会话"));
        System.out.println();
        
        // 测试5: 注册第二个用户的会话
        System.out.println("测试5: 注册第二个用户的会话");
        String sessionId2 = "session_002";
        registerActiveSession(sessionId2, userId2);
        boolean hasActive5a = hasActiveSession(userId1);
        boolean hasActive5b = hasActiveSession(userId2);
        System.out.println("用户1活跃状态: " + hasActive5a);
        System.out.println("用户2活跃状态: " + hasActive5b);
        System.out.println("结果: " + (hasActive5a && hasActive5b ? "✅ 通过 - 两个用户都有各自的活跃会话" : "❌ 失败 - 用户隔离有问题"));
        System.out.println();
        
        // 测试6: 会话完成后的状态清理
        System.out.println("测试6: 会话完成后的状态清理");
        markSessionCompleted(sessionId1);
        boolean hasActive6 = hasActiveSession(userId1);
        System.out.println("结果: " + (hasActive6 ? "❌ 失败 - 会话完成后应该清理状态" : "✅ 通过 - 会话完成后状态已清理"));
        System.out.println();
        
        // 测试7: 清理后用户可以重新发起对话
        System.out.println("测试7: 清理后用户可以重新发起对话");
        String sessionId3 = "session_003";
        registerActiveSession(sessionId3, userId1);
        boolean hasActive7 = hasActiveSession(userId1);
        System.out.println("结果: " + (hasActive7 ? "✅ 通过 - 用户可以重新发起对话" : "❌ 失败 - 用户应该能重新发起对话"));
        System.out.println();
        
        // 测试8: 空值处理
        System.out.println("测试8: 空值处理");
        boolean hasActiveNull = hasActiveSession(null);
        System.out.println("结果: " + (hasActiveNull ? "❌ 失败 - null用户ID应该返回false" : "✅ 通过 - null用户ID正确处理"));
        System.out.println();
        
        // 测试9: 当前活跃会话统计
        System.out.println("测试9: 当前活跃会话统计");
        int activeCount = getActiveSessionCount();
        System.out.println("当前活跃会话数: " + activeCount);
        System.out.println("结果: " + (activeCount == 2 ? "✅ 通过 - 活跃会话数正确" : "❌ 失败 - 活跃会话数不正确"));
        System.out.println();
        
        // 清理所有会话
        System.out.println("清理所有测试会话...");
        markSessionCompleted(sessionId2);
        markSessionCompleted(sessionId3);
        
        // 最终统计
        System.out.println("\n=== 测试完成 ===");
        System.out.println("最终活跃会话数: " + getActiveSessionCount());
        System.out.println("测试总结: 请检查上述各项测试结果");
        
        // 模拟超时测试（需要等待30分钟才能真正测试，这里只是演示逻辑）
        System.out.println("\n注意: 超时清理功能需要等待30分钟才能测试，当前只验证了核心逻辑。");
    }
}
