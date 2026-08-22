// Top-level build file where you can add configuration options common to all sub-projects/modules.
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  id("com.ncorti.ktfmt.gradle") version "0.25.0"
}

tasks.register<KtfmtFormatTask>("format") {
  source = project.fileTree(rootDir)
  include("*.gradle.kts", "app/*.gradle.kts")
  dependsOn(":app:ktfmtFormat")
}
