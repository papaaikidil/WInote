import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Nomor versi naik otomatis setiap kali GitHub merakit ulang (WINOTE_BUILD diisi alur rakit; lanjut dari 0.1.110).
val buildNumber = (System.getenv("WINOTE_BUILD") ?: System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

// Kunci tanda tangan TIDAK disimpan terbuka di repository ini. Alur rakit membuka app/kunci.enc dengan rahasia
// KUNCI_RAKIT ke folder app/kunci/ (diabaikan git). Tanpa kunci itu, rakitan memakai kunci debug biasa.
val kunciProps = Properties().apply {
    val f = file("kunci/kunci.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val adaKunci = file("kunci/hana-note.jks").exists() && kunciProps.getProperty("storePassword") != null

android {
    namespace = "id.hananote"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.hananote.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        // Tablet dan HP Android memakai prosesor ARM; versi untuk emulator dibuang supaya APK lebih kecil.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Kunci tanda tangan yang sama untuk setiap rakitan, supaya pembaruan
    // bisa dipasang menimpa versi lama tanpa menghapus catatan.
    signingConfigs {
        if (adaKunci) {
            create("hana") {
                storeFile = file("kunci/hana-note.jks")
                storePassword = kunciProps.getProperty("storePassword")
                keyAlias = kunciProps.getProperty("keyAlias")
                keyPassword = kunciProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Kode yang tidak terpakai dibuang supaya APK kecil dan aplikasi lebih ringan.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (adaKunci) signingConfigs.getByName("hana") else signingConfigs.getByName("debug")
        }
        debug {
            if (adaKunci) signingConfig = signingConfigs.getByName("hana")
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
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    // Perkiraan gerak pena: ujung tulisan digambar sedikit di depan supaya terasa menempel pada pena.
    implementation("androidx.input:input-motionprediction:1.0.0-beta05")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Pengenal tulisan tangan di perangkat (bahasa Indonesia, bisa offline setelah kamus terunduh).
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
    implementation("com.google.mlkit:language-id:17.0.6")
    // Face Paint: kamera depan (CameraX) dan jaring wajah 468 titik (ML Kit, model di dalam APK).
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("com.google.mlkit:face-mesh-detection:16.0.0-beta1")
    // Hapus latar gambar: objek utama dikenali otomatis (komponen diunduh lewat Layanan Google Play).
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    implementation("com.google.android.gms:play-services-base:18.5.0")
    // Edit PDF di catatan: membaca teks PDF beserta posisinya (pilih, stabilo, ubah teks) dan kolom formulir.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
