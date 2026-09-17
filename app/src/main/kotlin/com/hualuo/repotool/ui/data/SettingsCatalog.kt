package com.hualuo.repotool.ui.data

import com.hualuo.repotool.ui.model.IconKey
import com.hualuo.repotool.ui.model.SettingsItem
import com.hualuo.repotool.ui.model.SettingsSection
import com.hualuo.repotool.ui.model.SubField
import com.hualuo.repotool.ui.model.SubPage
import com.hualuo.repotool.ui.state.ChatRuntime
import com.hualuo.repotool.ui.state.UiKeys

/**
 * 「已经接真电」的设置项，和演示表（SettingsData.kt，照抄原型 v13.1）分开放。
 *
 * 为什么分两个文件：演示表是原型的逐行翻译，改它就得同时对原型；而这里每一项都对应设置文件里
 * 一个真键名，关掉 App 再开还在。合并在渲染时做，两边互不污染。
 *
 * 规矩一：能接真电的项一律用 PersistedSwitch / PersistedText / 带动作键的 Button，不许再用演示态 ——
 * 后者状态只活在 remember 里，拿它冒充设置就是「绿勾勾撒谎」的同款病。
 * 规矩二：图标只写 IconKey 键名，字形住 res/values/icons.xml（家规，闸门 NoEmojiInSourceTest）。
 * 规矩三：键名跟着**用它的运行层**要（provider 四键来自 ChatRuntime 的常量），
 * 表里再造一份字面量就是给下一次改键留的双源坑。
 */

/** 网关失败要不要自动重发一次的键名（进过真机就不许改，改了老设置读不到）。 */
const val RETRY_COSTLY_KEY = "ui.retry_costly_on_gateway"

/** 默认关：不拿用户的 token 赌运气；失败时给原因，重试按钮在用户手上。 */
const val RETRY_COSTLY_DEFAULT = false

