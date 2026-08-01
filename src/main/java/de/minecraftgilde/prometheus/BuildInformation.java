package de.minecraftgilde.prometheus;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

/** Immutable build metadata generated independently of the Minecraft runtime. */
public record BuildInformation(String gitCommit) {

    private static final String RESOURCE_NAME = "build-info.properties";
    private static final String UNKNOWN = "unknown";
    private static final Pattern COMMIT_PATTERN = Pattern.compile("[0-9a-f]{7,64}");

    public BuildInformation {
        gitCommit = normalize(gitCommit);
    }

    public static BuildInformation load() {
        try (
            InputStream input = BuildInformation.class
                .getClassLoader()
                .getResourceAsStream(RESOURCE_NAME)
        ) {
            return load(input);
        } catch (IOException exception) {
            return new BuildInformation(UNKNOWN);
        }
    }

    static BuildInformation load(InputStream input) {
        if (input == null) {
            return new BuildInformation(UNKNOWN);
        }

        Properties properties = new Properties();
        try {
            properties.load(input);
            return new BuildInformation(properties.getProperty("git-commit"));
        } catch (IOException exception) {
            return new BuildInformation(UNKNOWN);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (UNKNOWN.equals(normalized) || COMMIT_PATTERN.matcher(normalized).matches()) {
            return normalized;
        }
        return UNKNOWN;
    }
}
