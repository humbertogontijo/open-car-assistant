pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "open-car-assistant"

include(":app")
include(":integration-api")
include(":oca-support")
project(":oca-support").projectDir = file("support")

include(":integrations:platform:common")
include(":integrations:platform:flyme")
project(":integrations:platform:common").projectDir = file("integrations/platform/common")
project(":integrations:platform:flyme").projectDir = file("integrations/platform/flyme")

// Vehicle integrations: every integrations/<id>/ with a build.gradle.kts (except platform/)
file("integrations").listFiles()
    ?.filter { it.isDirectory && it.name != "platform" && File(it, "build.gradle.kts").exists() }
    ?.sortedBy { it.name }
    ?.forEach { dir ->
        val path = ":integrations:${dir.name}"
        include(path)
        project(path).projectDir = dir
    }

// External bridge plugins: every plugin-*/ with a build.gradle.kts
rootDir.listFiles()
    ?.filter { it.isDirectory && it.name.startsWith("plugin-") && File(it, "build.gradle.kts").exists() }
    ?.sortedBy { it.name }
    ?.forEach { dir ->
        include(":${dir.name}")
    }

include(":feature-memory")
include(":feature-telemetry")
include(":feature-web")
include(":feature-install")
include(":signing")
include(":feature-dvr")
include(":feature-debug")
include(":feature-history")
include(":feature-shortcuts")
include(":car-stubs")
