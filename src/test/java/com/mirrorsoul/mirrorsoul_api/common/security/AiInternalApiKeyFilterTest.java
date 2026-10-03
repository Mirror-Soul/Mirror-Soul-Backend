package com.mirrorsoul.mirrorsoul_api.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mirrorsoul.mirrorsoul_api.config.AiInternalApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class AiInternalApiKeyFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsMatchingApiKey() throws Exception {
        AiInternalApiKeyFilter filter = filterWithKey("test-secret");
        MockHttpServletRequest request = internalRequest();
        request.addHeader(AiInternalApiKeyFilter.API_KEY_HEADER, "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMissingOrInvalidApiKey() throws Exception {
        AiInternalApiKeyFilter filter = filterWithKey("test-secret");
        MockHttpServletRequest request = internalRequest();
        request.addHeader(AiInternalApiKeyFilter.API_KEY_HEADER, "wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INTERNAL_4010");
    }

    @Test
    void rejectsRequestWhenServerKeyIsNotConfigured() throws Exception {
        AiInternalApiKeyFilter filter = filterWithKey("");
        MockHttpServletRequest request = internalRequest();
        request.addHeader(AiInternalApiKeyFilter.API_KEY_HEADER, "some-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("INTERNAL_5030");
    }

    @Test
    void ignoresNonInternalAiPath() throws Exception {
        AiInternalApiKeyFilter filter = filterWithKey("test-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/calls/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private AiInternalApiKeyFilter filterWithKey(String key) {
        return new AiInternalApiKeyFilter(new AiInternalApiProperties(key), objectMapper);
    }

    private MockHttpServletRequest internalRequest() {
        return new MockHttpServletRequest("GET", "/internal/ai/calls/1/context");
    }
}
