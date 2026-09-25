package com.hualuo.repotool.ui.data

import com.hualuo.repotool.ui.model.IconKey
import com.hualuo.repotool.ui.model.SettingsItem
import com.hualuo.repotool.ui.model.SettingsSection
import com.hualuo.repotool.ui.model.SubField
import com.hualuo.repotool.ui.model.SubPage
import com.hualuo.repotool.ui.state.ChatRuntime
import com.hualuo.repotool.ui.state.UiKeys

/**
 * 已经接真电的设置项，和演示表分开放。
 */
const val RETRY_COSTLY_KEY = "ui.retry_costly_on_gateway"
const val RETRY_COSTLY_DEFAULT = false

val RealSubPages: Map<String, SubPage> = mapOf(
    "imagegen" to SubPage(
        "图像生成",
        listOf(
            SubField.PersistedText("API 密钥", UiKeys.IMAGE_GEN_KEY, "OpenAI 兼容 /images/generations 的钥匙；配了生成工具就出现", secret = true),
            SubField.PersistedText("API 地址", UiKeys.IMAGE_GEN_BASE_URL, "默认 https://api.openai.com/v1（兼容端点填到 v1 为止）"),
            SubField.PersistedText("模型", UiKeys.IMAGE_GEN_MODEL, "默认 gpt-image-1"),
            SubField.PersistedText("尺寸", UiKeys.IMAGE_GEN_SIZE, "默认 1024x1024"),
            SubField.Note("出图存到应用目录 tool_images 下，回执里报路径。"),
        ),
    ),
    "retry" to SubPage(
        "失败与重试",
        listOf(
            SubField.PersistedSwitch("网关掐了自动重发一次", RETRY_COSTLY_KEY, RETRY_COSTLY_DEFAULT),
            SubField.Note("开了它：502、504、524 这类网关失败会自动再来一次，代价是这次请求会再花一遍 token。关着（默认）：不替你花钱，把失败原因摊开给你看，重试按钮在你手上。"),
            SubField.Note("模型已经吐过字的半截回答，以及上下文超限，一律不重发。"),
        ),
    ),
    "provider" to SubPage("提供商", listOf(SubField.ProviderSettings)),
    "model" to SubPage("模型", listOf(SubField.ModelSettings)),
    "prompt" to SubPage(
        "系统指令",
        listOf(
            SubField.PersistedText("系统指令", ChatRuntime.KEY_SYSTEM_PROMPT, "比如：回答一律中文；留空就不发这条"),
            SubField.Note("这段话作为 system 消息放在每次请求的最前面，空着就不发。"),
        ),
    ),
    "trim" to SubPage(
        "历史裁剪",
        listOf(
            SubField.PersistedText("一次最多带几条历史", UiKeys.MAX_HISTORY, "数字，默认 40（范围 1-500）"),
            SubField.Note("超了就砍最旧的，砍了几条会当场出声。"),
        ),
    ),
    "github" to SubPage(
        "GitHub 工作台",
        listOf(
            SubField.GithubLogin,
            SubField.PersistedText("仓库", UiKeys.GITHUB_REPO, "owner/name，粘整条链接也认"),
            SubField.PersistedSwitch("CI 提醒（后台轮询，红绿出通知）", UiKeys.CI_NOTIFY, true),
            SubField.Note("令牌只进请求头，不进报错、日志与界面文本。"),
        ),
    ),
    "courier" to SubPage(
        "文件投递",
        listOf(
            SubField.PersistedText("目标仓库", UiKeys.COURIER_REPO, "owner/name，粘整条仓库链接也认"),
            SubField.PersistedText("分支", UiKeys.COURIER_BRANCH, "默认 main"),
            SubField.PersistedText("访问令牌", UiKeys.COURIER_TOKEN, "私有仓库必填；留空借用 GitHub 工作台的令牌", secret = true),
            SubField.Note("按文件边界打包分卷，逐卷上传，manifest 全账。"),
        ),
    ),
    "datactl" to SubPage(
        "数据控制",
        listOf(
            SubField.Button("导出备份", actionKey = "export"),
            SubField.Button("导入备份（覆盖同名设置，重名会话跳过）", actionKey = "import"),
            SubField.Button("导入旧 Agora 备份（.agora）", actionKey = "import_agora"),
            SubField.Note("本家备份包含设置与会话库。"),
        ),
    ),
)

val RealSectionAdditions: Map<String, List<SettingsItem>> = mapOf(
    "s-net" to listOf(
        SettingsItem(IconKey.SettingsRetry, "失败与重试", "网关把连接掐了怎么办；要不要自动重发", null, "retry"),
    ),
    "s-data" to listOf(
        SettingsItem(IconKey.Clip, "文件投递", "把手机上的文件分卷投进私有仓，manifest 全账", null, "courier"),
    ),
)

fun mergedSettingsSections(): List<SettingsSection> = SettingsSections.map { section ->
    val extra = RealSectionAdditions[section.id].orEmpty()
    if (extra.isEmpty()) section else section.copy(items = section.items + extra)
}

fun orphanAdditions(): List<SettingsItem> {
    val known = SettingsSections.map { it.id }.toSet()
    return RealSectionAdditions.filterKeys { it !in known }.values.flatten()
}

fun subPage(key: String): SubPage? = RealSubPages[key] ?: SubPages[key]
