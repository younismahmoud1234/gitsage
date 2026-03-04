plugins {
    id("io.micronaut.application") version "4.4.4"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

version = "0.1.0"
group = "dev.gitsage"

repositories {
    mavenCentral()
}

dependencies {
    // Micronaut
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.reactor:micronaut-reactor")

    // LangChain4j
    implementation("dev.langchain4j:langchain4j:${property("langchain4jVersion")}")
    implementation("dev.langchain4j:langchain4j-open-ai:${property("langchain4jVersion")}")
    implementation("dev.langchain4j:langchain4j-embeddings:${property("langchain4jVersion")}")

    // pgvector
    implementation("com.pgvector:pgvector:${property("pgvectorVersion")}")
    runtimeOnly("org.postgresql:postgresql")

    // Logging
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")

    // Lombok
    annotationProcessor("org.projectlombok:lombok:${property("lombokVersion")}")
    compileOnly("org.projectlombok:lombok:${property("lombokVersion")}")

    // Test
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
    testImplementation("org.mockito:mockito-core:${property("mockitoVersion")}")
    testImplementation("org.mockito:mockito-junit-jupiter:${property("mockitoVersion")}")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:postgresql:${property("testcontainersVersion")}")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

application {
    mainClass = "dev.gitsage.Application"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--enable-preview"))
}

tasks.withType<JavaExec> {
    jvmArgs("--enable-preview")
}

graalvmNative.toolchainDetection = false

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("dev.gitsage.*")
    }
}
