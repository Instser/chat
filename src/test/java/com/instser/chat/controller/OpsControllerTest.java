package com.instser.chat.controller;

import com.instser.chat.service.AiModelService;
import com.instser.chat.service.GeminiNetworkService;
import com.instser.chat.service.ManagementApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpsControllerTest {

    private ManagementApiKeyService apiKeyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        apiKeyService = mock(ManagementApiKeyService.class);

        BuildProperties buildProperties = mock(BuildProperties.class);
        when(buildProperties.getVersion()).thenReturn("0.0.1-SNAPSHOT");
        when(buildProperties.getGroup()).thenReturn("com.instser");
        when(buildProperties.getArtifact()).thenReturn("chat");
        when(buildProperties.getTime()).thenReturn(Instant.now());

        ApplicationContext ctx = mock(ApplicationContext.class);
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{});
        when(ctx.getEnvironment()).thenReturn(env);

        DataSource dataSource = mock(DataSource.class);
        RedisConnectionFactory redisConnFactory = mock(RedisConnectionFactory.class);
        GeminiNetworkService geminiNetworkService = mock(GeminiNetworkService.class);
        AiModelService aiModelService = mock(AiModelService.class);
        RestTemplate restTemplate = mock(RestTemplate.class);

        OpsController controller = new OpsController(
                apiKeyService, buildProperties, ctx,
                dataSource, redisConnFactory, geminiNetworkService,
                aiModelService, restTemplate);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldRejectVersionWithoutValidApiKey() throws Exception {
        when(apiKeyService.validateRequest(any())).thenReturn(false);

        mockMvc.perform(get("/ops/version"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("访问被拒绝：需要有效的API密钥"));
    }

    @Test
    void shouldRejectHealthWithoutValidApiKey() throws Exception {
        when(apiKeyService.validateRequest(any())).thenReturn(false);

        mockMvc.perform(get("/ops/health/full"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void shouldRejectAnalyzeErrorWithoutValidApiKey() throws Exception {
        when(apiKeyService.validateRequest(any())).thenReturn(false);

        mockMvc.perform(post("/ops/ai/analyze-error")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"errorLog\":\"test error\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnVersionInfoWithValidApiKey() throws Exception {
        when(apiKeyService.validateRequest(any())).thenReturn(true);

        mockMvc.perform(get("/ops/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.application").value("jinyu-chat"))
                .andExpect(jsonPath("$.data.version").value("0.0.1-SNAPSHOT"))
                .andExpect(jsonPath("$.data.groupId").value("com.instser"));
    }

    @Test
    void shouldReturnHealthStatusWithValidApiKey() throws Exception {
        when(apiKeyService.validateRequest(any())).thenReturn(true);

        RedisConnection redisConn = mock(RedisConnection.class);
        when(redisConn.ping()).thenReturn("PONG");
        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        when(redisFactory.getConnection()).thenReturn(redisConn);

        GeminiNetworkService geminiService = mock(GeminiNetworkService.class);
        when(geminiService.isServiceAvailable()).thenReturn(true);

        DataSource ds = mock(DataSource.class);

        // recreate controller with mockable DataSource for Redis health
        OpsController controller = new OpsController(
                apiKeyService,
                mock(BuildProperties.class),
                mock(ApplicationContext.class),
                ds,
                redisFactory,
                geminiService,
                mock(AiModelService.class),
                mock(RestTemplate.class));
        // set fields manually to avoid BuildProperties NPE
        java.lang.reflect.Field bpField = controller.getClass().getDeclaredField("buildProperties");
        bpField.setAccessible(true);
        BuildProperties bp = mock(BuildProperties.class);
        when(bp.getVersion()).thenReturn("1.0");
        when(bp.getGroup()).thenReturn("g");
        when(bp.getArtifact()).thenReturn("a");
        when(bp.getTime()).thenReturn(Instant.now());
        bpField.set(controller, bp);

        MockMvc healthMvc = MockMvcBuilders.standaloneSetup(controller).build();

        healthMvc.perform(get("/ops/health/full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall").value("UP"))
                .andExpect(jsonPath("$.data.components.redis.status").value("UP"));
    }

    @Test
    void shouldReportDegradedWhenRedisIsDown() throws Exception {
        when(apiKeyService.validateRequest(any())).thenReturn(true);

        RedisConnectionFactory brokenRedis = mock(RedisConnectionFactory.class);
        when(brokenRedis.getConnection()).thenThrow(new RuntimeException("Connection refused"));

        OpsController controller = new OpsController(
                apiKeyService,
                mock(BuildProperties.class),
                mock(ApplicationContext.class),
                mock(DataSource.class),
                brokenRedis,
                mock(GeminiNetworkService.class),
                mock(AiModelService.class),
                mock(RestTemplate.class));

        MockMvc healthMvc = MockMvcBuilders.standaloneSetup(controller).build();

        healthMvc.perform(get("/ops/health/full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall").value("DEGRADED"))
                .andExpect(jsonPath("$.data.components.redis.status").value("DOWN"));
    }

    @Test
    void shouldReturnFailedWhenNoAiModelAvailable() throws Exception {
        when(apiKeyService.validateRequest(any())).thenReturn(true);

        AiModelService emptyModelService = mock(AiModelService.class);
        when(emptyModelService.getAvailableModels()).thenReturn(List.of());

        OpsController controller = new OpsController(
                apiKeyService,
                mock(BuildProperties.class),
                mock(ApplicationContext.class),
                mock(DataSource.class),
                mock(RedisConnectionFactory.class),
                mock(GeminiNetworkService.class),
                emptyModelService,
                mock(RestTemplate.class));

        MockMvc analyzeMvc = MockMvcBuilders.standaloneSetup(controller).build();

        analyzeMvc.perform(post("/ops/ai/analyze-error")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"errorLog\":\"NullPointerException at line 42\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("无可用的 AI 模型"));
    }
}
