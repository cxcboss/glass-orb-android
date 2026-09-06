plugins {
    id("com.android.application")
}

android {
    namespace = "com.cxcboss.glassorb"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cxcboss.glassorb"
        // Android 10 is the lowest supported platform for the modern View
        // surface and edge-to-edge/window insets behavior used by the app.
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "1.5.0-demo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    sourceSets.getByName("main").assets.directories.add(rootProject.file("licenses").absolutePath)

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Official Material 3 Views provide current Android controls without a
    // Compose UI stack in this small native settings surface.
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
