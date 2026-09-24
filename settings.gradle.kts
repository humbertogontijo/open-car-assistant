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

// Shared libraries under libs/
include(":integration-api")
project(":integration-api").projectDir = file("libs/api")

include(":oca-support")
project(":oca-support").projectDir = file("libs/support")

include(":car-stubs")
project(":car-stubs").projectDir = file("libs/car-stubs")

include(":signing")
project(":signing").projectDir = file("libs/signing")

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

// External bridge plugins: every plugins/<id>/ with a build.gradle.kts
file("plugins").listFiles()
    ?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
    ?.sortedBy { it.name }
    ?.forEach { dir ->
        val path = ":plugin-${dir.name}"
        include(path)
        project(path).projectDir = dir
    }

// Curated shell features under features/ (Gradle names stay :feature-<id>)
listOf(
    "memory",
    "telemetry",
    "web",
    "install",
    "dvr",
    "debug",
    "history",
    "shortcuts",
).forEach { id ->
    val path = ":feature-$id"
    include(path)
    project(path).projectDir = file("features/$id")
}
