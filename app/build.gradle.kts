import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    jacoco
}

jacoco { toolVersion = libs.versions.jacoco.get() }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("11")
    }
}

android {
    namespace = "com.dsalmun.luxalarm"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dsalmun.luxalarm"
        minSdk = 28
        targetSdk = 37
        versionCode = 15
        versionName = "2.4.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // AGP overwrites the `jacoco { }` tool version with this one, so both must agree.
    testCoverage { jacocoVersion = libs.versions.jacoco.get() }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // FakeAlarmRepository and friends are shared between the local and instrumented suites.
    // AGP 9's built-in kotlinc reads the `kotlin` set, not `java`, so register on both.
    sourceSets {
        getByName("test").kotlin.directories.add("src/testShared/java")
        getByName("androidTest").kotlin.directories.add("src/testShared/java")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // org.gradle.jvmargs sizes the daemon, not the forked test JVM, which
                // otherwise defaults to 512m — not enough for Robolectric plus Compose.
                it.maxHeapSize = "2g"
                // Robolectric 4.17 reaches into SharedSecrets to fake FileDescriptors, which
                // recent JDKs no longer export; without this every sandboxed test fails.
                it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
                // Robolectric's sandbox classloader leaves app classes with no code-source
                // location, so without this JaCoCo drops their data and reports 0% coverage.
                it.extensions.configure(JacocoTaskExtension::class.java) {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    // Not testImplementation: Robolectric loads the *merged* debug manifest, which an AAR only
    // reaches from the compile/runtime classpath. Otherwise ComponentActivity is not found.
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    // Required by the shared src/testShared fixtures, which compile into both suites.
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(kotlin("test"))
    androidTestImplementation(libs.androidx.rules)
}

// Generated code — Compose lambda holders and live literals, Room implementations, resources.
val coverageExclusions =
    listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*_Impl*",
        "**/ComposableSingletons*",
        "**/LiveLiterals*",
        "**/ui/theme/**",
    )

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    group = "verification"
    description = "Generates a JaCoCo coverage report for the debug unit tests."
    dependsOn("testDebugUnitTest")

    val buildDirectory = layout.buildDirectory

    // Do NOT also list the pre-AGP-9 build/tmp/kotlin-classes/debug: it lingers from older builds
    // and JaCoCo aborts with "Can't add different class with same name" when both are present.
    classDirectories.setFrom(
        files(
                buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
                buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
            )
            .asFileTree
            .matching { exclude(coverageExclusions) }
    )

    sourceDirectories.setFrom(files("src/main/java"))

    executionData.setFrom(
        files(
                buildDirectory.dir("outputs/unit_test_code_coverage/debugUnitTest"),
                buildDirectory.dir("jacoco"),
            )
            .asFileTree
            .matching { include("**/*.exec") }
    )

    reports {
        html.required = true
        xml.required = true
        csv.required = false
    }
}
