plugins {
    id("com.android.application")
}

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val trufarApiUrl = providers.gradleProperty("TRUFAR_API_URL").orElse("").get()
val trufarApiToken = providers.gradleProperty("TRUFAR_API_TOKEN").orElse("").get()
// OpenAI-compatible endpoint that runs the bundled TruFar agent (Kafka); see CLAUDE.md §5.2.
val trufarLlmUrl = providers.gradleProperty("TRUFAR_LLM_URL").orElse("").get()
val trufarLlmApiKey = providers.gradleProperty("TRUFAR_LLM_API_KEY").orElse("").get()
val trufarLlmModel = providers.gradleProperty("TRUFAR_LLM_MODEL").orElse("").get()

android {
    namespace = "com.example.tomodoom"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.tomodoom"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TRUFAR_API_URL", buildConfigString(trufarApiUrl))
        buildConfigField("String", "TRUFAR_API_TOKEN", buildConfigString(trufarApiToken))
        buildConfigField("String", "TRUFAR_LLM_URL", buildConfigString(trufarLlmUrl))
        buildConfigField("String", "TRUFAR_LLM_API_KEY", buildConfigString(trufarLlmApiKey))
        buildConfigField("String", "TRUFAR_LLM_MODEL", buildConfigString(trufarLlmModel))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true // BuildConfig.DEBUG gates the Pets debug panel
    }
}

dependencies {

    implementation("androidx.core:core:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.navigation:navigation-fragment:2.7.7")
    implementation("androidx.navigation:navigation-ui:2.7.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM tests (android.jar only has throwing stubs); parses the agent export.
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}