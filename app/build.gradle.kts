import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "uz.alifbo.keyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "uz.alifbo.keyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Не встраивать в APK служебный список зависимостей: он не нужен и мешает воспроизводимой сборке.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":converter"))
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
}

/**
 * Клавиатура видит всё, что набирает человек, поэтому у неё не должно быть ни одного разрешения,
 * в первую очередь доступа в интернет. Сборка падает, если разрешение появится — например, вместе
 * с новой библиотекой.
 */
abstract class CheckNoPermissions : DefaultTask() {
    @get:InputFile
    abstract val manifest: RegularFileProperty

    @TaskAction
    fun check() {
        val text = manifest.get().asFile.readText()
        val found = Regex("""<uses-permission(?:-sdk-23)?[^>]*android:name="([^"]+)"""")
            .findAll(text).map { it.groupValues[1] }.toList()
        if (found.isNotEmpty()) {
            throw GradleException("Клавиатура не должна запрашивать разрешения, а в манифесте есть: $found")
        }
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val checkTask = tasks.register<CheckNoPermissions>("check${name}Permissions") {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        tasks.matching { it.name == "assemble$name" || it.name == "bundle$name" || it.name == "check" }
            .configureEach { dependsOn(checkTask) }
    }
}
