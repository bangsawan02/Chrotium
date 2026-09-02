plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  // alias(libs.plugins.kotlin.serialization)
  // alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  id("com.ncorti.ktfmt.gradle")
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "org.matrix.chromext"
    minSdk = 30
    targetSdk = 34
    versionCode = 19
    versionName = "3.8.5"

    androidResources {
        localeFilters += listOf("en", "id")
        additionalParameters += listOf("--allow-reserved-package-id", "--package-id", "0x42")
    }
  }

  signingConfigs {
    create("release") {
      val testKeystore = file("${rootDir}/release-test.jks")
      val customKeystorePath = System.getenv("KEYSTORE_PATH")
      if (customKeystorePath != null && file(customKeystorePath).exists()) {
        storeFile = file(customKeystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else if (testKeystore.exists()) {
        storeFile = testKeystore
        storePassword = System.getenv("STORE_PASSWORD") ?: "androidtest"
        keyAlias = System.getenv("KEY_ALIAS") ?: "testrelease"
        keyPassword = System.getenv("KEY_PASSWORD") ?: "androidtest"
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isShrinkResources = true
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      isDebuggable = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }

  packaging {
    resources {
      excludes += listOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "/META-INF/*.version",
        "/META-INF/DEPENDENCIES",
        "/META-INF/LICENSE*",
        "/META-INF/NOTICE*",
        "/META-INF/*.kotlin_module",
        "DebugProbesKt.bin",
        "*.proto"
      )
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildFeatures {
    compose = true
    buildConfig = true
    aidl = false
    shaders = false
    resValues = false
  }

  lint {
    checkReleaseBuilds = false
    abortOnError = false
    disable +=
        listOf(
            "Internationalization",
            "UnsafeIntentLaunch",
            "SetJavaScriptEnabled",
            "UnspecifiedRegisterReceiverFlag",
            "Usability:Icons")
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Optimasi untuk mengurangi task gradle debug (menonaktifkan unit test, android test, dan lint pada varian debug agar build lebih cepat)
tasks.configureEach {
  if (name.contains("Debug", ignoreCase = true)) {
    if (name.contains("UnitTest", ignoreCase = true) || 
        name.contains("AndroidTest", ignoreCase = true) || 
        name.contains("Lint", ignoreCase = true)) {
      enabled = false
    }
  }
}

// ksp {
//   arg("room.schemaLocation", "$projectDir/schemas")
//   arg("room.incremental", "true")
//   arg("room.expandProjection", "true")
// }

dependencies {
  // compileOnly("de.robv.android.xposed:api:82")
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // Unused external dependencies commented out to optimize APK size and prioritize native APIs
  // implementation(libs.coil.compose)
  // implementation(libs.androidx.datastore.preferences)
  // implementation(libs.kotlinx.serialization.json)
  // implementation(libs.okhttp)
  // implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.quickjs.android)

  // Unused test dependencies commented out for build speed optimization
  // testImplementation(libs.androidx.compose.ui.test.junit4)
  // testImplementation(libs.androidx.core)
  // testImplementation(libs.androidx.junit)
  // testImplementation(libs.junit)
  // testImplementation(libs.kotlinx.coroutines.test)
  // testImplementation(libs.robolectric)
  // testImplementation(libs.roborazzi)
  // testImplementation(libs.roborazzi.compose)
  // testImplementation(libs.roborazzi.junit.rule)
  // debugImplementation(libs.androidx.compose.ui.test.manifest)
  // "ksp"(libs.androidx.room.compiler)
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xbackend-threads=4"
        )
    }
}

// Placeholder task to prevent '-x lintVitalRelease' from failing in environments with checkReleaseBuilds disabled
tasks.register("lintVitalRelease") {
    group = "verification"
    description = "Placeholder task for lintVitalRelease"
}