/** 真设置子页（键名到页面）。查表走 subPage()，别直接读这张表。 */
val RealSubPages: Map<String, SubPage> = mapOf(
    "retry" to SubPage(
        "失败与重试",
        listOf(
            SubField.PersistedSwitch(
                "网关掐了自动重发一次",
                RETRY_COSTLY_KEY,
                RETRY_COSTLY_DEFAULT,
            ),
            SubField.Note(
                "开了它：502、504、524 这类网关失败会自动再来一次，代价是这次请求会再花一遍 token。" +
                    "关着（默认）：不替你花钱，把失败原因摊开给你看，重试按钮在你手上。",
            ),
            SubField.Note(
                "两种情况一律不重发，跟开关无关：模型已经吐过字的半截回答（重发会内容重复），" +
                    "以及上下文超限（重发同一份内容只会再错一次）。",
            ),
        ),
    ),
    // 覆盖演示表里那张「提供商」页：现在发送真的走这三格填的东西。
    "provider" to SubPage(
        "提供商",
        listOf(
            SubField.PersistedText(
                "端点名",
                ChatRuntime.KEY_NAME,
                "比如：bai2 网关",
            ),
            SubField.PersistedText(
                "base URL",
                ChatRuntime.KEY_BASE_URL,
                "比如：https://api.example.com/v1",
            ),
            SubField.PersistedText(
                "API 密钥",
                ChatRuntime.KEY_API_KEY,
                "本地端点可以不填",
                secret = true,
            ),
            SubField.Note(
                "这三格就是回合流发送真正读的：没填 base URL 发不出去，会指名道姓告诉你缺哪一格，" +
                    "不会拿演示回答糊弄你。base 自带版本段（/v1、/compatible-mode/v1）就不再补，" +
                    "没带会自动补 /v1。",
            ),
            SubField.Note(
                "密钥明文存在 App 私有目录（2026-09-15 拍板）：非 root 手机上别的 App 读不到；" +
                    "备份文件发给别人看 = 明文可见，这个边界当初是照着「自己用、忘了密码更麻烦」定的。",
            ),
        ),
    ),
    // 覆盖演示表「系统指令」页：这段话真的进每次请求（system 消息，排最前）。
    "prompt" to SubPage(
        "系统指令",
        listOf(
            SubField.PersistedText(
                "系统指令",
                ChatRuntime.KEY_SYSTEM_PROMPT,
                "比如：回答一律中文；留空就不发这条",
            ),
            SubField.Note(
                "这段话作为 system 消息放在每次请求的最前面，历史裁剪不砍它；" +
                    "空着就一个字不多发，不会塞一条空消息占位。",
            ),
        ),
    ),
    // 覆盖演示表「历史裁剪」页：上限真管发送，砍数真出声。
    "trim" to SubPage(
        "历史裁剪",
        listOf(
            SubField.PersistedText(
                "一次最多带几条历史",
                UiKeys.MAX_HISTORY,
                "数字，默认 40（范围 1-500）",
            ),
            SubField.Note(
                "超了就砍最旧的：砍了几条会当场出声（「上下文装不下：砍了 N 条旧话才发」）。" +
                    "系统指令不算条数；填了读不懂的数字就按 40 走，不会悄悄装作没设。",
            ),
        ),
    ),
    // 覆盖演示表「GitHub 工作台」页：仓库CI 页/检查更新/CI 提醒全读这几格。
    "github" to SubPage(
        "GitHub 工作台",
        listOf(
            SubField.PersistedText(
                "仓库",
                UiKeys.GITHUB_REPO,
                "owner/name，粘整条链接也认",
            ),
            SubField.PersistedText(
                "访问令牌",
                UiKeys.GITHUB_TOKEN,
                "公开仓库可留空；私有仓库要填",
                secret = true,
            ),
            SubField.PersistedSwitch(
                "CI 提醒（后台轮询，红绿出通知）",
                UiKeys.CI_NOTIFY,
                true,
            ),
            SubField.Note(
                "仓库CI 页拉 workflow runs、「检查更新」对最新发布版，读的都是上面两格。" +
                    "令牌只进请求头，不进任何报错、日志与界面文本。",
            ),
            SubField.Note(
                "CI 提醒：每 15 分钟在后台拍一次 GitHub，有新 run 出结果就发通知栏（红绿都报）。" +
                    "关掉就完全静默。Android 13+ 首开 App 会问一次通知权限，拒过的话去系统设置里开，" +
                    "这里不会反复弹。",
            ),
        ),
    ),
    // 新增真子页（0.7.0 文件投递）：长任务页投递读这三格；令牌留空借用「GitHub 工作台」那把。
    "courier" to SubPage(
        "文件投递",
        listOf(
            SubField.PersistedText(
                "目标仓库",
                UiKeys.COURIER_REPO,
                "owner/name，粘整条仓库链接也认",
            ),
            SubField.PersistedText(
                "分支",
                UiKeys.COURIER_BRANCH,
                "默认 main",
            ),
            SubField.PersistedText(
                "访问令牌",
                UiKeys.COURIER_TOKEN,
                "私有仓库必填；留空借用「GitHub 工作台」的令牌",
                secret = true,
            ),
            SubField.Note(
                "长任务页选好文件/目录后「开始投递」：按文件边界打成 zip 分卷（每卷约 32MB，" +
                    "单文件不劈开），逐卷传进上面的仓库，收尾写一份 manifest.json" +
                    "（每卷内容 + 每文件 SHA-256 + 总账），收方按账还原与校验。",
            ),
            SubField.Note(
                "一批最多 20 卷（约 640MB）；同一批重投会覆盖同名卷（重投 = 原地修复）。" +
                    "令牌只进请求头，不进任何报错、日志与界面文本。",
            ),
        ),
    ),
    // 覆盖演示表「数据控制」页：导出/导入/旧包兑换全是真动作（系统文件选择器 + 后台线程）。
    "datactl" to SubPage(
        "数据控制",
        listOf(
            SubField.Button(
                "导出备份",
                actionKey = "export",
            ),
            SubField.Button(
                "导入备份（覆盖同名设置，重名会话跳过）",
                actionKey = "import",
            ),
            SubField.Button(
                "导入旧 Agora 备份（.agora）",
                actionKey = "import_agora",
            ),
            SubField.Note(
                "本家备份包含：全部设置 + 会话库（一个会话一个文件），导出成一个 zip。" +
                    "导入时设置以备份为准（同名键覆盖、新键补齐），重名会话原样保留不动；" +
                    "两条账都会当场报给你。",
            ),
            SubField.Note(
                "旧 Agora 备份能兑的：提供商（端点名/base URL/激活的那把密钥）、默认模型、" +
                    "思考/搜索/终端/代码开关、系统指令字面部分、全部会话正文（id 加 agora- 前缀）。" +
                    "带不过来的会逐项报给你：图片视频（媒体不在备份里）、定时任务与循环" +
                    "（新版还没有这功能）、模板变量、工具气泡。",
            ),
        ),
    ),
)

/** 追加到主页各组的真设置项，key 是演示表里的组 id。图标只写键名。 */
val RealSectionAdditions: Map<String, List<SettingsItem>> = mapOf(
    "s-net" to listOf(
        SettingsItem(
            IconKey.SettingsRetry,
            "失败与重试",
            "网关把连接掐了怎么办；要不要自动重发",
            null,
            "retry",
        ),
    ),
    "s-data" to listOf(
        SettingsItem(
            IconKey.Clip,
            "文件投递",
            "把手机上的文件分卷投进私有仓，manifest 全账",
            null,
            "courier",
        ),
    ),
)

/**
 * 主页渲染用的合并结果：同 id 的组只出现一次，真设置项追加在该组演示项后面。
 * 顺序稳定（演示在前、真设置在后），免得每次重组条目乱跳。
 */
fun mergedSettingsSections(): List<SettingsSection> = SettingsSections.map { section ->
    val extra = RealSectionAdditions[section.id].orEmpty()
    if (extra.isEmpty()) section else section.copy(items = section.items + extra)
}

/**
 * 组 id 写错（演示表里没这个组）而挂不上去的条目。
 * 界面启动时必须问一次这个，非空就要出声：悄悄丢一项设置等于用户以为没有这项。
 */
fun orphanAdditions(): List<SettingsItem> {
    val known = SettingsSections.map { it.id }.toSet()
    return RealSectionAdditions.filterKeys { it !in known }.values.flatten()
}

/** 子页查表：真设置表优先，再落回演示表（照原型那份）。未知 key 返回 null，由界面处理。 */
fun subPage(key: String): SubPage? = RealSubPages[key] ?: SubPages[key]
