import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val lyricsApiBase = providers.gradleProperty("NEO_LYRICS_API_BASE").orElse("").get()
val lyricsApiKey = providers.gradleProperty("NEO_LYRICS_API_KEY").orElse("").get()
fun quotedBuildValue(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val releaseStoreFilePath = providers.gradleProperty("NEO_RELEASE_STORE_FILE").orNull
    ?: System.getenv("NEO_RELEASE_STORE_FILE")
val releaseStorePassword = providers.gradleProperty("NEO_RELEASE_STORE_PASSWORD").orNull
    ?: System.getenv("NEO_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.gradleProperty("NEO_RELEASE_KEY_ALIAS").orNull
    ?: System.getenv("NEO_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.gradleProperty("NEO_RELEASE_KEY_PASSWORD").orNull
    ?: System.getenv("NEO_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

// The actual mobile models are intentionally embedded into the APK, not downloaded at runtime.
// Build-time download is checksum-pinned and cached under GRADLE_USER_HOME. This keeps Track+ fully
// offline after installation while avoiding an empty model AAR and supports both English + Persian.
data class VoskAssetModel(val assetDir: String, val archiveName: String, val url: String, val sha256: String)

val generatedVoskAssetsDir = layout.buildDirectory.dir("generated/vosk-model-assets")
val prepareVoskOfflineModels by tasks.registering {
    val models = listOf(
        VoskAssetModel(
            assetDir = "model-en-us",
            archiveName = "vosk-model-small-en-us-0.15.zip",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            sha256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
        ),
        VoskAssetModel(
            assetDir = "model-fa",
            archiveName = "vosk-model-small-fa-0.42.zip",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip",
            sha256 = "977cb5faa538f3a835ccfd35f5f6d8284b5c450b89c700b9bd4736b66536ad46"
        )
    )
    outputs.dir(generatedVoskAssetsDir)
    doLast {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }

        val outputRoot = generatedVoskAssetsDir.get().asFile.apply { mkdirs() }
        val cacheRoot = File(gradle.gradleUserHomeDir, "caches/neo-player-vosk-models").apply { mkdirs() }
        models.forEach { model ->
            val target = File(outputRoot, model.assetDir)
            val uuid = File(target, "uuid")
            if (target.isDirectory && uuid.isFile && uuid.readText().trim() == model.sha256) return@forEach

            val archive = File(cacheRoot, model.archiveName)
            if (!archive.isFile || sha256(archive) != model.sha256) {
                archive.delete()
                logger.lifecycle("Downloading pinned offline speech model: ${model.archiveName}")
                URI(model.url).toURL().openStream().buffered().use { input ->
                    archive.outputStream().buffered().use { output -> input.copyTo(output, 128 * 1024) }
                }
            }
            val actual = sha256(archive)
            if (actual != model.sha256) {
                archive.delete()
                throw GradleException("Checksum mismatch for ${model.archiveName}: $actual")
            }

            target.deleteRecursively()
            target.mkdirs()
            val canonicalRoot = target.canonicalPath + File.separator
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val normalized = entry.name.replace('\\', '/')
                    val firstSlash = normalized.indexOf('/')
                    val relative = if (firstSlash >= 0) normalized.substring(firstSlash + 1) else ""
                    if (relative.isNotBlank()) {
                        val out = File(target, relative)
                        if (!out.canonicalPath.startsWith(canonicalRoot)) throw GradleException("Unsafe path in ${model.archiveName}")
                        if (entry.isDirectory) out.mkdirs() else {
                            out.parentFile?.mkdirs()
                            out.outputStream().buffered().use { stream -> zip.copyTo(stream, 128 * 1024) }
                        }
                    }
                    zip.closeEntry()
                }
            }
            if (!File(target, "am/final.mdl").isFile || !File(target, "conf").isDirectory) {
                target.deleteRecursively()
                throw GradleException("Downloaded ${model.archiveName} is not a compatible Vosk model")
            }
            uuid.writeText(model.sha256)
        }
    }
}

android {
    namespace = "com.neoplayer.app"
    compileSdk = 36
    sourceSets.getByName("main").assets.srcDir(generatedVoskAssetsDir)

    defaultConfig {
        applicationId = "com.neoplayer.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "DISPLAY_VERSION", "\"Alpha 0.5\"")
        buildConfigField("String", "LYRICS_API_BASE", quotedBuildValue(lyricsApiBase))
        buildConfigField("String", "LYRICS_API_KEY", quotedBuildValue(lyricsApiKey))
        buildConfigField("boolean", "SIGNED_RELEASE", hasReleaseSigning.toString())
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFilePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Deliberately keep all code/resources for this additive milestone. R8 can be enabled in
            // a later production-size pass after device QA; nothing is stripped merely to shrink APK.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    testOptions.unitTests.isIncludeAndroidResources = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareVoskOfflineModels) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")
    implementation("androidx.paging:paging-runtime:3.3.2")
    implementation("androidx.paging:paging-compose:3.3.2")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.guava:guava:33.3.1-android")

    // Fully offline speech-to-text. English and Persian mobile models are embedded into the APK;
    // additional Vosk models can be imported locally from ZIP without any NEO server/account.
    implementation("com.alphacephei:vosk-android:0.3.75@aar") {
        // Vosk historically pulled older JNA Android natives. Keep every ABI, but force the newer
        // Android build so 16 KB page-size compliance is verifiable instead of dropping x86_64.
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation("net.java.dev.jna:jna:5.18.1@aar")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.sqlite:sqlite-framework:2.4.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
