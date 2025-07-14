/*
 * Insouciant Qualms © 2024 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("libs") {
            from(files("../build-logic/libs.versions.toml"))
        }
    }
}

includeBuild("../build-logic")
includeBuild("../common")
include("model")
include("persistence")
include("api")
