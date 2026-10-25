package com.twomemory.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RepositoryCredentialHygieneTest {

    private static final String FORBIDDEN_DEVELOPMENT_PASSWORD = "moon_letter_" + "dev_only";

    @Test
    void repositoryDoesNotContainCommittedDevelopmentDatabasePassword() throws IOException {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path repositoryRoot = Files.exists(workingDirectory.resolve("infra/compose.yaml"))
                ? workingDirectory
                : workingDirectory.getParent();
        List<Path> filesToInspect = List.of(
                repositoryRoot.resolve("infra/compose.yaml"),
                repositoryRoot.resolve("server/src/main/resources/application-dev.yml"),
                repositoryRoot.resolve("server/src/test"),
                repositoryRoot.resolve("android/app/src/test")
        );

        for (Path path : filesToInspect) {
            if (Files.isDirectory(path)) {
                try (var paths = Files.walk(path)) {
                    paths.filter(Files::isRegularFile)
                            .filter(file -> file.toString().endsWith(".java")
                                    || file.toString().endsWith(".kt")
                                    || file.toString().endsWith(".yml")
                                    || file.toString().endsWith(".yaml"))
                            .forEach(this::assertDoesNotContainPassword);
                }
            } else {
                assertDoesNotContainPassword(path);
            }
        }
    }

    private void assertDoesNotContainPassword(Path file) {
        try {
            assertFalse(Files.readString(file).contains(FORBIDDEN_DEVELOPMENT_PASSWORD),
                    () -> "Committed development password found in " + file);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + file, exception);
        }
    }
}
