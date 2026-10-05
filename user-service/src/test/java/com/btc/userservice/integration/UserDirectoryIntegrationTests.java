package com.btc.userservice.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Employee directory search, role filter and head counts (administrators only). */
@SpringBootTest
@AutoConfigureMockMvc
class UserDirectoryIntegrationTests {

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final String admin = TestJwt.bearer(1, "ADMIN");

    @BeforeEach
    void seed() {
        user("Asha Rao", "asha@example.com", "EMPLOYEE");
        user("Bram Admin", "bram@example.com", "ADMIN");
        user("Chen 100%", "chen@example.com", "EMPLOYEE");
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void searchMatchesNameOrEmailCaseInsensitivelyAndTreatsWildcardsLiterally() throws Exception {
        mockMvc.perform(get("/users?q=ASHA").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "1")).andExpect(jsonPath("$[0].name").value("Asha Rao"));
        mockMvc.perform(get("/users?q=bram@").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "1"));
        mockMvc.perform(get("/users").param("q", "%").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "1")).andExpect(jsonPath("$[0].name").value("Chen 100%"));
        mockMvc.perform(get("/users?role=employee&sort=name,asc").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "2"));
        mockMvc.perform(get("/users?role=OWNER").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void responsesNeverExposePasswordHashes() throws Exception {
        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void statsAreForAdministratorsOnly() throws Exception {
        mockMvc.perform(get("/users/stats").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.admins").value(1))
                .andExpect(jsonPath("$.employees").value(2))
                .andExpect(jsonPath("$.joinedLast30Days").value(3));
        mockMvc.perform(get("/users/stats").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(2, "EMPLOYEE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/users?q=asha").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(2, "EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    private void user(String name, String email, String role) {
        userRepository.save(User.builder().name(name).email(email).phone("1").role(role).passwordHash("$2a$10$hash").build());
    }
}
