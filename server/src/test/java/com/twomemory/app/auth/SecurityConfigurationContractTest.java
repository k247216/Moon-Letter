package com.twomemory.app.auth;

import com.twomemory.app.CoupleDiaryApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationContractTest {

    @Test
    void bootstrapSecretComparisonRejectsMissingOrDifferentValues() {
        assertThat(BootstrapService.secretsMatch("deployment-secret", "deployment-secret")).isTrue();
        assertThat(BootstrapService.secretsMatch("deployment-secret", "different-secret")).isFalse();
        assertThat(BootstrapService.secretsMatch("deployment-secret", null)).isFalse();
        assertThat(BootstrapService.secretsMatch("", "")).isFalse();
    }

    @Test
    void applicationExcludesUnusedGeneratedPasswordUser() {
        SpringBootApplication annotation = CoupleDiaryApplication.class
                .getAnnotation(SpringBootApplication.class);

        assertThat(Arrays.asList(annotation.exclude()))
                .contains(UserDetailsServiceAutoConfiguration.class);
    }
}
