plugins {

    id("java-common")
    id("check-common")
    `java-library`
}

configurations.all {
    resolutionStrategy {
        force("org.jgrapht:jgrapht-core:1.5.2")
        force("org.apache.commons:commons-lang3:3.16.0")
        force("org.apache.commons:commons-compress:1.27.1")
        force("net.java.dev.jna:jna:5.17.0")
        force("org.springframework:spring-tx:6.1.13")
        force("org.springframework:spring-core:6.1.13")
        force("org.springframework:spring-beans:6.1.13")
        force("org.springframework:spring-context:6.1.13")
    }
}

dependencies {

    implementation("dev.iq.common:core")
    implementation(project(":persistence"))
    api(project(":model"))
    
    // Spring TX for transaction management
    implementation("org.springframework:spring-context:6.1.13")
    implementation("org.springframework:spring-tx:6.1.13")
    implementation("javax.inject:javax.inject:1")

    testImplementation(testFixtures(project(":persistence")))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers)
    
    // Spring Test dependencies  
    testImplementation("org.springframework:spring-test:6.1.13")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.5")
}
