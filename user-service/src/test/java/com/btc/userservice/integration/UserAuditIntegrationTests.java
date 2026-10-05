package com.btc.userservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.userservice.audit.AuditLog;
import com.btc.userservice.audit.AuditLogRepository;
import com.btc.userservice.repository.UserRepository;
import com.btc.userservice.security.TestJwt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** User management actions leave server-side audit records without personal data or secrets. */
@SpringBootTest
@AutoConfigureMockMvc
class UserAuditIntegrationTests {

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
    private ObjectMapper objectMapper;

    private final String admin = TestJwt.bearer(9001, "ADMIN");

    @AfterEach
    void cleanUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void creationRoleChangePasswordAndDeletionAreAudited() throws Exception {
        String created = mockMvc.perform(post("/users").header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dee\",\"email\":\"dee@example.com\",\"phone\":\"1\",\"password\":\"secret-pass-1\",\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(put("/users/" + id).header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dee\",\"email\":\"dee@example.com\",\"phone\":\"1\",\"password\":\"another-pass-2\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/users/" + id).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNoContent());

        assertThat(auditLogRepository.findAll()).extracting(AuditLog::getAction)
                .containsExactlyInAnyOrder("USER_CREATED", "USER_PASSWORD_CHANGED", "USER_ROLE_CHANGED", "USER_DELETED");
        assertThat(auditLogRepository.findAll()).allSatisfy(log -> {
            assertThat(log.getActorId()).isEqualTo(9001L);
            assertThat(log.getTargetId()).isEqualTo(id);
            assertThat(log.getSummary()).doesNotContain("dee@example.com").doesNotContain("secret-pass-1").doesNotContain("another-pass-2");
        });
        mockMvc.perform(get("/users/audit-logs?action=user_role_changed").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].summary").value("Role of user #" + id + " changed from EMPLOYEE to ADMIN"))
                .andExpect(jsonPath("$[0].source").value("users"));
        mockMvc.perform(get("/users/audit-logs?actorId=9001&size=2").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "4")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void auditLogsAreAdministratorOnlyAndReadOnly() throws Exception {
        mockMvc.perform(get("/users/audit-logs").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(2, "EMPLOYEE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/users").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(2, "EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        // No write API exists: these requests are refused and nothing is changed.
        mockMvc.perform(post("/users/audit-logs").header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().is4xxClientError());
        mockMvc.perform(delete("/users/audit-logs").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().is4xxClientError());
        assertThat(auditLogRepository.count()).isZero();
    }
}
