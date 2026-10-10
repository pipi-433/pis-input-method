plugins {
    id("org.fcitx.fcitx5.android.app-convention")
    id("org.fcitx.fcitx5.android.native-app-convention")
    id("org.fcitx.fcitx5.android.build-metadata")
    id("org.fcitx.fcitx5.android.data-descriptor")
    id("org.fcitx.fcitx5.android.fcitx-component")
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.fcitx.fcitx5.android"

    defaultConfig {
        applicationId = "io.pis.inputmethod"
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                targets(
                    // jni
                    "native-lib",
                    // copy fcitx5 built-in addon libraries
                    "copy-fcitx5-modules",
                    // android specific modules
                    "androidfrontend",
                    "androidkeyboard",
                    "androidnotification"
                )
            }
        }
    }

    buildFeatures {
        viewBinding = true
        resValues = true
    }

    buildTypes {
        release {
            resValue("mipmap", "app_icon", "@drawable/pis_app_icon")
            resValue("mipmap", "app_icon_round", "@drawable/pis_app_icon")
            resValue("string", "app_name", "pis\\'input method")
            proguardFile("proguard-rules.pro")
        }
        debug {
            resValue("mipmap", "app_icon", "@drawable/pis_app_icon")
            resValue("mipmap", "app_icon_round", "@drawable/pis_app_icon")
            resValue("string", "app_name", "pis\\'input method")
        }
    }

    androidResources {
        noCompress += "gguf"
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }
}

val pisTranslationAbi = providers.gradleProperty("buildABI").orElse("arm64-v8a")
val preparePisTranslation by tasks.registering(Exec::class) {
    inputs.dir(rootProject.file("native-translation"))
    inputs.file(rootProject.file("tools/prepare_translation.py"))
    inputs.property("abi", pisTranslationAbi)
    outputs.file(layout.buildDirectory.file(pisTranslationAbi.map { "generated/pis-translation-jni/$it/libpis_translation.so" }))
    outputs.file(layout.buildDirectory.file("generated/pis-translation-assets/translation/Hy-MT2-1.8B-Q4_K_M.gguf"))
    commandLine(providers.gradleProperty("pythonExecutable").orElse("python").get(), rootProject.file("tools/prepare_translation.py"), "--abi", pisTranslationAbi.get())
}
android.sourceSets["main"].apply {
    jniLibs.srcDir(layout.buildDirectory.dir("generated/pis-translation-jni").get().asFile)
    assets.srcDir(layout.buildDirectory.dir("generated/pis-translation-assets").get().asFile)
}
tasks.named("preBuild") { dependsOn(preparePisTranslation) }

val preparePisVoice by tasks.registering(Exec::class) {
    inputs.file(rootProject.file("tools/prepare_voice.py"))
    outputs.file(layout.buildDirectory.file("generated/pis-voice-runtime/sherpa-onnx-1.13.8.aar"))
    outputs.dir(layout.buildDirectory.dir("generated/pis-voice-assets"))
    commandLine(providers.gradleProperty("pythonExecutable").orElse("python").get(), rootProject.file("tools/prepare_voice.py"))
}
android.sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/pis-voice-assets").get().asFile)
tasks.named("preBuild") { dependsOn(preparePisVoice) }

val preparePisWordNet by tasks.registering(Exec::class) {
    inputs.file(rootProject.file("tools/prepare_wordnet.py"))
    outputs.dir(layout.buildDirectory.dir("generated/pis-wordnet-assets"))
    commandLine(providers.gradleProperty("pythonExecutable").orElse("python").get(),rootProject.file("tools/prepare_wordnet.py"))
}
// The current candidate/long-press path uses gloss.db. Keep experimental
// WordNet resources in debug builds only, without inflating the release APK.
android.sourceSets["debug"].assets.srcDir(layout.buildDirectory.dir("generated/pis-wordnet-assets").get().asFile)
tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(preparePisWordNet) }
dependencies {
    implementation(files(layout.buildDirectory.file("generated/pis-voice-runtime/sherpa-onnx-1.13.8.aar")).builtBy(preparePisVoice))
}

fcitxComponent {
    includeLibs = listOf(
        "fcitx5",
        "fcitx5-lua",
        "libime",
        "fcitx5-chinese-addons"
    )
    // exclude (delete immediately after install) tables that nobody would use
    excludeFiles = listOf("cangjie", "erbi", "qxm", "wanfeng").map {
        "usr/share/fcitx5/inputmethod/$it.conf"
    }
    installPrebuiltAssets = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    ksp(project(":codegen"))
    implementation(project(":lib:fcitx5"))
    implementation(project(":lib:fcitx5-lua"))
    implementation(project(":lib:libime"))
    implementation(project(":lib:fcitx5-chinese-addons"))
    implementation(project(":lib:common"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.viewpager2)
    implementation(libs.material)
    implementation(libs.arrow.core)
    implementation(libs.arrow.functions)
    implementation(libs.imagecropper)
    implementation(libs.flexbox)
    implementation(libs.dependency)
    implementation(libs.timber)
    implementation(libs.splitties.bitflags)
    implementation(libs.splitties.dimensions)
    implementation(libs.splitties.resources)
    implementation(libs.splitties.views.dsl)
    implementation(libs.splitties.views.dsl.appcompat)
    implementation(libs.splitties.views.dsl.constraintlayout)
    implementation(libs.splitties.views.dsl.coordinatorlayout)
    implementation(libs.splitties.views.dsl.recyclerview)
    implementation(libs.splitties.views.recyclerview)
    implementation(libs.aboutlibraries.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.lifecycle.testing)
    androidTestImplementation(libs.junit)
}

configurations {
    all {
        // remove Baseline Profile Installer or whatever it is...
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
        // remove unwanted splitties libraries...
        exclude(group = "com.louiscad.splitties", module = "splitties-appctx")
        exclude(group = "com.louiscad.splitties", module = "splitties-systemservices")
    }
}
