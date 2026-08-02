import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.util.zip.ZipFile

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

fun normalizeGitCommit(value: String?): String? {
    val normalized = value?.trim()?.lowercase()
    return normalized?.takeIf {
        it == "unknown" || it.matches(Regex("[0-9a-f]{7,64}"))
    }
}

fun detectGitCommit(repositoryRoot: File): String {
    return runCatching {
        val process = ProcessBuilder(
            "git",
            "-C",
            repositoryRoot.absolutePath,
            "rev-parse",
            "HEAD"
        )
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        if (process.waitFor() == 0) {
            normalizeGitCommit(output) ?: "unknown"
        } else {
            "unknown"
        }
    }.getOrDefault("unknown")
}

group = providers.gradleProperty("projectGroup").get()
version = providers.gradleProperty("projectVersion").get()

val targetJavaVersion = providers.gradleProperty("javaVersion").get().toInt()
val buildGitCommit = providers.gradleProperty("gitCommit").orNull
    ?.let(::normalizeGitCommit)
    ?: detectGitCommit(rootDir)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(
        platform("io.prometheus:prometheus-metrics-bom:${providers.gradleProperty("prometheusVersion").get()}")
    )
    implementation("io.prometheus:prometheus-metrics-core")
    implementation("io.prometheus:prometheus-metrics-instrumentation-jvm")
    implementation("io.prometheus:prometheus-metrics-exporter-httpserver")

    compileOnly(
        "io.papermc.paper:paper-api:${providers.gradleProperty("paperApiVersion").get()}"
    )

    testImplementation(
        platform("org.junit:junit-bom:${providers.gradleProperty("junitVersion").get()}")
    )
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(
        "io.papermc.paper:paper-api:${providers.gradleProperty("paperApiVersion").get()}"
    )
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(targetJavaVersion)
    options.encoding = Charsets.UTF_8.name()
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    inputs.property("gitCommit", buildGitCommit)
    filteringCharset = Charsets.UTF_8.name()
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
    filesMatching("build-info.properties") {
        expand("gitCommit" to buildGitCommit)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate(
        "io.prometheus",
        "de.minecraftgilde.prometheus.internal.prometheus"
    )
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

val expectedPluginVersion = project.version.toString()
val verifyPluginJar = tasks.register("verifyPluginJar") {
    group = "verification"
    description = "Verifies the contents of the single distributable plugin JAR."
    dependsOn(tasks.shadowJar)

    doLast {
        val jarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val pluginJars = jarFile.parentFile
            .listFiles { file -> file.isFile && file.extension == "jar" }
            ?.toList()
            .orEmpty()
        check(pluginJars == listOf(jarFile)) {
            "Expected exactly one distributable JAR, found: ${pluginJars.map { it.name }}"
        }

        ZipFile(jarFile).use { archive ->
            val entries = archive.entries().asSequence().toList()
            val names = entries.map { it.name }.toSet()
            check("plugin.yml" in names) { "plugin.yml is missing from the plugin JAR" }
            check("build-info.properties" in names) {
                "build-info.properties is missing from the plugin JAR"
            }
            check(
                "de/minecraftgilde/prometheus/ExporterPlugin.class" in names
            ) { "Plugin main class is missing from the plugin JAR" }
            check(
                names.any {
                    it.startsWith(
                        "de/minecraftgilde/prometheus/internal/prometheus/metrics/"
                    )
                }
            ) { "Relocated Prometheus runtime classes are missing" }
            check(
                listOf(
                    "JvmMemoryMetrics.class",
                    "JvmGarbageCollectorMetrics.class",
                    "JvmThreadsMetrics.class",
                    "JvmClassLoadingMetrics.class",
                    "JvmBufferPoolMetrics.class",
                    "ProcessMetrics.class"
                ).all { className ->
                    "de/minecraftgilde/prometheus/internal/prometheus/metrics/instrumentation/jvm/$className" in names
                }
            ) { "Relocated JVM/process instrumentation classes are missing" }
            check(names.none { it.startsWith("io/prometheus/") }) {
                "Unrelocated Prometheus runtime classes are present"
            }
            check(
                names.none {
                    it.startsWith("org/bukkit/")
                        || it.startsWith("io/papermc/paper/")
                        || it.startsWith("dev/folia/")
                        || it.startsWith("net/minecraft/")
                }
            ) { "A Paper, Folia, Bukkit, or Minecraft API class was embedded" }
            check(
                names.none {
                    it.startsWith("META-INF/")
                        && (
                            it.endsWith(".SF")
                                || it.endsWith(".DSA")
                                || it.endsWith(".RSA")
                        )
                }
            ) { "Dependency signature files are present" }

            val descriptor = archive
                .getInputStream(archive.getEntry("plugin.yml"))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            check("main: de.minecraftgilde.prometheus.ExporterPlugin" in descriptor)
            check("version: '$expectedPluginVersion'" in descriptor)
            check("\${" !in descriptor) {
                "plugin.yml contains an unexpanded placeholder"
            }

            val buildInfo = archive
                .getInputStream(archive.getEntry("build-info.properties"))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            check("git-commit=$buildGitCommit" in buildInfo) {
                "build-info.properties does not contain the resolved Git commit"
            }
            check("\${" !in buildInfo) {
                "build-info.properties contains an unexpanded placeholder"
            }
        }
    }
}

tasks.check {
    dependsOn(verifyPluginJar)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
