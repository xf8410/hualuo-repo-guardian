import org.gradle.api.tasks.testing.logging.TestExceptionFormat

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
        // 红必须自带原因：默认短格式只打 "java.lang.AssertionError at 文件:行"，
        // 断言里写的消息文本一个字都不显示 —— M1 连红两轮查不出根因就是卡在这。
        // 旧 Agora 用 continue-on-error + tail 截断藏错误，这是同一类病的轻量版。
        exceptionFormat = TestExceptionFormat.FULL
    }
}
