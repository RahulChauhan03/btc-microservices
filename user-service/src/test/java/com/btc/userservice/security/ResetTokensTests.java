package com.btc.userservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResetTokensTests {

    @Test
    void tokensAreUrlSafeHighEntropyAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            String token = ResetTokens.newToken();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            assertThat(seen.add(token)).isTrue();
        }
    }

    @Test
    void onlyAFixedLengthOneWayHashIsStored() {
        String token = ResetTokens.newToken();
        String hash = ResetTokens.hash(token);

        assertThat(hash).hasSize(64).matches("[0-9a-f]+").isEqualTo(ResetTokens.hash(token)).doesNotContain(token);
        assertThat(ResetTokens.hash(ResetTokens.newToken())).isNotEqualTo(hash);
    }
}
