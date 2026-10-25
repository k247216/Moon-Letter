import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Deployment identities and personal copy are local inputs, never repository defaults.
val moonletterPrivate = Properties().apply {
    val source = rootProject.file("private.properties")
    if (source.isFile) source.reader(Charsets.UTF_8).use { load(it) }
}
fun moonletterSetting(name: String, fallback: String): String =
    providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull
        ?: moonletterPrivate.getProperty(name) ?: fallback
fun moonletterLiteral(value: String): String = "\"" + value.replace("\\", "\\\\")
    .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

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
        // Every externally testable behavior change gets a new package identity.
        // V8: compact journal, city search, gallery export and identity-first setup.
        versionCode = 43
        versionName = "0.1.0+paper-interactions-v43-20261025"
        buildConfigField("String", "PUBLIC_API_ORIGIN", moonletterLiteral(moonletterSetting("MOONLETTER_PUBLIC_ORIGIN", "https://api.example.invalid")))
        buildConfigField("String", "DEV_SERVER_ORIGIN", moonletterLiteral(moonletterSetting("MOONLETTER_DEV_ORIGIN", "http://10.0.2.2:8080")))
        buildConfigField("String", "FIRST_MEMBER_WELCOME", moonletterLiteral(moonletterSetting("MOONLETTER_FIRST_WELCOME", "记录彼此想念的每一天")))
        buildConfigField("String", "OSS_HOST", moonletterLiteral(moonletterSetting("MOONLETTER_OSS_HOST", "bucket.example.invalid")))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }

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
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
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
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test:runner:1.7.0")
    testImplementation("androidx.test.espresso:espresso-core:3.7.0")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp)
    testImplementation("org.postgresql:postgresql:42.7.4")
}
