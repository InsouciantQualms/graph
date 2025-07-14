plugins {

    id("java-common")
    id("check-common")
    `java-library`

    id("plantuml-common")
}

dependencies {

    // Common framework
    implementation("dev.iq.common:core")

    // JGraphT in memory graph database
    implementation(libs.jgrapht.core)

    // JSON processing and flattening
    implementation(libs.bundles.jackson)
    implementation("com.github.wnameless.json:json-flattener:0.16.6") {
        exclude(group = "com.fasterxml.jackson.core")
    }

    // Testcontainers for integration testing
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers)
}

classDiagrams {
}
