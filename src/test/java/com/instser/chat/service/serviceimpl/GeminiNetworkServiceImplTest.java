package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.AiModel;
import com.instser.chat.entity.GeminiGroundingResponse;
import com.instser.chat.service.AiModelService;
import com.instser.chat.service.GeminiResponseParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiNetworkServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void queryWithGrounding_shouldUseOfficialGeminiRestHeadersAndToolName() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        AiModelService aiModelService = mock(AiModelService.class);
        GeminiResponseParser responseParser = mock(GeminiResponseParser.class);

        AiModel model = new AiModel();
        model.setBaseUrl("https://generativelanguage.googleapis.com/v1beta");
        model.setModelCode("gemini-2.5-flash");
        model.setApiKey("test-api-key");

        when(aiModelService.getById(6L)).thenReturn(model);
        when(responseParser.parseResponse(anyString())).thenReturn(new GeminiGroundingResponse());

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-api-key"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(contentTypeIsJson())
                .andExpect(bodyUsesOfficialGoogleSearchTool())
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        GeminiNetworkServiceImpl service = new GeminiNetworkServiceImpl(restTemplate, aiModelService, responseParser);

        service.queryWithGrounding("test query");

        server.verify();
    }

    private static org.springframework.test.web.client.RequestMatcher headerDoesNotExist(String headerName) {
        return request -> {
            if (request.getHeaders().containsKey(headerName)) {
                throw new AssertionError("Header should not exist: " + headerName);
            }
        };
    }

    private org.springframework.test.web.client.RequestMatcher contentTypeIsJson() {
        return request -> {
            MediaType contentType = request.getHeaders().getContentType();
            if (!MediaType.APPLICATION_JSON.includes(contentType)) {
                throw new AssertionError("Expected JSON content type but was: " + contentType);
            }
        };
    }

    private org.springframework.test.web.client.RequestMatcher bodyUsesOfficialGoogleSearchTool() {
        return request -> {
            String body = ((MockClientHttpRequest) request).getBodyAsString();
            JsonNode root = objectMapper.readTree(body);
            assertNotNull(root.get("contents"));
            assertNotNull(root.get("tools"));
            JsonNode firstTool = root.get("tools").get(0);
            assertNotNull(firstTool.get("google_search"));
        };
    }
}
