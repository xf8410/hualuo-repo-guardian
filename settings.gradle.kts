pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// 依赖仓库只许在这里写一次，子项目里再写就直接报错（防止各处私自加源）
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "hualuo-repo-tool"

// engine = 不依赖安卓的纯逻辑，能单独跑测试；app = 手机上装的那个壳
include(":engine", ":app")
