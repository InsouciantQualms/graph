plugins {
    id("java-common")
}

dependencies {
    implementation(project(":model"))
    implementation("com.cedarpolicy:cedar-java:3.2.0")
}