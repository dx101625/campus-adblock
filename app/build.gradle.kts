plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.campus.adblock"
    sourceSets.getByName("main").manifest.srcFile(rootProject.file("build/generated/miuix/AndroidManifest.xml"))
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    buildToolsVersion = "37.0.0"

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("signing/release.p12")
            storePassword = (rootProject.file("signing/password.txt").takeIf { it.exists() }?.readText()?.trim()) ?: System.getenv("CAMPUS_STORE_PASSWORD")
            keyAlias = "campus"
            keyPassword = storePassword
            enableV3Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.campus.adblock"
        minSdk = 24
        targetSdk = 34
        versionCode = providers.environmentVariable("CAMPUS_VERSION_CODE").orElse("25").get().toInt()
        versionName = providers.environmentVariable("CAMPUS_VERSION").orElse("1.6.2").get()
    }

    buildTypes {
        release {
            signingConfig = if (providers.gradleProperty("campusNoSign").isPresent) null else signingConfigs.getByName("release")
            optimization.enable = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    compileOnly(files(providers.environmentVariable("CAMPUS_XPOSED_API").orElse(rootProject.file("tools/libxposed-api/classes.jar").absolutePath).get()))
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("io.github.libxposed:service:102.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
