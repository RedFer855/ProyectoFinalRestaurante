import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.example.proyectofinalrestaurante"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.proyectofinalrestaurante"
        // API 24 cubre ~96.6% de dispositivos reales (abril 2026) vs. ~0% en API 37.
        // Ver P-003 en contexto/40 - Proyecto Restaurante/Deuda Técnica - Pendientes.md
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL", "")}\"")
        // Supabase deprecó el nombre "anon key" a favor de "publishable key" a fines de
        // 2026; el valor no cambió, solo el nombre (ver P-012 en Deuda Técnica - Pendientes).
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY", "")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        // Java 17: lo exigen AGP/Gradle 9.x y es requisito para adoptar Hilt (P-006).
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Con minSdk 24, varias APIs de java.time/java.util.stream necesitan desugaring
        // para funcionar por debajo de API 26 (P-003).
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.drawerlayout)
    implementation(libs.material)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    testImplementation(libs.junit)
    testImplementation(libs.core.testing)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}