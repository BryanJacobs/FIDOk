plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.compose.compiler)
}

dependencies {
    repositories {
        google()
        mavenCentral()
    }
}

android {
    namespace = "us.q3q.fidok.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "us.q3q.fidok"
        // minSdk = 23 FIXME
        minSdk = 31
        targetSdk = 33
        versionCode = 1
        versionName = project.version.toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composecompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildToolsVersion = "34.0.0"
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":fidok-android"))
    implementation(project(":fidok"))
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)
    implementation(libs.activity.ktx)
    implementation(libs.fragment.ktx)
    implementation(platform("androidx.compose:compose-bom:${libs.versions.composebom.get()}"))
    implementation(libs.bundles.compose)
    implementation("androidx.compose.material3:material3")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform("androidx.compose:compose-bom:${libs.versions.composebom.get()}"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    implementation(libs.androidx.runtime.livedata)

    implementation(libs.kermit)
}
