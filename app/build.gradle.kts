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

        javaCompileOptions {
            annotationProcessorOptions {
                // Room exporta el esquema a JSON versionado (app/schemas) para que las
                // migraciones sean testeables con MigrationTestHelper (Plan Fase 2b, E1).
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
    // Robolectric lee los assets del variant debug (android_merged_assets =
    // mergeDebugAssets), no los del source set test: por eso el esquema de Room se
    // agrega acá y no en test (issue robolectric/robolectric#3928). Así además el
    // esquema nunca entra al APK de release.
    sourceSets["debug"].assets.srcDir("$projectDir/schemas")

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
    testOptions {
        unitTests {
            // Necesario para Robolectric (shadows con resources reales) y para que
            // MigrationTestHelper pueda abrir app/schemas/*.json desde los unit tests.
            isIncludeAndroidResources = true
        }
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
    // Pull-to-refresh del menú (E7): "bajar para sincronizar" (Plan Fase 2b, §5.6).
    implementation(libs.swiperefreshlayout)
    implementation(libs.material)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    // ProcessLifecycleOwner: resync al volver la app a primer plano (ver SyncApplication).
    implementation(libs.lifecycle.process)
    // Carga y cache de las fotos de los platillos. Sin el annotationProcessor de Glide:
    // con Glide.with(...) alcanza y se evita un procesador de anotaciones en el build.
    implementation(libs.glide)
    // Rota la foto segun su EXIF antes de subirla; si no, las de camara salen acostadas.
    implementation(libs.exifinterface)
    // Base local offline-first (Plan Fase 2b): Room es la unica fuente de verdad de la UI.
    implementation(libs.room.runtime)
    // Room no funciona sin su procesador de anotaciones: los DAOs son codigo generado.
    annotationProcessor(libs.room.compiler)
    // Drena el outbox en segundo plano con Constraints.NETWORK_CONNECTED.
    implementation(libs.work.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.core.testing)
    testImplementation(libs.robolectric)
    // MigrationTestHelper: prueba cada migracion contra el esquema versionado.
    testImplementation(libs.room.testing)
    // Verifica lo que SyncScheduler encola de verdad, contra un WorkManager real en memoria.
    testImplementation(libs.work.testing)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}