import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    // 纯 Kotlin 逻辑，不碰安卓：可以在电脑上单独跑测试，比安卓测试快得多。
    // 上传、解压、校验、路径这些"容易出错又必须对"的代码都放这里。
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    // 只要运行时库，不要编译器插件：这里全部用 Json.parseToJsonElement 动态读，
    // 没有 @Serializable 数据类，所以不引 kotlin 序列化插件。
    // 为什么必须引：提供商返回的错误体形状五花八门（error.message / error 是字符串 /
    // detail / reason / error_description / 顶层 code+type / 网关直接吐 HTML），
    // 手写 JSON 扫描器正是最容易出 bug 的地方，而这段代码要处理的是**不可信输入**。
    // 后面搬对话请求体组装时同一份库还要用，不重复引。
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
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
