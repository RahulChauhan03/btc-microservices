package com.btc.userservice.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.userservice.audit.AuditLogRepository;
import com.btc.userservice.entity.User;
import com.btc.userservice.repository.PasswordResetTokenRepository;
import com.btc.userservice.repository.UserRepository;
import com.btc.userservice.security.ResetTokens;
import com.btc.userservice.security.TestJwt;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Forgot / reset password end to end through HTTP, security, service and database. SMTP is a Mockito mock
 * (no email ever leaves the test) and time is a controllable clock. Subclasses choose the database.
 */
@SpringBootTest(properties = {
        "spring.mail.host=smtp.test.invalid",
        "btc.password-reset.mail-from=no-reply@btc-flow.test",
        "btc.password-reset.frontend-base-url=https://app.btc-flow.test/",
        "btc.password-reset.requests-per-client=1000",
        "btc.password-reset.reset-attempts-per-client=1000"})
@AutoConfigureMockMvc
@Import(AbstractPasswordResetIntegrationTest.ClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
abstract class AbstractPasswordResetIntegrationTest {

    static final String GENERIC = "If an account exists for that email, you will receive password-reset instructions.";
    private static final String OLD_PASSWORD = "old-password-1";
    private static final Pattern TOKEN = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]{43})");

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordResetTokenRepository tokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MutableClock clock;

    @Autowired
    AuditLogRepository auditLogRepository;

    @MockitoBean
    JavaMailSender mailSender;

    private String email;
    private String client;

    @BeforeEach
    void setUp() {
        clock.reset();
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        String id = UUID.randomUUID().toString().substring(0, 8);
        email = "reset-" + id + "@example.com";
        client = "203.0.113." + (Math.abs(id.hashCode()) % 250);
        userRepository.save(User.builder().name("Reset User").email(email).phone("1").role("EMPLOYEE")
                .passwordHash(passwordEncoder.encode(OLD_PASSWORD)).build());
    }

    @AfterEach
    void cleanUp() {
        tokenRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        reset(mailSender);
    }

    @Test
    void sameGenericResponseForKnownAndUnknownAddressesButOnlyKnownOnesGetMail(CapturedOutput output) throws Exception {
        forgot(email.toUpperCase()).andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(GENERIC));
        forgot("nobody-" + UUID.randomUUID() + "@example.com").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(GENERIC));

        MimeMessage message = sentMessage();
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly(email);
        verify(mailSender, timeout(500).times(1)).send(any(MimeMessage.class));
        assertThat(output).doesNotContain(email.toUpperCase()).doesNotContain(tokenFrom(message));
    }

    @Test
    void emailHasBrandingTheConfiguredLinkExpiryAndIgnoreNoteButNoSecrets() throws Exception {
        forgot(email).andExpect(status().isAccepted());
        MimeMessage message = sentMessage();
        String text = text(message, "text/plain");
        String html = text(message, "text/html");

        assertThat(message.getSubject()).isEqualTo("Reset your BTC Flow password");
        assertThat(message.getFrom()[0].toString()).isEqualTo("no-reply@btc-flow.test");
        assertThat(text).contains("BTC Flow").contains("https://app.btc-flow.test/reset-password?token=")
                .contains("expires in 15 minutes").contains("you can ignore this email").doesNotContain(OLD_PASSWORD);
        assertThat(html).contains("BTC Flow").contains("https://app.btc-flow.test/reset-password?token=")
                .contains("15 minutes");
    }

    @Test
    void onlyTheHashIsStoredAndANewRequestSupersedesTheOldLink() throws Exception {
        forgot(email);
        String first = tokenFrom(sentMessage());
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        forgot(email);
        String second = tokenFrom(sentMessage());

        List<String> stored = jdbcTemplate.queryForList("select token_hash from password_reset_tokens", String.class);
        assertThat(stored).contains(ResetTokens.hash(first), ResetTokens.hash(second)).doesNotContain(first, second);
        resetWith(first, "new-password-1", "new-password-1").andExpect(status().isNotFound());
        resetWith(second, "new-password-1", "new-password-1").andExpect(status().isOk());
    }

    @Test
    void validTokenChangesThePasswordOnceAndDoesNotSignTheUserIn(CapturedOutput output) throws Exception {
        forgot(email);
        String token = tokenFrom(sentMessage());

        resetWith(token, "brand-new-pass", "brand-new-pass").andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.message").value("Your password has been reset. You can now sign in."));

        login(OLD_PASSWORD).andExpect(status().isUnauthorized());
        login("brand-new-pass").andExpect(status().isOk());
        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("USER_PASSWORD_RESET");
            assertThat(log.getTargetId()).isEqualTo(user().getId());
            assertThat(log.getSummary()).doesNotContain(token).doesNotContain("brand-new-pass");
        });
        resetWith(token, "another-pass-1", "another-pass-1").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This password reset link is invalid or has already been used."));
        assertThat(output).doesNotContain(token).doesNotContain("brand-new-pass").doesNotContain(email);
    }

    @Test
    void expiredUnknownAndRevokedTokensAreRejected() throws Exception {
        forgot(email);
        String token = tokenFrom(sentMessage());

        resetWith("not-a-real-token", "brand-new-pass", "brand-new-pass").andExpect(status().isNotFound());
        clock.advance(Duration.ofMinutes(16));
        resetWith(token, "brand-new-pass", "brand-new-pass").andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("This password reset link has expired. Please request a new one."));
        assertThat(passwordEncoder.matches(OLD_PASSWORD, user().getPasswordHash())).isTrue();
    }

    @Test
    void passwordRulesAreEnforcedByTheServer() throws Exception {
        forgot(email);
        String token = tokenFrom(sentMessage());

        resetWith(token, "brand-new-pass", "different-pass").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passwords do not match"));
        resetWith(token, "short", "short").andExpect(status().isBadRequest());
        String tooLong = "é".repeat(40); // 40 characters but 80 bytes: BCrypt would silently truncate it
        resetWith(token, tooLong, tooLong).andExpect(status().isBadRequest());
        resetWith(token, "brand-new-pass", "brand-new-pass").andExpect(status().isOk());
    }

    @Test
    void concurrentUseOfOneTokenSucceedsExactlyOnce() throws Exception {
        forgot(email);
        String token = tokenFrom(sentMessage());
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String password = "concurrent-pass-" + i;
                results.add(pool.submit(() -> {
                    start.await();
                    return resetWith(token, password, password).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnlyOnce(200).filteredOn(code -> code != 200).containsOnly(404);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from password_reset_tokens where used_at is not null",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void failedDeliveryStillAnswersGenericallyAndRevokesTheUnsentLink() throws Exception {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        forgot(email).andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(GENERIC));

        verify(mailSender, timeout(5000)).send(any(MimeMessage.class));
        for (int i = 0; i < 50 && openTokens() > 0; i++) {
            Thread.sleep(100);
        }
        assertThat(openTokens()).isZero();
    }

    @Test
    void perAddressLimitStopsMailButNotTheGenericResponse() throws Exception {
        for (int i = 0; i < 5; i++) {
            forgot(email).andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(GENERIC));
        }
        verify(mailSender, timeout(5000).times(3)).send(any(MimeMessage.class));
        Thread.sleep(300);
        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void recoveryEndpointsArePublicWhileEverythingElseStillNeedsAToken() throws Exception {
        forgot(email).andExpect(status().isAccepted());
        resetWith("unknown-token", "brand-new-pass", "brand-new-pass").andExpect(status().isNotFound());
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/" + user().getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users").header("Authorization", TestJwt.bearer(user().getId(), "EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedRequestsAreRejected() throws Exception {
        forgot("not-an-email").andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ---- helpers ----

    ResultActions forgot(String address) throws Exception {
        return mockMvc.perform(post("/auth/forgot-password").header("X-Forwarded-For", client)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + address + "\"}"));
    }

    ResultActions resetWith(String token, String password, String confirmation) throws Exception {
        return mockMvc.perform(post("/auth/reset-password").header("X-Forwarded-For", client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                        .formatted(token, password, confirmation)));
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000)).send(captor.capture());
        return captor.getValue();
    }

    private static String tokenFrom(MimeMessage message) throws Exception {
        Matcher matcher = TOKEN.matcher(text(message, "text/plain"));
        assertThat(matcher.find()).as("reset link in email").isTrue();
        return matcher.group(1);
    }

    private static String text(Part part, String mimeType) throws Exception {
        if (part instanceof MimeMessage message) {
            message.saveChanges(); // finalises MIME headers, as sending would
        }
        Object content = part.getContent();
        if (content instanceof String body) {
            return part.isMimeType(mimeType) ? body : null;
        }
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart body = multipart.getBodyPart(i);
                String found = text(body, mimeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private User user() {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }

    private int openTokens() {
        return jdbcTemplate.queryForObject("select count(*) from password_reset_tokens where used_at is null "
                + "and revoked_at is null and user_id = ?", Integer.class, user().getId());
    }

    static final class MutableClock extends Clock {
        private volatile Instant instant = Instant.now();

        void reset() {
            instant = Instant.now();
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

}
