plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cc.opencar.assistant.integrations.antora1000"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":integration-api"))
    api(project(":integrations:platform:aaos"))
    api(project(":integrations:platform:flyme"))
    compileOnly(project(":car-stubs"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-ktx:1.15.0")
    // VenusVehicleServer (unprivileged Antora path)
    implementation("io.grpc:grpc-okhttp:1.68.1")
    implementation("io.grpc:grpc-stub:1.68.1")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
}
