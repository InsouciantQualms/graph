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
    }
}

dependencies {

    implementation("dev.iq.common:core")
    implementation(project(":persistence"))
    api(project(":model"))

    testImplementation(testFixtures(project(":persistence")))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers)
}
