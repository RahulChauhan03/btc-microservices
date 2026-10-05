package com.btc.claimservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.claimservice.audit.AuditService;
import com.btc.claimservice.client.ExpenseLockClient.ExpenseSummary;
import com.btc.claimservice.client.ExpenseLockClient;
import com.btc.claimservice.config.SecurityConfig;
import com.btc.claimservice.entity.Claim;
import com.btc.claimservice.outbox.ClaimOutbox;
import com.btc.claimservice.reimbursement.ReimbursementService;
import com.btc.claimservice.repository.ClaimRepository;
import com.btc.claimservice.security.TestJwt;
import com.btc.claimservice.service.impl.ClaimServiceImpl;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClaimController.class)
@Import({SecurityConfig.class, ClaimServiceImpl.class})
class ClaimAuthorizationWebTests {

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClaimRepository claimRepository;

    @MockitoBean
    private ExpenseLockClient expenseLockClient;

    @MockitoBean
    private ClaimOutbox claimOutbox;

    @MockitoBean
    private ReimbursementService reimbursementService;

    @MockitoBean
    private AuditService auditService;

    @Test
    void clientSuppliedAmountStatusOwnerAndReviewerAreIgnored() throws Exception {
        when(claimRepository.findExpenseIdsInActiveClaims(anyCollection(), anyLong())).thenReturn(List.of());
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimRepository.saveAndFlush(any(Claim.class))).thenAnswer(invocation -> {
            Claim claim = invocation.getArgument(0);
            claim.setId(1L);
            return claim;
        });
        when(expenseLockClient.lockForClaim(eq(1L), eq(10L), anyCollection()))
                .thenReturn(List.of(new ExpenseSummary(1L, 10L, null, new BigDecimal("42.00"))));

        mockMvc.perform(post("/claims").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(10, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"claimNumber":"C-1","title":"Taxi","expenseIds":[1],
                                 "claimAmount":99999,"status":"APPROVED","ownerId":1,"reviewedBy":10}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimAmount").value(42.00))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.ownerId").value(10))
                .andExpect(jsonPath("$.reviewedBy").doesNotExist());

        ArgumentCaptor<Claim> saved = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(saved.capture());
        assertThat(saved.getValue().getClaimAmount()).isEqualByComparingTo("42.00");
    }

    @Test
    void claimWithoutExpensesIsRejected() throws Exception {
        mockMvc.perform(post("/claims").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(10, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimNumber\":\"C-1\",\"title\":\"Taxi\",\"claimAmount\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewEndpointsEnforceRoleAndSelfApproval() throws Exception {
        when(claimRepository.findById(7L)).thenReturn(Optional.of(Claim.builder().id(7L).status("SUBMITTED")
                .ownerId(10L).claimAmount(BigDecimal.TEN).expenseIds(new LinkedHashSet<>(List.of(1L))).build()));
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/claims/7/approve").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(10, "EMPLOYEE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/claims/7/approve").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(10, "EMPLOYEE"))
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/claims/7/approve").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(10, "ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/claims/7/reject").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(1, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewedBy").value(1));
    }
}
