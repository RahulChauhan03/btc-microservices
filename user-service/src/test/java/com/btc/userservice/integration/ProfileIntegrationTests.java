package com.btc.userservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.userservice.audit.AuditLogRepository;
import com.btc.userservice.entity.User;
import com.btc.userservice.repository.UserRepository;
import com.btc.userservice.security.TestJwt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Profile & settings: own data only, permitted fields only, password change with current-password proof. */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileIntegrationTests {

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private long id;
    private String token;

    @BeforeEach
    void seed() {
        id = userRepository.save(User.builder().name("Erin").email("erin@example.com").phone("1").role("EMPLOYEE")
                .passwordHash(passwordEncoder.encode("current-pass-1")).build()).getId();
        token = TestJwt.bearer(id, "EMPLOYEE");
    }

    @AfterEach
    void cleanUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void meIsTheTokenSubjectAndOnlyNameAndPhoneChange() throws Exception {
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(put("/users/me").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Erin Q \",\"phone\":\"555\",\"email\":\"evil@example.com\",\"role\":\"ADMIN\",\"id\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Erin Q"))
                .andExpect(jsonPath("$.email").value("erin@example.com"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
        mockMvc.perform(put("/users/me").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"phone\":\"555\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeRequiresTheCurrentPasswordAndIsAudited() throws Exception {
        change("wrong-pass-1", "brand-new-pass", "brand-new-pass").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        change("current-pass-1", "brand-new-pass", "different-pass").andExpect(status().isBadRequest());
        change("current-pass-1", "short", "short").andExpect(status().isBadRequest());
        change("current-pass-1", "current-pass-1", "current-pass-1").andExpect(status().isBadRequest());
        change("current-pass-1", "brand-new-pass", "brand-new-pass").andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches("brand-new-pass", userRepository.findById(id).orElseThrow().getPasswordHash())).isTrue();
        assertThat(auditLogRepository.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getAction()).isEqualTo("USER_PASSWORD_CHANGED");
            assertThat(log.getSummary()).doesNotContain("brand-new-pass").doesNotContain("current-pass-1");
        });
    }

    @Test
    void ownPasswordCannotBeChangedWithoutTheCurrentOneViaTheGenericEndpoint() throws Exception {
        mockMvc.perform(put("/users/" + id).header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Erin\",\"email\":\"erin@example.com\",\"phone\":\"1\",\"password\":\"hijacked-pass\"}"))
                .andExpect(status().isBadRequest());
        assertThat(passwordEncoder.matches("current-pass-1", userRepository.findById(id).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void repeatedWrongAttemptsAreRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            change("wrong-pass-" + i, "brand-new-pass", "brand-new-pass").andExpect(status().isBadRequest());
        }
        change("current-pass-1", "brand-new-pass", "brand-new-pass").andExpect(status().isTooManyRequests());
    }

    private ResultActions change(String current, String next, String confirm) throws Exception {
        return mockMvc.perform(post("/users/me/password").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}".formatted(current, next, confirm)));
    }
}
