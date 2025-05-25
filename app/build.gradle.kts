plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.freelancera"
    compileSdk = 35

    defaultConfig {

        applicationId = "com.example.freelancera"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["appAuthRedirectScheme"] = "freelancerauth"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:deprecation")
    }
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("net.openid:appauth:0.11.1")
    implementation("androidx.browser:browser:1.7.0")  // Chrome Custom Tabs
    // OkHttp (do logowania zapytań – pomocne przy debugowaniu)
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
    implementation ("androidx.appcompat:appcompat:1.6.1")
    // Jeśli chcesz pracować z JsonObject (z GSON lub Moshi):
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(platform("com.google.firebase:firebase-bom:33.14.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation(libs.swiperefreshlayout)
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-storage")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}