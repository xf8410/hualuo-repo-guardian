// 插件版本集中写在这一处，子项目里只写「用哪个」，不写版本。
// 想升级插件就改这里，改一处就够（旧 Agora 是版本写三处，每次都漏改一处变红）。
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
}
