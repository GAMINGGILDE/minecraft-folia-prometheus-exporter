package de.minecraftgilde.prometheus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BuildInformationTest {

    @Test
    void loadsTheExpandedBuildResource() {
        String gitCommit = BuildInformation.load().gitCommit();

        assertFalse(gitCommit.contains("${"));
        assertTrue(
            gitCommit.equals("unknown") || gitCommit.matches("[0-9a-f]{7,64}")
        );
    }

    @Test
    void normalizesAValidCommitHash() {
        BuildInformation information = BuildInformation.load(
            resource("git-commit=ABCDEF1234567\n")
        );

        assertEquals("abcdef1234567", information.gitCommit());
    }

    @Test
    void fallsBackForMissingOrInvalidMetadata() {
        assertEquals("unknown", BuildInformation.load(null).gitCommit());
        assertEquals(
            "unknown",
            BuildInformation.load(resource("git-commit=not-a-hash\n")).gitCommit()
        );
    }

    @Test
    void fallsBackWhenTheResourceCannotBeRead() {
        InputStream failingInput = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("expected");
            }
        };

        assertEquals("unknown", BuildInformation.load(failingInput).gitCommit());
    }

    private static InputStream resource(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
