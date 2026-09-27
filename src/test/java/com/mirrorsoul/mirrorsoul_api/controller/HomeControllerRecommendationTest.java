package com.mirrorsoul.mirrorsoul_api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.RecommendResDTO;
import com.mirrorsoul.mirrorsoul_api.service.HomeService;
import com.mirrorsoul.mirrorsoul_api.service.RecommendService;
import com.mirrorsoul.mirrorsoul_api.service.RecommendationDetailService;
import com.mirrorsoul.mirrorsoul_api.service.SwipeService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HomeControllerRecommendationTest {

    @Test
    void recommendReturnsOkWithEmptyRecommendations() throws Exception {
        RecommendService recommendService = mock(RecommendService.class);
        HomeController controller = new HomeController(
                recommendService,
                mock(HomeService.class),
                mock(SwipeService.class),
                mock(RecommendationDetailService.class)
        );
        UUID userUuid = UUID.randomUUID();
        CustomUserDetails currentUser = mock(CustomUserDetails.class);
        when(currentUser.getUuid()).thenReturn(userUuid);
        when(recommendService.getRecommendations(userUuid, PageRequest.of(0, 10)))
                .thenReturn(new RecommendResDTO.RecommendationSliceDTO(
                        List.of(),
                        0,
                        10,
                        false
                ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(currentUserResolver(currentUser))
                .build();

        mockMvc.perform(get("/home/recommend")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.recommendations").isEmpty())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(10))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    private HandlerMethodArgumentResolver currentUserResolver(CustomUserDetails currentUser) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == CustomUserDetails.class;
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return currentUser;
            }
        };
    }
}
