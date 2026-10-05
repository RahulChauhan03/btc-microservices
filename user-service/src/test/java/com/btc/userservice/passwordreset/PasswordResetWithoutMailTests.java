package com.btc.userservice.passwordreset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.userservice.security.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Without SMTP settings the feature fails safely (503 for every address) instead of pretending to send. */
@SpringBootTest(properties = "btc.password-reset.requests-per-client=3")
@AutoConfigureMockMvc
class PasswordResetWithoutMailTests {

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unconfiguredMailAnswers503ThenRateLimits() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/forgot-password").header("X-Forwarded-For", "198.51.100.7")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"anyone@example.com\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message")
                            .value("Password reset is temporarily unavailable. Please contact your administrator."));
        }
        mockMvc.perform(post("/auth/forgot-password").header("X-Forwarded-For", "198.51.100.7")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"anyone@example.com\"}"))
                .andExpect(status().isTooManyRequests());
        // Spoofing an earlier X-Forwarded-For hop does not escape the limit: the gateway-appended last hop counts.
        mockMvc.perform(post("/auth/forgot-password").header("X-Forwarded-For", "10.0.0.1, 198.51.100.7")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"anyone@example.com\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
