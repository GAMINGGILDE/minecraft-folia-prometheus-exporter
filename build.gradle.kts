plugins {
    java
}

group = providers.gradleProperty("projectGroup").get()
version = providers.gradleProperty("projectVersion").get()

java {
    toolchain {
        languageVersion.set(
            JavaLanguageVersion.of(
                providers.gradleProperty("javaVersion").get().toInt()
            )
        )
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Versions werden vor der Implementierung verbindlich festgelegt.
    compileOnly(
        "dev.folia:folia-api:${providers.gradleProperty("foliaApiVersion").get()}"
    )

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
