package com.btc.userservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.userservice.config.JwtConfig;
import com.btc.userservice.config.SecurityConfig;
import com.btc.userservice.entity.User;
import com.btc.userservice.repository.UserRepository;
import com.btc.userservice.security.TestJwt;
import com.btc.userservice.service.impl.UserServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Paging contract and the standard error body for malformed or invalid requests. */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtConfig.class, UserServiceImpl.class})
class ApiReliabilityWebTests {

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void listReturnsArrayWithTotalCountHeader() throws Exception {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(
                List.of(User.builder().id(3L).name("C").build()), PageRequest.of(1, 1), 5));

        mockMvc.perform(get("/users?page=1&size=1&sort=name,desc").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(1, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(3));
    }

    @Test
    void invalidPagingParametersReturnStandardError() throws Exception {
        String admin = TestJwt.bearer(1, "ADMIN");
        for (String query : new String[] {"size=0", "size=501", "page=-1", "sort=passwordHash", "page=abc"}) {
            mockMvc.perform(get("/users?" + query).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.path").value("/users"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        verifyNoInteractions(userRepository);
    }

    @Test
    void malformedJsonReturnsStandardErrorWithoutInternals() throws Exception {
        mockMvc.perform(post("/users").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"x\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));
    }

    @Test
    void unsupportedMethodAndMediaTypeAreReportedConsistently() throws Exception {
        String admin = TestJwt.bearer(1, "ADMIN");
        mockMvc.perform(post("/users/5").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(post("/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }
}
