plugins {
    id("java-common")
}

dependencies {

    implementation("dev.iq.common:core")
    implementation(project(":model"))
    implementation("com.cedarpolicy:cedar-java:3.2.0")
}
