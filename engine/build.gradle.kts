plugins {
    // 纯 Kotlin 逻辑，不碰安卓：可以在电脑上单独跑测试，比安卓测试快得多。
    // 上传、解压、校验、路径这些"容易出错又必须对"的代码都放这里。
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    // 把每条测试的名字打到 CI 日志里（测试名用中文写的，直接看得懂过了什么）
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}
