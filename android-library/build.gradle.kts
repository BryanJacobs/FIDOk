plugins {
    id("com.android.library")
    kotlin("android")
    id("com.vanniktech.maven.publish") version "0.27.0"
}

dependencies {
    repositories {
        google()
        mavenCentral()
    }
}

android {
    namespace = "us.q3q.fidok.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
        // minSdk = 31
        lint {
            targetSdk = 33
        }

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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildToolsVersion = "34.0.0"
}

dependencies {
    implementation(project(":fidok"))
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)

    implementation(libs.kermit)
}
