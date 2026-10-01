package com.twomemory.app.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The recovery command is the only way back into an archive when both devices
 * are locked out, so the invocation written in the README must be the one that
 * actually works. Reading only {@code System.getProperty} made every documented
 * {@code --moon-letter.admin.*} program argument a silent no-op.
 */
class SessionAdminCommandInvocationTest {

    private static final String MODE = "moon-letter.admin.mode";
    private static final String USER_ID = "moon-letter.admin.user-id";

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty(MODE);
        System.clearProperty(USER_ID);
    }

    @Test
    void readsFlagsPassedAsProgramArguments() {
        UUID userId = UUID.randomUUID();
        SessionAdminCommand.Request request = command().resolveRequest(
                new DefaultApplicationArguments("--" + MODE + "=issue", "--" + USER_ID + "=" + userId));

        assertThat(request.mode()).isEqualTo(SessionAdminCommand.Mode.ISSUE_REPLACEMENT);
        assertThat(request.userId()).isEqualTo(userId);
    }

    @Test
    void stillReadsFlagsPassedAsSystemProperties() {
        UUID userId = UUID.randomUUID();
        System.setProperty(MODE, "revoke-all");
        System.setProperty(USER_ID, userId.toString());

        SessionAdminCommand.Request request = command().resolveRequest(
                new DefaultApplicationArguments());

        assertThat(request.mode()).isEqualTo(SessionAdminCommand.Mode.REVOKE_ALL);
        assertThat(request.userId()).isEqualTo(userId);
    }

    @Test
    void programArgumentsWinOverSystemProperties() {
        UUID userId = UUID.randomUUID();
        System.setProperty(MODE, "issue");
        System.setProperty(USER_ID, userId.toString());
        SessionAdminCommand.Request request = command().resolveRequest(
                new DefaultApplicationArguments("--" + MODE + "=revoke-all"));

        assertThat(request.mode()).isEqualTo(SessionAdminCommand.Mode.REVOKE_ALL);
        assertThat(request.userId()).isEqualTo(userId);
    }

    @Test
    void aNormalServerBootIsRecognisedByTheAbsenceOfAMode() {
        assertThat(command().resolveRequest(new DefaultApplicationArguments(
                "--spring.profiles.active=dev", "--server.port=8080"))).isNull();
    }

    @Test
    void aModeWithoutATargetUserFailsInsteadOfQuietlyDoingNothing() {
        assertThatThrownBy(() -> command().resolveRequest(
                new DefaultApplicationArguments("--" + MODE + "=issue")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(USER_ID);
    }

    @Test
    void anUnknownModeFailsInsteadOfBootingTheWebApplication() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> command().resolveRequest(
                new DefaultApplicationArguments("--" + MODE + "=delete-everything", "--" + USER_ID + "=" + userId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(MODE);
    }

    private static SessionAdminCommand command() {
        return new SessionAdminCommand(null, null);
    }
}
