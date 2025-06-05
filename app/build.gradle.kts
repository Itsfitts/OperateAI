plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "1.9.22"
    id("kotlin-kapt")
}

android {
    namespace = "com.ai.assistance.operit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ai.assistance.operit"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE-EPL-1.0.txt"
            excludes += "LICENSE-EPL-1.0.txt"
            excludes += "/META-INF/LICENSE-EDL-1.0.txt"
            excludes += "LICENSE-EDL-1.0.txt"
            
            // Resolve merge conflicts for document libraries
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "META-INF/versions/9/module-info.class"
            
            // Fix for duplicate Netty files
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/INDEX.LIST"
            
            // Fix for any other potential duplicate files
            pickFirsts += "**/*.so"
        }
    }
}

dependencies {
    implementation(libs.androidx.ui.graphics.android)
    implementation(files("libs\\ffmpegkit.jar"))
    // Desugaring support for modern Java APIs on older Android
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // APK解析和修改库
    implementation("com.android.tools.build:apksig:8.1.0") // APK签名工具
    implementation("net.dongliu:apk-parser:2.6.10") // 用于解析和处理AndroidManifest.xml
    implementation("com.github.Sable:axml:2.0.0") // 用于Android二进制XML的读写
    implementation("com.github.iyxan23:zipalign-java:1.2.1") // 用于处理ZIP文件对齐
    implementation("com.github.rushiiMachine:arsc:master-SNAPSHOT") // 用于解析和处理Android资源文件
    
    // ZIP处理库 - 用于APK解压和重打包
    implementation("org.apache.commons:commons-compress:1.25.0")
    implementation("commons-io:commons-io:2.13.0") // 添加Apache Commons IO
    
    // 图片处理库
    implementation("com.github.bumptech.glide:glide:4.16.0") // 用于处理图像
    
    // XML处理
    implementation("androidx.core:core-ktx:1.12.0")
    
    // libsu - root access library
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    
    // Add missing SVG support
    implementation("com.caverock:androidsvg-aar:1.4")
    
    // Add missing GIF support for Markwon
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.28")
    
    // Image Cropper for background image cropping
    implementation("com.vanniktech:android-image-cropper:4.5.0")
    
    // ExoPlayer for video background
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
    
    // Material 3 Window Size Class
    implementation("androidx.compose.material3:material3-window-size-class:1.2.0")
    
    // Window metrics library for foldables and adaptive layouts
    implementation("androidx.window:window:1.1.0")
    
    // Document conversion libraries
    implementation("com.itextpdf:itextpdf:5.5.13.3") // iText for PDF creation
    implementation("org.apache.pdfbox:pdfbox:2.0.27") // PDFBox for PDF operations
    
    // Markdown rendering libraries - 使用自定义的Markwon，不使用compose-markdown库
    // implementation("com.github.jeziellago:compose-markdown:0.5.7") {
    //     exclude(group = "io.noties.markwon")  // Exclude all Markwon dependencies from compose-markdown
    // }
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("io.noties.markwon:image-coil:4.6.2")
    
    // 图片加载库
    implementation("io.coil-kt:coil:2.5.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // LaTeX rendering libraries
    implementation("ru.noties:jlatexmath-android:0.2.0")
    implementation("com.github.tech-pw:RenderX:1.0.0") // RenderX library for LaTeX rendering
    
    // Base Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.lifecycle.runtime.ktx)

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Ktor dependencies
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-client-auth:2.3.7")
    implementation("io.ktor:ktor-client-logging:2.3.7")
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-client-websockets:2.3.7")
    implementation("io.ktor:ktor-server-websockets:2.3.7")
    implementation("io.ktor:ktor-server-status-pages:2.3.7")
    
    // Server-Sent Events (SSE) dependencies for Ktor
    implementation("io.ktor:ktor-client-core-jvm:2.3.7")
    implementation("io.ktor:ktor-server-servlet-jvm:2.3.7")
    
    // Additional Ktor dependencies for IO operations
    implementation("io.ktor:ktor-io:2.3.7")
    
    // UUID dependencies
    implementation("com.benasher44:uuid:0.8.2")
    
    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // HJSON dependency for human-friendly JSON parsing
    implementation("org.hjson:hjson:3.0.0")

    // 中文分词库 - Jieba Android
    implementation("com.huaban:jieba-analysis:1.0.2")

    // 向量搜索库 - 轻量级实现，适合Android
    implementation("com.github.jelmerk:hnswlib-core:0.0.46")
    implementation("com.github.jelmerk:hnswlib-utils:0.0.46")
    
    // 用于向量嵌入的TF Lite (如果需要自定义嵌入)
    implementation("org.tensorflow:tensorflow-lite:2.8.0")

    // BouncyCastle加密库 - 用于PKCS12密钥处理
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    // Room 数据库
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1") // Kotlin扩展和协程支持
    kapt("androidx.room:room-compiler:2.6.1") // 使用kapt代替ksp

    // FFmpeg dependency with specific architectures
    // implementation("com.arthenica:ffmpeg-kit-full:6.0-2") {
        // exclude(group = "com.arthenica", module = "ffmpeg-kit-android-lib-armeabi-v7a")
        // exclude(group = "com.arthenica", module = "ffmpeg-kit-android-lib-x86")
    // }

    // Archive/compression libraries
    implementation("org.apache.commons:commons-compress:1.24.0")
    implementation("com.github.junrar:junrar:7.5.5")

    // Compose dependencies - use BOM for version consistency
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    // Use BOM version for all Compose dependencies
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Shizuku dependencies
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Network dependencies
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.16.2")

    // DataStore dependencies
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.datastore:datastore-preferences-core:1.0.0")

    // Debug dependencies
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))

    // Apache POI - for Document processing (DOC, DOCX, etc.)
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("org.apache.poi:poi-scratchpad:5.2.3")

    // Kotlin logging
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    // Color picker for theme customization
    implementation("com.github.skydoves:colorpicker-compose:1.0.6")
    
    // NanoHTTPD for local web server
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // 添加测试依赖
    testImplementation(libs.junit)
    
    // Android测试依赖
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    
    // 协程测试依赖
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    // 模拟测试框架
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    androidTestImplementation("org.mockito:mockito-android:5.2.0")
}