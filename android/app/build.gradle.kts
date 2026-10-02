plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * versionName carries the commit this build came from: two phones can otherwise be
 * running different debug builds with no way to tell them apart on the device.
 * "-dirty" means uncommitted code is in the APK, so a green device run is not
 * automatically evidence for the pushed commit.
 */
fun gitDescriptor(workingDir: File): String {
    fun run(vararg args: String): String? = runCatching {
        val process = ProcessBuilder(*args)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && text.isNotEmpty()) text else null
    }.getOrNull()
    val sha = run("git", "rev-parse", "--short", "HEAD") ?: return "unknown"
    val dirty = run("git", "status", "--porcelain") != null
    return if (dirty) "$sha-dirty" else sha
}

android {
    namespace = "com.twomemory.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.twomemory.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0+" + gitDescriptor(rootDir)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:sync"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:timeline"))
    implementation(project(":feature:couple"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.test.junit)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp)
    testImplementation("org.postgresql:postgresql:42.7.4")
}
