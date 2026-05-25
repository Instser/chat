package com.instser.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.component.ChatLimitCacheManager;
import com.instser.chat.service.ManagementApiKeyService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCacheControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRejectWhenBuiltinAccountCredentialInvalid() throws Exception {
        Object controller = createController();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/admin/cache/user-package/refresh-all")
                .header("X-API-Key", "valid-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "account": "wrong",
                          "password": "wrong"
                        }
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("访问被拒绝：管理员账号或密码错误"));
    }

    @Test
    void shouldRefreshSpecifiedUserCacheWhenCredentialValid() throws Exception {
        ManagementApiKeyService managementApiKeyService = Mockito.mock(ManagementApiKeyService.class);
        when(managementApiKeyService.validateRequest(any())).thenReturn(true);

        ChatLimitCacheManager chatLimitCacheManager = Mockito.mock(ChatLimitCacheManager.class);
        Object controller = createController(managementApiKeyService, chatLimitCacheManager);
        ReflectionTestUtils.setField(controller, "adminAccount", "configured-admin");
        ReflectionTestUtils.setField(controller, "adminPassword", "Configured.Pass.123");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/admin/cache/user-package/refresh-all")
                .header("X-API-Key", "valid-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshPayload("configured-admin", "Configured.Pass.123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("全量用户套餐与聊天次数缓存清理成功"));

        verify(chatLimitCacheManager).clearAllUserPackageCaches();
    }

    private Object createController() throws Exception {
        ManagementApiKeyService managementApiKeyService = Mockito.mock(ManagementApiKeyService.class);
        when(managementApiKeyService.validateRequest(any())).thenReturn(true);
        ChatLimitCacheManager chatLimitCacheManager = Mockito.mock(ChatLimitCacheManager.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        return createController(managementApiKeyService, chatLimitCacheManager, eventPublisher);
    }

    private Object createController(ManagementApiKeyService managementApiKeyService,
                                    ChatLimitCacheManager chatLimitCacheManager) throws Exception {
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        return createController(managementApiKeyService, chatLimitCacheManager, eventPublisher);
    }

    private Object createController(ManagementApiKeyService managementApiKeyService,
                                    ChatLimitCacheManager chatLimitCacheManager,
                                    ApplicationEventPublisher eventPublisher) throws Exception {
        try {
            Class<?> controllerClass = Class.forName("com.instser.chat.controller.AdminCacheController");
            Object controller = controllerClass
                    .getConstructor(ManagementApiKeyService.class, ChatLimitCacheManager.class, ApplicationEventPublisher.class)
                    .newInstance(managementApiKeyService, chatLimitCacheManager, eventPublisher);
            ReflectionTestUtils.setField(controller, "adminAccount", "aaaugment");
            ReflectionTestUtils.setField(controller, "adminPassword", "your-admin-password");
            return controller;
        } catch (ClassNotFoundException ex) {
            fail("缺少 AdminCacheController 管理接口");
            return null;
        }
    }

    private record RefreshPayload(String account, String password) {
    }
}
