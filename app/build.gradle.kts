import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 版本号只能从根目录的 version.properties 读，写死的数字在这里一律不许出现。
// 注意：读文件这件事在本脚本和 engine 里各有一份（engine 那份是给运行时和测试用的），
// CI 的「自检四」会把两边对一次，防止有人只改一处。
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val versionNameValue = versionProps.getProperty("versionName")
val versionCodeValue = versionProps.getProperty("versionCode").toInt()

android {
    namespace = "com.hualuo.repotool"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hualuo.repotool"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeValue
        versionName = versionNameValue
    }

    buildTypes {
        // CI 只打 debug 包（用系统自带的调试签名，不设任何密钥）
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig 用来把版本号交给界面显示
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // 安卓这边只是个壳：能看见的界面 + 权限；真正的逻辑都在 engine 里
    implementation(project(":engine"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
}

// 供 CI 核对"版本号只有一个来源"：打印出真正生效的版本号，和文件里对一次
tasks.register("printVersion") {
    group = "hualuo"
    description = "打印构建实际使用的版本号"
    doLast {
        println("versionName=$versionNameValue")
    }
}
