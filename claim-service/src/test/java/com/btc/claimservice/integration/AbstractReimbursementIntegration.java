package com.btc.claimservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.claimservice.audit.AuditLogRepository;
import com.btc.claimservice.client.ExpenseLockClient;
import com.btc.claimservice.client.NotificationClient;
import com.btc.claimservice.entity.Claim;
import com.btc.claimservice.outbox.OutboxEventRepository;
import com.btc.claimservice.outbox.OutboxEventType;
import com.btc.claimservice.reimbursement.ReimbursementRepository;
import com.btc.claimservice.repository.ClaimRepository;
import com.btc.claimservice.security.TestJwt;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Reimbursement lifecycle, its authorization, and the claim-service audit trail, through HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractReimbursementIntegration {

    private static final long OWNER = 10L;
    private static final long OTHER = 20L;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ReimbursementRepository reimbursementRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private ExpenseLockClient expenseLockClient;

    @MockitoBean
    private NotificationClient notificationClient;

    private final String admin = TestJwt.bearer(1, "ADMIN");
    private final String owner = TestJwt.bearer(OWNER, "EMPLOYEE");

    @AfterEach
    void cleanUp() {
        reimbursementRepository.deleteAll();
        auditLogRepository.deleteAll();
        outboxEventRepository.deleteAll();
        claimRepository.deleteAll();
    }

    @Test
    void approvalOpensAPendingReimbursementAndIsAudited() throws Exception {
        long claim = submitted(OWNER, "42.50");
        approve(claim).andExpect(status().isOk());

        mockMvc.perform(get("/claims/" + claim + "/reimbursement").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(42.50))
                .andExpect(jsonPath("$.allowedNext.length()").value(2));
        mockMvc.perform(get("/claims/audit-logs?action=claim_approved").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].actorId").value(1))
                .andExpect(jsonPath("$[0].targetId").value(claim))
                .andExpect(jsonPath("$[0].source").value("claims"));

        long rejected = submitted(OWNER, "5.00");
        mockMvc.perform(post("/claims/" + rejected + "/reject").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk());
        mockMvc.perform(get("/claims/" + rejected + "/reimbursement").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isNotFound());
        assertThat(auditLogRepository.findAll()).extracting(log -> log.getAction())
                .containsExactlyInAnyOrder("CLAIM_APPROVED", "CLAIM_REJECTED");
    }

    @Test
    void lifecycleIsEnforcedAndPaidIsFinal() throws Exception {
        long claim = approved(OWNER);

        update(claim, admin, "PENDING", null, null).andExpect(status().isConflict());
        update(claim, admin, "PROCESSING", null, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
        update(claim, admin, "PENDING", null, null).andExpect(status().isConflict());
        update(claim, admin, "FAILED", null, null).andExpect(status().isOk());
        update(claim, admin, "PAID", null, null).andExpect(status().isConflict());
        update(claim, admin, "PROCESSING", null, null).andExpect(status().isOk());
        update(claim, admin, "PAID", null, "REF-1").andExpect(status().isBadRequest());
        update(claim, admin, "PAID", LocalDate.now().plusDays(1).toString(), "REF-1").andExpect(status().isBadRequest());
        update(claim, admin, "PAID", LocalDate.now().toString(), "REF 1 <x>").andExpect(status().isBadRequest());
        update(claim, admin, "PAID", LocalDate.now().toString(), "REF-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentReference").value("REF-1"))
                .andExpect(jsonPath("$.allowedNext.length()").value(0));
        update(claim, admin, "PROCESSING", null, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("PAID is final")));

        long another = approved(OWNER);
        update(another, admin, "PAID", LocalDate.now().toString(), "REF-1").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already recorded")));

        mockMvc.perform(get("/claims/audit-logs?action=REIMBURSEMENT_STATUS_CHANGED&targetId=" + claim)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "4"))
                .andExpect(jsonPath("$[0].summary").value(containsString("PROCESSING → PAID")));
        assertThat(outboxEventRepository.findAllByClaimIdOrderById(claim)).filteredOn(e -> e.getEventType() == OutboxEventType.NOTIFICATION)
                .anySatisfy(e -> assertThat(e.getPayload()).contains("REIMBURSEMENT_UPDATED").contains("REF-1"));
    }

    @Test
    void onlyAdministratorsUpdateAndNeverTheirOwnClaim() throws Exception {
        long claim = approved(OWNER);
        update(claim, owner, "PAID", LocalDate.now().toString(), "SELF-1").andExpect(status().isForbidden());
        mockMvc.perform(put("/claims/" + claim + "/reimbursement").header(HttpHeaders.AUTHORIZATION, owner)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());

        long adminsOwn = approvedOwnedBy(1L);
        update(adminsOwn, admin, "PROCESSING", null, null).andExpect(status().isForbidden());
        update(adminsOwn, TestJwt.bearer(2, "ADMIN"), "PROCESSING", null, null).andExpect(status().isOk());
    }

    @Test
    void employeesSeeOnlyTheirOwnReimbursements() throws Exception {
        approved(OWNER);
        approved(OTHER);
        mockMvc.perform(get("/claims/reimbursements").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(header().string("X-Total-Count", "1")).andExpect(jsonPath("$[0].ownerId").value(OWNER))
                .andExpect(jsonPath("$[0].claimNumber").exists());
        mockMvc.perform(get("/claims/reimbursements?ownerId=" + OTHER).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/claims/reimbursements?status=PENDING").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "2"));
        long others = claimRepository.findAll().stream().filter(c -> c.getOwnerId() == OTHER).findFirst().orElseThrow().getId();
        mockMvc.perform(get("/claims/" + others + "/reimbursement").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/claims/audit-logs").header(HttpHeaders.AUTHORIZATION, owner)).andExpect(status().isForbidden());
    }

    private long submitted(long ownerId, String amount) {
        return claimRepository.save(Claim.builder().claimNumber("C-" + UUID.randomUUID().toString().substring(0, 8)).title("Claim")
                .claimAmount(new BigDecimal(amount)).status("SUBMITTED").ownerId(ownerId)
                .expenseIds(new LinkedHashSet<>(List.of(ownerId * 1000 + (long) (Math.random() * 999)))).build()).getId();
    }

    private long approved(long ownerId) throws Exception {
        long id = submitted(ownerId, "30.00");
        approve(id).andExpect(status().isOk());
        return id;
    }

    private long approvedOwnedBy(long ownerId) throws Exception {
        long id = submitted(ownerId, "30.00");
        mockMvc.perform(post("/claims/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(2, "ADMIN")))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions approve(long id) throws Exception {
        return mockMvc.perform(post("/claims/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions update(long claim, String token, String status, String date, String reference) throws Exception {
        String body = "{\"status\":\"%s\",\"paymentDate\":%s,\"paymentReference\":%s}".formatted(status,
                date == null ? "null" : "\"" + date + "\"", reference == null ? "null" : "\"" + reference + "\"");
        return mockMvc.perform(put("/claims/" + claim + "/reimbursement").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
