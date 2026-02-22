pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "umbra"
include(":app")
include(":core")
include(":terminal")

// Auto-register feature modules
file("features").let { dir ->
    if (dir.isDirectory) {
        dir.listFiles()?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }?.forEach {
            include(":features:${it.name}")
        }
    }
}
