plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        licenseHeaderFile(rootProject.file("config/license-header.txt"))
        ktfmt().kotlinlangStyle()
    }
}
