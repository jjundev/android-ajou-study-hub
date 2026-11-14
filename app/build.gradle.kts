plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.jjundev.ajoustudyhub"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jjundev.ajoustudyhub"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val studyHubUrl: String = providers.gradleProperty("studyHubUrl")
            .getOrElse("https://ajou-study-hub.johnshin617.workers.dev/")
        buildConfigField("String", "STUDY_HUB_URL", "\"$studyHubUrl\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

val jdkHttpServerJar = tasks.register<Jar>("jdkHttpServerJar") {
    archiveFileName.set("jdk-httpserver.jar")
    destinationDirectory.set(layout.buildDirectory.dir("jdk-httpserver"))
    val javaHome = System.getProperty("java.home")
    val jmod = File(javaHome, "jmods/jdk.httpserver.jmod")
    if (jmod.exists()) {
        from(zipTree(jmod)) {
            include("classes/**")
            eachFile {
                path = path.removePrefix("classes/")
            }
            includeEmptyDirs = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.webkit)
    implementation(libs.material)

    testImplementation(files(jdkHttpServerJar))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
