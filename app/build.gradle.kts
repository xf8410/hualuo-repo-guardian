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

// 签名（2026-09-16 拍板「随便弄一个，要能覆盖安装」）：
// 覆盖安装认的是**钥匙**不是版本号——换了钥匙系统直接拒装，只能卸载重装（数据全丢）。
// 所以钥匙由 CI 首跑铸造一次并提交回私有仓（见 .github/workflows/ci.yml「密钥首铸」），
// 此后每一次 release 构建都用同一把，装新包永远是覆盖升级。
// 代价说清楚：私有仓里谁有读权限谁就能签这个 App——自己用的仓，认这个边界。
val signingPropsFile = rootProject.file("signing/signing.properties")
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) signingPropsFile.inputStream().use { load(it) }
}

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

    if (signingPropsFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // release：钥匙在就签（正式覆盖安装包）；钥匙不在（有人把 signing/ 删了）就不签，
        // 构建照常走完但 CI 的 apksigner 对证会当场红——不许没签的包冒充发布包。
        release {
            isMinifyEnabled = false
            if (signingPropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // debug 照旧系统调试签名：调试密钥在各台机器上不保证一致，
        // 所以「装来用」的包一律认 release 签名版，debug 只做测试。
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
    // SAF 目录树递归收集（文件投递 B 段）：DocumentFile 负责 tree URI 的列目录、stat 与开流
    implementation("androidx.documentfile:documentfile:1.0.1")
    // CI 红绿提醒的后台轮询（2026-09-16 接回旧 Agora 魔改版就有的功能）：
    // WorkManager 15 分钟一拍，不引入前台服务（manifest 纪律：永远没有 <service>）
    implementation("androidx.work:work-runtime-ktx:2.9.1")
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
