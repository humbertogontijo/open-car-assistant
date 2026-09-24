plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "cc.opencar.assistant.feature.install"
    compileSdk = 35
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Single source of truth: signing/community.* → packaged assets at build time
val copyCommunityKeys by tasks.registering(Copy::class) {
    from(rootProject.file("signing/community.pk8"), rootProject.file("signing/community.pem"))
    into(layout.projectDirectory.dir("src/main/assets/signing"))
}

tasks.named("preBuild").configure { dependsOn(copyCommunityKeys) }

dependencies {
    api(project(":integration-api"))
    api(project(":signing"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-ktx:1.15.0")
}
