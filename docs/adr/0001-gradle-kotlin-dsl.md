# ADR 0001: Gradle Kotlin DSL

## Entscheidung

Das Projekt verwendet Gradle mit `build.gradle.kts`.

Der Wrapper ist auf Gradle 9.6.1 festgelegt. Die Binärdistribution
verwendet folgende SHA-256-Prüfsumme:

```text
9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14
```

## Gründe

- typsichere Buildkonfiguration
- gute IDE-Unterstützung
- Java Toolchains
- reproduzierbare Builds über Gradle Wrapper
- einfache GitHub-Actions-Integration
- verifizierbare Wrapper-Distribution
- Unterstützung für Java 25
