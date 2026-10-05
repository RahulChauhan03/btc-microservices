package com.btc.notificationservice.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.notificationservice.repository.NotificationRepository;
import com.btc.notificationservice.security.TestJwt;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;

/** Event intake, per-user visibility and read state through HTTP against a real database. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractNotificationIntegration {

    private static final long EMPLOYEE = 10;
    private static final long OTHER = 20;
    private static final long ADMIN = 1;
    private static final long OTHER_ADMIN = 2;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
    }

    @Test
    void onlyServicesMayRecordEventsAndRepeatsAreStoredOnce() throws Exception {
        String event = event(UUID.randomUUID().toString(), "USER", EMPLOYEE, ADMIN, "CLAIM_APPROVED");

        record(event, null).andExpect(status().isUnauthorized());
        record(event, TestJwt.bearer(ADMIN, "ADMIN")).andExpect(status().isForbidden());
        record(event, TestJwt.service()).andExpect(status().isCreated());
        record(event, TestJwt.service()).andExpect(status().isOk());
        record(event(UUID.randomUUID().toString(), "USER", null, ADMIN, "CLAIM_APPROVED"), TestJwt.service())
                .andExpect(status().isBadRequest());
        record("{\"eventId\":\"x\",\"audience\":\"EVERYONE\",\"type\":\"X\",\"title\":\"t\",\"message\":\"m\"}", TestJwt.service())
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/notifications").header(HttpHeaders.AUTHORIZATION, user(EMPLOYEE)))
                .andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    void usersSeeOnlyTheirOwnAndAdministratorsSeeAdminNoticesExceptTheirOwnActions() throws Exception {
        recordOk(event(UUID.randomUUID().toString(), "USER", EMPLOYEE, ADMIN, "CLAIM_APPROVED"));
        recordOk(event(UUID.randomUUID().toString(), "USER", OTHER, ADMIN, "CLAIM_REJECTED"));
        recordOk(event(UUID.randomUUID().toString(), "ADMINS", null, EMPLOYEE, "CLAIM_SUBMITTED"));
        recordOk(event(UUID.randomUUID().toString(), "ADMINS", null, OTHER_ADMIN, "CLAIM_SUBMITTED"));

        mockMvc.perform(get("/notifications").header(HttpHeaders.AUTHORIZATION, user(EMPLOYEE)))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].type").value("CLAIM_APPROVED"))
                .andExpect(jsonPath("$[0].read").value(false));
        mockMvc.perform(get("/notifications").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(OTHER_ADMIN, "ADMIN")))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].type").value("CLAIM_SUBMITTED"));
        mockMvc.perform(get("/notifications").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(ADMIN, "ADMIN")))
                .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    void readStateIsPerUserAndOthersCannotTouchMyNotifications() throws Exception {
        recordOk(event(UUID.randomUUID().toString(), "USER", EMPLOYEE, ADMIN, "CLAIM_APPROVED"));
        recordOk(event(UUID.randomUUID().toString(), "USER", EMPLOYEE, ADMIN, "CLAIM_REJECTED"));
        recordOk(event(UUID.randomUUID().toString(), "ADMINS", null, EMPLOYEE, "CLAIM_SUBMITTED"));
        long mine = notificationRepository.findAll().stream().filter(n -> "CLAIM_APPROVED".equals(n.getType()))
                .findFirst().orElseThrow().getId();
        long adminNotice = notificationRepository.findAll().stream().filter(n -> "ADMINS".equals(n.getAudience()))
                .findFirst().orElseThrow().getId();

        unread(user(EMPLOYEE), 2);
        mockMvc.perform(post("/notifications/" + mine + "/read").header(HttpHeaders.AUTHORIZATION, user(OTHER)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/notifications/" + adminNotice + "/read").header(HttpHeaders.AUTHORIZATION, user(EMPLOYEE)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/notifications/" + mine + "/read").header(HttpHeaders.AUTHORIZATION, user(EMPLOYEE)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/notifications/" + mine + "/read").header(HttpHeaders.AUTHORIZATION, user(EMPLOYEE)))
                .andExpect(status().isNoContent());
        unread(user(EMPLOYEE), 1);
        mockMvc.perform(get("/notifications?unreadOnly=true").header(HttpHeaders.AUTHORIZATION, user(EMPLOYEE)))
                .andExpect(header().string("X-Total-Count", "1")).andExpect(jsonPath("$[0].type").value("CLAIM_REJECTED"));

        mockMvc.perform(post("/notifications/read-all").header(HttpHeaders.AUTHORIZATION, user(EMPLOYEE)))
                .andExpect(jsonPath("$.updated").value(1));
        unread(user(EMPLOYEE), 0);
        unread(TestJwt.bearer(ADMIN, "ADMIN"), 1);
        mockMvc.perform(post("/notifications/read-all").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(ADMIN, "ADMIN")))
                .andExpect(jsonPath("$.updated").value(1));
        unread(TestJwt.bearer(ADMIN, "ADMIN"), 0);
        unread(TestJwt.bearer(OTHER_ADMIN, "ADMIN"), 1);
    }

    @Test
    void listsArePagedNewestFirst() throws Exception {
        for (int i = 0; i < 3; i++) {
            recordOk(event(UUID.randomUUID().toString(), "USER", EMPLOYEE, ADMIN, "TYPE_" + (char) ('A' + i)));
        }
        mockMvc.perform(get("/notifications?size=2").header(HttpHeaders.AUTHORIZATION, user(EMPLOYEE)))
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("TYPE_C"));
        mockMvc.perform(get("/notifications?size=500").header(HttpHeaders.AUTHORIZATION, user(EMPLOYEE)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
    }

    private void unread(String token, int expected) throws Exception {
        mockMvc.perform(get("/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.count").value(expected));
    }

    private ResultActions record(String body, String token) throws Exception {
        var request = post("/notifications/internal/events").contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(token == null ? request : request.header(HttpHeaders.AUTHORIZATION, token));
    }

    private void recordOk(String body) throws Exception {
        record(body, TestJwt.service()).andExpect(status().isCreated());
        Thread.sleep(5); // distinct created_at for ordering assertions
    }

    private static String user(long id) {
        return TestJwt.bearer(id, "EMPLOYEE");
    }

    private static String event(String id, String audience, Long recipient, Long actor, String type) {
        return """
                {"eventId":"%s","audience":"%s","recipientId":%s,"actorId":%s,"type":"%s",
                 "title":"Claim update","message":"Claim C-1 was updated.","link":"/claims"}"""
                .formatted(id, audience, recipient, actor, type);
    }
}
