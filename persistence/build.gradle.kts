import java.time.Duration

plugins {

    id("java-common")
    id("check-common")
    `java-library`
}

dependencies {

    // Propject
    implementation("dev.iq.common:core")
    api(project(":model"))

    // JGraphT
    implementation(libs.jgrapht.core)

    // Spring - minimal dependencies for @Repository and transaction support
    implementation("org.springframework:spring-context:6.2.9")
    implementation("org.springframework:spring-tx:6.2.9")
    implementation("javax.inject:javax.inject:1")

    // Tinkerpop
    implementation(libs.gremlin.core)
    runtimeOnly(libs.gremlin.driver)
    testImplementation(libs.gremlin.tinkergraph)

    // SqlLite
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")

    // Connection pooling and JDBI
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation(platform("org.jdbi:jdbi3-bom:3.45.1"))
    implementation("org.jdbi:jdbi3-core")
    implementation("org.jdbi:jdbi3-sqlobject")

    // Mongo DB
    implementation("org.mongodb:mongodb-driver-sync:5.5.1")
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:4.20.1")

    // Testcontainers
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers)
    
    // Spring Test dependencies
    testImplementation("org.springframework:spring-test:6.2.9")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.5") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j2-impl")
    }
}

// Conflicts stem from de.flapdoodle.embed.mongo
configurations.all {
    resolutionStrategy {
        force("org.apache.commons:commons-lang3:3.16.0")
        force("org.apache.commons:commons-compress:1.27.1")
        force("net.java.dev.jna:jna:5.17.0")
        force("org.jgrapht:jgrapht-core:1.5.2")
        force("org.junit.jupiter:junit-jupiter-api:5.10.2")
        force("org.junit:junit-bom:5.10.2")
        force("org.junit.jupiter:junit-jupiter-engine:5.10.2")
        force("org.springframework:spring-context:6.2.9")
        force("org.springframework:spring-tx:6.2.9")
        force("org.springframework:spring-core:6.2.9")
        force("org.springframework:spring-beans:6.2.9")
    }
    
    // Exclude log4j-to-slf4j to avoid conflict with log4j-slf4j2-impl
    exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")
}

// Configure test timeouts to prevent hanging
tasks.withType<Test> {
    timeout.set(Duration.ofMinutes(5))
    
    // Ensure tests don't hang on shutdown and suppress ByteBuddy agent warning
    jvmArgs(
        "-Xmx512m", 
        "-XX:+UseG1GC"
    )
    
    // Log test events - only show failures and skipped tests
    testLogging {
        events("skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        
        // Only show stdout/stderr for failed tests
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
