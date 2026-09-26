package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.engine.github.GitHubCiClient
import com.hualuo.engine.github.GitHubRun
import com.hualuo.engine.github.normalizeGitHubRepo
import com.hualuo.engine.search.SearchOutcome
import com.hualuo.engine.search.WebSearchClient
import com.hualuo.engine.search.WebSearchResult
import com.hualuo.engine.store.SessionStore
import com.hualuo.repotool.backup.BackupGateway
import com.hualuo.repotool.ui.data.DemoComposerThumbs
import com.hualuo.repotool.ui.data.DemoConversations
import com.hualuo.repotool.ui.data.RETRY_COSTLY_DEFAULT
import com.hualuo.repotool.ui.data.RETRY_COSTLY_KEY
import com.hualuo.repotool.ui.model.Conv
import com.hualuo.repotool.ui.model.NavTab
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.reflect.KProperty

/** 文件投递批里的一条：用户选进来的一个文件或一棵目录树。 */
data class CourierPick(
    /** 屏上显示用的短标签（选文件时是系统选择器给的路径尾段，收集真名在投递时做）。 */

    val label: String,
    /** content URI 的字符串形式。状态层不认识安卓的 Uri 类，转回去是根界面那层的事。 */
    val uri: String,
    /** true = 一棵目录树（OpenDocumentTree 的结果），投递时递归收集。 */
    val isTree: Boolean,
)

/**
 * 全局界面状态（v13.1 的 JS 变量一对一翻译）。
 *
 * 现在的分工：
 *  - 已接真电的字段（tab、input、currentModel、六个工具开关、lockToConversation）走 persist：
 *    初值从设置里读，改了就记一笔，落盘时机由界面攒着 flush。
 *  - 设置页里新加的开关走 flag 与 setFlag：键名由数据表带过来，不占字段位。
 *  - 设置页里的真文本走 text 与 setText：同样按键名，落盘按 settingsRevision 去抖。
 *  - 回合流真消息住 chat（ChatRuntime）：发送走真网络，busy 也从它读，不再单独一个演示布尔；
 *    历史上限与系统指令同刀接进（砍数出声、system 排最前）。
 *  - **会话库（M2 接线）**：store 不为 null 时，启动**同步**接上最近一次会话（没有异步首读，
 *    白屏和「多进几次才出来」没有土壤）、抽屉列表来自真库、新建/删除都动真文件；
 *    store 为 null（纯 JVM 测试、或会话库没建成）时一切照演示版走，行为不变。
 *  - **仓库CI（GitHub 只读）**：runs 与最新发布版现场拉，失败/坏条目出声不冒充；
 *    仓库与令牌在设置「GitHub 工作台」里配，令牌只进请求头。
 *  - **GitHub 登录（2026-09-22 补）**：整体住 [githubLogin]（[GithubLoginState]，进程级）；
 *    拿令牌验证 /user 并记登录态与权限；令牌一改登录当场作废（防撒谎态）。
 *  - **仓库工作台（浏览 + 改码）**：整体住 [repo]（[RepoWorkbenchState] 状态舱，
 *    999 行红线拆出来的：清单/浏览/分支/提交历史/文件预览与改码提交）。
 *  - **工具页真电（网页搜索）**：免费档 DuckDuckGo（引擎件 WebSearchClient，fetch 缝隙
 *    让引擎测试不碰真网，这里给的就是真网）；结果真数据、失败出声不冒充。
 *  - **长任务页真电（文件投递）**：选文件/选目录只发动作请求（[pendingDataAction] 桥上走），
 *    收集与分卷投递在根界面的后台线程（CourierDelivery）；目标仓/分支/令牌在设置「文件投递」。
 *  - **工具族（0.7.0 刀②）**：GitHub 读类十件注册进 [chat]（列仓、看别人的仓、浏览目录、
 *    读文件、搜代码、分支、提交历史、CI 三层）；令牌与默认仓库在执行那一刻从设置现场读
 *    （[buildGithubToolRegistry]），改了下一句生效。
 *  - **写类闸门（0.7.0 刀③）**：[writeGate] 非空才注册 github_update_file（不给闸门就不存在
 *    这个工具，默认拒写）；模型提议先摆确认卡，用户点头才真走 PUT。
 *  - **备份（数据控制）**：按钮只发出动作请求（[pendingDataAction]），系统文件选择器在
 *    RootScreen 那层开；导入的设置**必须**经 [applyImportedBackup] / [applyAgoraImport]
 *    走活通道进——绕过活通道直接写文件，会被下一次 flush 用旧值盖掉（两份事实的老病）。
 *  - 传 UiPersistence.None（默认）时行为与接线前逐字一致，纯 JVM 测试就这么跑。
 *
 * 键名进过真机就不许改（改了老设置读不到），清单在 UiKeys、SettingsCatalog 与 ChatRuntime。
 * 委托一律和声明写在同一行：属性声明在语法上本身就是完整的，把 by 挪到下一行有被当成分句结束的风险，不赌。
 */
class AppUiState(
    private val persist: UiPersistence = UiPersistence.None,
    private val store: SessionStore? = null,
    /** 写类工具的确认闸门；null = 不注册写工具（默认拒写的另一半）。 */
    private val writeGate: WriteConfirmGate? = null,
    /** 记忆库；null = 不注册记忆工具族（同闸门纪律）。 */
    private val memoryStore: com.hualuo.engine.memory.MemoryStore? = null,
    /** 技能库；null = 不注册技能工具族、系统提示词不拼技能目录。 */
    private val skillStore: com.hualuo.engine.memory.MemoryStore? = null,
    /** 图像生成配置读取（钥匙没配返回不可用配置）；null = 不注册图像生成。 */
    private val imageGenConfig: (() -> com.hualuo.engine.toolcalls.ImageGenConfig?)? = null,
    /** 图像字节落盘（字节、文件名前缀）到保存路径；null = 不注册图像生成。 */
    private val imageGenPersist: ((ByteArray, String) -> String)? = null,
    /** 看视频的库目录（录屏本体+manifest）；null = 不注册看视频工具族。 */
    val watchInboxDir: File? = null,
    /** 帧缓存目录（导入时抽好的 JPEG）。 */
    val watchFramesDir: File? = null,
) {

    // ── 已接持久化 ──────────────────────────────────────────────────────────

    /** SO 观测桥状态舱（560 清单 361-400 域；状态机在引擎件，这里只存收场）。
     *  放在 toolRegistry 构造调用之前：构造时 observeClientProvider 闭包要捕获它。 */
    val observe = ObserveUiState(persist)

    /** 查看器状态舱（全语言/进制/全格式上传；流式分块，红线三拆件）。 */
    val viewer = ViewerUiState(persist)

    /** 底栏停在第几页。存枚举名，读不懂就回回合流页。 */
    var tab: NavTab by saved(UiKeys.TAB, readTab(), { it.name })

    /** 输入框草稿（进程被杀、切去别的 App 再回来，不该丢字）。 */
    var input: String by saved(UiKeys.DRAFT, persist.load(UiKeys.DRAFT) ?: "", { it })

    /** 当前模型。 */
    var currentModel: String by saved(UiKeys.MODEL, persist.load(UiKeys.MODEL) ?: DEFAULT_MODEL, { it })

    var thinkOn: Boolean by saved(UiKeys.THINK_ON, readBool(UiKeys.THINK_ON, true), { it.toString() })

    var thinkLevel: Int by saved(UiKeys.THINK_LEVEL, readInt(UiKeys.THINK_LEVEL, 2), { it.toString() })

    var webSearchOn: Boolean by saved(UiKeys.WEB_SEARCH_ON, readBool(UiKeys.WEB_SEARCH_ON, true), { it.toString() })

    var shellOn: Boolean by saved(UiKeys.SHELL_ON, readBool(UiKeys.SHELL_ON, false), { it.toString() })

    var codeExecOn: Boolean by saved(UiKeys.CODE_EXEC_ON, readBool(UiKeys.CODE_EXEC_ON, false), { it.toString() })

    var relayOn: Boolean by saved(UiKeys.RELAY_ON, readBool(UiKeys.RELAY_ON, false), { it.toString() })

    var lockToConversation: Boolean by saved(
        UiKeys.LOCK_TO_CONVERSATION,
        readBool(UiKeys.LOCK_TO_CONVERSATION, false),
        { it.toString() },
    )

    /** 把攒着的改动落盘。返回 null 表示没问题；返回字符串是失败原因，界面必须 toast 出来。 */
    fun flushPersistence(): String? = persist.flush()

    /** 取走设置层累计的坏消息（非法值、半个表情等），取走即清空。 */
    fun persistenceMessages(): List<String> = persist.drainMessages()

    // ── 设置页的真开关（按键名，不占字段位） ────────────────────────────────

    /**
     * 本次界面里被改过的开关。只由 setFlag 填，读的时候不写：
     * getOrPut 会在组合期间写状态，那是重组抖动的常见来源，别给后面的人埋。
     */
    private val flagOverrides = mutableStateMapOf<String, Boolean>()

    /** 读一个真开关：本次改过的优先，其次设置文件，最后调用方给的默认值。 */
    fun flag(key: String, defaultOn: Boolean = false): Boolean =
        flagOverrides[key] ?: readBool(key, defaultOn)

    /** 改一个真开关：既更新界面状态（会重组），又记进设置等落盘。 */
    fun setFlag(key: String, value: Boolean) {
        flagOverrides[key] = value
        persist.save(key, value.toString())
    }

    // ── 设置页的真文本（按键名；提供商地址密钥这类） ────────────────────────

    /**
     * 本次改过、还没落盘的文本：输入框每敲一下只写内存，
     * 真落盘由界面按 [settingsRevision] 攒着去抖——「一个字写一次盘」是绝对不许的。
     */
    private val textOverrides = mutableStateMapOf<String, String>()

    /**
     * 每次 setText 给这个版本号加一：自动保存的看护方只看这一个数，
     * 不逐个盯每张输入框——以后文本键增减，看护点也不用跟着改。
     */
    var settingsRevision by mutableStateOf(0L)
        private set

    /** 读一个真文本设置：本次改过的优先，其次设置文件，最后调用方给的默认值。 */
    fun text(key: String, default: String = ""): String =
        textOverrides[key] ?: persist.load(key) ?: default

    /**
     * 改一个真文本：界面立刻更新、内存记一笔（不碰盘），修订号推进等去抖落盘。
     *
     * 特殊关照：改的是 github.token 时，把 [githubLogin] 里那次验证**当场作废**——
     * 防「屏上还写着已登录为旧身份、手里其实已经换了新钥匙」的撒谎态
     * （用户实报这条时点名的关切；作废是幂等的，没登录过就什么都不做）。
     */
    fun setText(key: String, value: String) {
        if (text(key) == value) return
        textOverrides[key] = value
        persist.save(key, value)
        settingsRevision += 1
        if (key == UiKeys.GITHUB_TOKEN) {
            githubLogin.invalidate()
        }
    }

    // ── GitHub 登录（2026-09-22 补） ────────────────────────────────────────

    /**
     * GitHub 登录状态舱：令牌验证（/user）、登录名与权限清单、退出登录。
     * 挂进程（本状态层由应用单例持有），转屏/切出不丢。通知出口与修订号与其它件同源。
     */
    val githubLogin = GithubLoginState(
        persist = persist,
        toast = { msg -> toast(msg) },
        bumpRevision = { settingsRevision += 1 },
    )

    // ── 回合流真运行层 ──────────────────────────────────────────────────────

    /**
     * 真说过的话与生成槽都住这里（契约见 ChatRuntime），会话仓与上下文喂养同刀接进；
     * 工具族（0.7.0 刀②③）同刀接进：GitHub 读类十件直进，写类一件只有 [writeGate]
     * 非空才注册（默认拒写）。三个真开关全部具名传：尾随 lambda 会绑到 ChatRuntime 的
     * 最后一个参数（clock），拿开关去尾随就是拿 Boolean 冒充 Long——CI 编译段抓到过，别再犯。
     */
    val chat = ChatRuntime(
        persist,
        autoRetryCostly = { flag(RETRY_COSTLY_KEY, RETRY_COSTLY_DEFAULT) },
        store = store,
        maxHistoryTurns = { readInt(UiKeys.MAX_HISTORY, ChatRuntime.MAX_HISTORY_TURNS).coerceIn(1, 500) },
        systemPrompt = {
            val base = persist.load(ChatRuntime.KEY_SYSTEM_PROMPT)?.trim().orEmpty()
            // 技能目录拼在系统提示词后（对齐旧仓 GenerationRequestBuilder 的 available_skills
            // 注入）：只报名字+描述，正文模型按需 read；空库不占一个字
            val catalog = skillStore?.let { com.hualuo.engine.toolcalls.SkillTool.catalog(it) }.orEmpty()
            when {
                catalog.isEmpty() -> base
                base.isEmpty() -> catalog
                else -> base + "\n\n" + catalog
            }
        },
        toolRegistry = buildGithubToolRegistry(
            persist,
            writeGate,
            memoryStore = memoryStore,
            sessionStore = store,
            webSearchEnabled = { webSearchOn },
            skillStore = skillStore,
            imageGenConfig = imageGenConfig,
            imageGenPersist = imageGenPersist,
            videoUrlSession = { ModelSettingsRuntime.current()?.sessionFor(currentModel) },
            watchInboxDir = watchInboxDir,
            watchFramesDir = watchFramesDir,
            visionSession = ::visionSessionOrDefault,
            observeClientProvider = {
                com.hualuo.engine.observe.ObserveClient(
                    observe.baseUrl,
                    com.hualuo.engine.api.UrlConnTransport(),
                )
            },
            observeLink = observe.link,
        ),
    )

    /**
     * 看视频的「眼睛」会话：设置里填的眼睛模型 id（provider:model）。
     * 没填/解析不出 = null（工具两件不注册）。与主对话用什么模型无关——
     * 纯文本主模型调 watch_video，眼睛读帧出文字给它。
     */
    private fun visionSessionOrDefault(): com.hualuo.engine.api.ProviderSession? {
        val id = persist.load(UiKeys.VISION_MODEL)?.trim().orEmpty()
        if (id.isEmpty()) return null
        return ModelSettingsRuntime.current()?.sessionFor(id)
    }

    /** 输入区发送钮的忙灯：真在跑才亮，不再是个能手动点着玩的演示布尔。 */
    val busy: Boolean get() = chat.busy

    // ── 仓库工作台（状态舱在 RepoWorkbenchState：浏览/分支/历史/改码） ──────

    /**
     * 仓库工作台的状态舱（999 行红线拆出来的）：只吃两样外界——
     * 设置里的 GitHub 令牌与这台 toast 喇叭，其余全部自持。
     */
    val repo = RepoWorkbenchState(
        loadToken = { persist.load(UiKeys.GITHUB_TOKEN)?.trim()?.takeIf { it.isNotEmpty() } },
        toast = { msg -> toast(msg) },
    )

    /**
     * 发送钮的统一入口（Composer 只管叫，规矩收在状态层一处，纯 JVM 可测）：
     *  - 空草稿不空发，出声说明；
     *  - 超过 [MAX_PROMPT_CHARS] 字符拒发——单条超大粘贴是把上下文窗口顶爆的最快方式。
     *    这道闸按字符管「单条」，历史护栏按条数管「总量」，各补各的盲区；
     *  - 过了闸才交 [chat.send]；**砍了历史必须当场出声**（家规：砍数上屏）；
     *    发出去草稿清空（清动作本身也记设置文件，防重开冒草稿）。
     */
    fun sendCurrentInput() {
        val text = input
        if (text.isBlank()) {
            toast("没内容可发")
            return
        }
        if (text.length > MAX_PROMPT_CHARS) {
            toast("这条 ${text.length} 字，超了单条上限 $MAX_PROMPT_CHARS：拆开发送或先精简，别拿大粘贴赌对方的窗口")
            return
        }
        chat.send(text, currentModel)
        if (chat.lastTrimmed > 0) {
            toast("上下文装不下：砍了 ${chat.lastTrimmed} 条旧话才发（上限在「历史裁剪」里调）")
        }
        input = ""
    }

    // ── 工具页真电：网页搜索（免费档 DuckDuckGo） ───────────────────────────

    /** 工具页搜索框里的词。演示壳转真电的第一格输入。 */
    var searchQuery by mutableStateOf("")

    /** 正在搜：按钮与提示行都看它。 */
    var searchBusy by mutableStateOf(false)
        private set

    /** 最近一次的搜索结果（真数据，引擎件清净过）。 */
    var searchResults by mutableStateOf(emptyList<WebSearchResult>())
        private set

    /** 最近一次搜索的收场话（成功报条数，失败给理由）；null = 还没搜过。 */
    var searchNote by mutableStateOf<String?>(null)
        private set

    /**
     * 工具页「搜一下」：真网络、真结果、失败出声不冒充（家规）。
     * 后台线程跑（大会计 IO 不进主线程，ANR 病根的老规矩）；收场一律写回
     * [searchResults] 与 [searchNote]，成功的旧结果不偷偷留着顶数——失败就明示失败。
     */
    fun runWebSearch() {
        if (searchBusy) return
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            toast("先在框里写要搜什么")
            return
        }
        searchBusy = true
        searchNote = null
        Thread({
            val outcome = WebSearchClient().search(query)
            searchBusy = false
            when (outcome) {
                is SearchOutcome.Ok -> {
                    searchResults = outcome.results
                    searchNote = "搜到 ${outcome.results.size} 条（${outcome.query}）"
                }
                is SearchOutcome.Failed -> {
                    searchResults = emptyList()
                    searchNote = outcome.reason
                }
            }
        }, "hualuo-web-search").start()
    }

    /** 视频库状态舱（编排细节在 VideoUnderstandingState，红线三拆件）。 */
    val video = VideoUnderstandingState(watchInboxDir = watchInboxDir)


    // ── APK 检查（560 清单 121-160 域；引擎全测，这里只存文本收场） ──

    var apkChecking by mutableStateOf(false)
    var apkReport by mutableStateOf<String?>(null)

    // ── 仓库CI（GitHub 只读） ───────────────────────────────────────────────

    private val ciClient = GitHubCiClient()

    var ciBusy by mutableStateOf(false)
        private set
    var ciRuns by mutableStateOf(emptyList<GitHubRun>())
        private set
    var ciBadEntries by mutableStateOf(0)
        private set
    var ciError by mutableStateOf<String?>(null)
        private set
    var ciRepoLabel by mutableStateOf(DEFAULT_GITHUB_REPO)
        private set
    /** 「检查更新」的一句话结论；null = 还没查过。 */
    var updateNote by mutableStateOf<String?>(null)
        private set

    private fun githubToken(): String? =
        persist.load(UiKeys.GITHUB_TOKEN)?.trim()?.takeIf { it.isNotEmpty() }

    /** 拉默认分支最近的 workflow runs。失败/坏条目都摆在明面上，不冒充成功。 */
    fun refreshRepoCi() {
        if (ciBusy) return
        val repo = persist.load(UiKeys.GITHUB_REPO)?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_GITHUB_REPO
        val token = githubToken()
        ciBusy = true
        ciError = null
        ciRepoLabel = repo
        Thread({
            val snapshot = runCatching { ciClient.latestRuns(repo, token) }.getOrElse {
                ciBusy = false
                ciError = "拉不动 GitHub（${it.message ?: "出错了"}）"
                return@Thread
            }
            ciRuns = snapshot.runs
            ciBadEntries = snapshot.badEntries
            ciError = snapshot.error
            ciBusy = false
        }, "hualuo-ci").start()
    }

    /** 进页时才拉；已有数据或正在拉就不重复。 */
    fun refreshRepoCiIfStale() {
        if (ciRuns.isEmpty() && !ciBusy) refreshRepoCi()
    }

    /** 拿当前版本对 GitHub 最新发布版：有新版/已最新/没发布过/查不到，四态各说各话。 */
    fun checkUpdate() {
        if (ciBusy) return
        val repo = persist.load(UiKeys.GITHUB_REPO)?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_GITHUB_REPO
        val token = githubToken()
        ciBusy = true
        Thread({
            val result = runCatching { ciClient.latestRelease(repo, token) }.getOrElse {
                ciBusy = false
                updateNote = "查不动（${it.message ?: "出错了"}）"
                return@Thread
            }
            ciBusy = false
            updateNote = when {
                result.error != null -> "查不到：${result.error}"
                result.notFound -> "GitHub 上还没有发布版，跳过对比"
                else -> {
                    val tag = result.release?.tag ?: "?"
                    if (tag == versionLabel.trim()) "已是最新（$tag）" else "有新版：$tag（当前 ${versionLabel}）"
                }
            }
        }, "hualuo-update").start()
    }

    // ── 长任务页真电：文件投递（courier；SAF 与投递执行在 RootScreen/CourierDelivery） ──

    /**
     * 已选进投递批的条目。只活在本进程、不落盘：SAF 授权跟着进程走，进程死了
     * 重新选一遍才靠得住，把 URI 装进设置文件是假安心。想清空点「清空已选」。
     */
    var courierPicks by mutableStateOf(emptyList<CourierPick>())
        private set

    /** 投递是否在跑：按钮看它禁点，桥上看它拒绝重复发车。 */
    var courierBusy by mutableStateOf(false)
        private set

    /** 最近一次投递的收场话（含收集报告与落点）；null = 本进程还没投过。 */
    var courierNote by mutableStateOf<String?>(null)
        private set

    /** 根界面把选择器的结果交进来（一次选中的可以是一个或多个）。 */
    fun addCourierPicks(picks: List<CourierPick>) {
        if (picks.isEmpty()) return
        courierPicks = courierPicks + picks
    }

    /** 清空已选批（不影响仓里已经投出去的卷）。 */
    fun clearCourierPicks() {
        courierPicks = emptyList()
    }

    /**
     * 长任务页「选文件」「选目录」：只发动作请求，系统选择器归 RootScreen 开
     * （备份同款桥：纯 JVM 状态层不认识 ActivityResult）。
     */
    fun requestCourierPick(tree: Boolean) {
        if (courierBusy) {
            toast("正在投递，先等这批跑完")
            return
        }
        pendingDataAction = if (tree) ACTION_COURIER_PICK_TREE else ACTION_COURIER_PICK_FILES
    }

    /** 目标仓：设置「文件投递」里配，粘整条仓库链接也认（与仓库CI 同一套 normalize）。没配对返回 null。 */
    fun courierRepo(): String? = normalizeGitHubRepo(text(UiKeys.COURIER_REPO).trim())

    /** 目标分支：没配按 main 走（投自家仓的默认分支是最常见的一格省略）。 */
    fun courierBranch(): String = text(UiKeys.COURIER_BRANCH).trim().ifBlank { "main" }

    /** 令牌：文件投递那格留空就借用「GitHub 工作台」的令牌（同一把钥匙不逼人填两遍）。 */
    fun courierToken(): String =
        text(UiKeys.COURIER_TOKEN).trim().ifBlank { text(UiKeys.GITHUB_TOKEN).trim() }

    /**
     * 长任务页「开始投递」：先过三道闸（批里有货、仓写法对、令牌在手），过了才发动作请求——
     * 真正的收集与分卷投递在 RootScreen 的后台线程（状态层不认识 ContentResolver）。
     * 缺哪道闸就指名道姓出声，绝不空发请求去撞网络。
     */
    fun requestCourierDeliver() {
        if (courierBusy) return
        if (courierPicks.isEmpty()) {
            toast("还没选要投的东西：先点「选文件」或「选目录」")
            return
        }
        if (courierRepo() == null) {
            toast("目标仓库没配对：去设置「文件投递」填 owner/name（粘整条仓库链接也认）")
            return
        }
        if (courierToken().isEmpty()) {
            toast("令牌不在手：私有仓库投递要令牌，去设置「文件投递」或「GitHub 工作台」填")
            return
        }
        pendingDataAction = ACTION_COURIER_DELIVER
    }

    /** 投递发车（接线层在后台线程里叫）：忙灯亮、旧收场话清掉——旧账不许挂着顶数。 */
    fun beginCourier() {
        courierBusy = true
        courierNote = null
    }

    /** 投递收场（接线层叫）：忙灯灭、结论入账。成功失败都走这里，不许静默。 */
    fun finishCourier(note: String) {
        courierBusy = false
        courierNote = note
    }

    // ── 备份（数据控制；动作桥接与文件选择器在 RootScreen） ─────────────────

    /**
     * 数据控制页与文件投递共用的动作桥（export/import/import_agora、courier_pick_files、
     * courier_pick_tree、courier_deliver）。设置子页的按钮只发请求，
     * 系统文件选择器（SAF）归 RootScreen 开——纯 JVM 状态层不认识安卓的 ActivityResult。
     */
    var pendingDataAction by mutableStateOf<String?>(null)
        private set

    fun requestDataAction(key: String) {
        pendingDataAction = key
    }

    fun clearPendingDataAction() {
        pendingDataAction = null
    }

    /**
     * 把导入的本家备份应用进**活通道**：设置逐键 save（活通道是唯一事实，绕过它直接写文件
     * 会被下一次 flush 用旧值盖掉）；会话已由网关落盘，这里只把账报出来。
     * 返回一句话给 toast；修订号推一格 + 立即落盘，界面与盘上同时吃到新值。
     *
     * 属性解析必须喂 StringReader：Properties.load(InputStream) 按 ISO-8859-1 解码，
     * 中文值全会变乱码——CI 测试段抓过（run 35099409305），别改回字节流。
     *
     * 导入完成后 [githubLogin] 从设置里重读登录态（备份里带就接上，没带就回到未登录）——
     * 绝不拿旧内存值冒充导入结果。
     */
    fun applyImportedBackup(backup: BackupGateway.ImportedBackup): String {
        if (!backup.formatOk) {
            return buildString {
                append("这不是本应用导出的备份包（格式或版本对不上）：没有导入任何东西")
                if (backup.warnings.isNotEmpty()) append("；").append(backup.warnings.joinToString("；"))
            }
        }
        var applied = 0
        val settingsText = backup.settingsText ?: ""
        if (settingsText.isNotEmpty()) {
            val props = java.util.Properties()
            val loadFailure = runCatching { props.load(java.io.StringReader(settingsText)) }
                .exceptionOrNull()
            if (loadFailure != null) {
                // 设置坏了不挡会话：会话账必须照样报全
                return "备份里的设置读不懂（${loadFailure.message ?: "格式不对"}）：" +
                    "设置没动、会话 ${backup.sessionsImported} 份已入库" +
                    backup.warnings.joinToString("；", prefix = "；")
            }
            for (name in props.stringPropertyNames()) {
                persist.save(name, props.getProperty(name))
                applied += 1
            }
        }
        githubLogin.reloadFromSettings()
        settingsRevision += 1
        val issues = persistenceMessages()
        val flushFailure = flushPersistence()
        return buildString {
            append("导入完成：设置 $applied 项、会话 ${backup.sessionsImported} 份")
            if (backup.sessionsSkipped > 0) append("、重名会话跳过 ${backup.sessionsSkipped} 份（原来的保留）")
            if (backup.warnings.isNotEmpty()) append("；").append(backup.warnings.joinToString("；"))
            if (issues.isNotEmpty()) append("；设置提示：").append(issues.joinToString("；"))
            if (flushFailure != null) append("；设置没存上：").append(flushFailure)
        }
    }

    /**
     * 旧 Agora 包（.agora）的应用：会话已由网关落盘（agora- 前缀、重名跳过），
     * 这里把兑换单里的设置逐键送进**活通道**，带不动的账（媒体/任务/模板变量）原样报出来。
     * 返回一句话给 toast；修订号推一格 + 立即落盘。
     */
    fun applyAgoraImport(outcome: BackupGateway.AgoraImportOutcome): String {
        if (!outcome.recognized || outcome.plan == null) {
            return outcome.error ?: "旧备份没认出来：没有导入任何东西"
        }
        val plan = outcome.plan
        var applied = 0
        fun put(key: String, value: String?) {
            if (value != null) {
                persist.save(key, value)
                applied += 1
            }
        }
        put(ChatRuntime.KEY_NAME, plan.providerName)
        put(ChatRuntime.KEY_BASE_URL, plan.baseUrl)
        put(ChatRuntime.KEY_API_KEY, plan.apiKey)
        put(UiKeys.MODEL, plan.selectedModel)
        put(ChatRuntime.KEY_SYSTEM_PROMPT, plan.systemPrompt)
        plan.thinkingOn?.let { put(UiKeys.THINK_ON, it.toString()) }
        plan.thinkingLevel?.let { put(UiKeys.THINK_LEVEL, it.toString()) }
        plan.codeExecOn?.let { put(UiKeys.CODE_EXEC_ON, it.toString()) }
        plan.webSearchOn?.let { put(UiKeys.WEB_SEARCH_ON, it.toString()) }
        plan.shellOn?.let { put(UiKeys.SHELL_ON, it.toString()) }
        settingsRevision += 1
        val issues = persistenceMessages()
        val flushFailure = flushPersistence()
        return buildString {
            append("旧 Agora 备份导入完成：设置 $applied 项、会话 ${outcome.sessionsImported} 份")
            if (outcome.sessionsSkipped > 0) append("、重名会话跳过 ${outcome.sessionsSkipped} 份（原来的保留）")
            if (plan.notes.isNotEmpty()) append("；").append(plan.notes.joinToString("；"))
            if (issues.isNotEmpty()) append("；设置提示：").append(issues.joinToString("；"))
            if (flushFailure != null) append("；设置没存上：").append(flushFailure)
        }
    }

    // ── 仍是演示态的字段 ────────────────────────────────────────────────────

    /** 版本串由入口注入（BuildConfig 读自 version.properties 单源），界面里不许写死。 */
    var versionLabel by mutableStateOf("")

    // 抽屉（store 接上后这里是真库列表；没接库才落回演示数据）
    var drawerOpen by mutableStateOf(false)
    var convQuery by mutableStateOf("")
    var selecting by mutableStateOf(false)
    var selectedIds by mutableStateOf(setOf<String>())
    var convs by mutableStateOf(DemoConversations)
    var confirmOpen by mutableStateOf(false)
    var confirmText by mutableStateOf("")
    var confirmAction: (() -> Unit)? = null

    // 输入区的瞬时态（不该持久化）
    var micOn by mutableStateOf(false)
    var addMenuOpen by mutableStateOf(false)
    var loopBarOn by mutableStateOf(true)
    var queueBarOn by mutableStateOf(true)
    var thumbs by mutableStateOf(DemoComposerThumbs)

    // 原位弹层（模型、工具、任务详情互斥，同原型 closeAll）
    var modelSheetOpen by mutableStateOf(false)
    var toolSheetOpen by mutableStateOf(false)
    var taskSheetKey by mutableStateOf<String?>(null)
    var modelQuery by mutableStateOf("")

    // 设置
    var settingsOpen by mutableStateOf(false)
    var settingsQuery by mutableStateOf("")

    /** 子页栈：空表示停在设置主页；栈顶是当前二级页 key（对应原型 SUBSTACK）。 */
    var subStack by mutableStateOf(listOf<String>())

    // toast
    var toastText by mutableStateOf("")
    var toastVisible by mutableStateOf(false)
    private var toastSeq = 0
    var toastToken by mutableStateOf(0)

    fun toast(msg: String) {
        toastText = msg
        toastVisible = true
        toastSeq += 1
        toastToken = toastSeq
    }

    fun closeSheets() {
        modelSheetOpen = false
        toolSheetOpen = false
        taskSheetKey = null
        addMenuOpen = false
        drawerOpen = false
    }

    fun toggleSelect(id: String) {
        val cur = selectedIds
        selectedIds = if (id in cur) cur - id else cur + id
    }

    fun addThumb(icon: String) {
        thumbs = thumbs + icon
    }

    fun removeThumb(index: Int) {
        thumbs = thumbs.filterIndexed { i, _ -> i != index }
    }

    fun newConversation() {
        val s = store
        if (s == null) {
            // 没接库（纯 JVM 测试/会话库没建成）：老演示路径，一字不动
            val c = Conv("c" + (convs.size + 1) + "-" + System.currentTimeMillis(), "新会话 · 刚刚", "刚刚")
            convs = listOf(c) + convs
            selecting = false
            selectedIds = emptySet()
            closeSheets()
            toast("已新建会话（演示数据）")
            return
        }
        val id = runCatching { s.create(currentModel) }.getOrNull()
        if (id == null) {
            toast("新会话没建成（盘上出事了）：还接着当前会话聊，字不会丢")
            closeSheets()
            return
        }
        chat.startFreshSession(id)
        // 标题允许空——还没说话就是没标题，屏上标「（未命名）」是明示不是编造
        convs = listOf(Conv(id, "（未命名）", fmtConvMeta(System.currentTimeMillis()))) + convs
        selecting = false
        selectedIds = emptySet()
        closeSheets()
        toast("已新建会话")
    }

    /** 抽屉点某条会话：同步读那份 JSONL 摆上屏；读不出就出声，绝不摆空壳。 */
    fun openConversation(id: String) {
        if (store == null) return
        if (id == chat.sessionId) {
            closeSheets()
            return
        }
        val note = chat.restoreFromStore(id)
        closeSheets()
        when {
            note == null -> toast("这个会话读不到了：文件可能已被删")
            note.badLines > 0 -> toast("已切到该会话（${note.count} 条）；另有 ${note.badLines} 行读不出，已跳过")
            note.headMissing -> toast("已切到该会话（${note.count} 条）；这份会话头损坏，标题时间失真")
            else -> toast("已切到该会话（${note.count} 条）")
        }
    }

    fun askDeleteSelected() {
        val n = selectedIds.size
        if (n == 0) return
        confirmText = "删除 $n 个会话？"
        confirmAction = {
            val ids = selectedIds
            // 真库：删会话就是删文件（SessionStore 的家规，不留墓碑）
            store?.let { s -> ids.forEach { id -> runCatching { s.delete(id) } } }
            if (ids.contains(chat.sessionId)) {
                // 删的是当前正开的会话：屏上消息清掉、户头摘掉，下条消息自动开新户
                chat.startFreshSession(null)
            }
            convs = convs.filter { it.id !in ids }
            selectedIds = emptySet()
            selecting = false
            confirmOpen = false
            drawerOpen = false
        }
        confirmOpen = true
    }

    fun openSettings(anchorSub: String? = null) {
        closeSheets()
        settingsQuery = ""
        subStack = if (anchorSub != null) listOf(anchorSub) else emptyList()
        settingsOpen = true
    }

    fun backFromSettings() {
        val s = subStack
        if (s.isNotEmpty()) {
            subStack = s.dropLast(1)
        } else {
            settingsOpen = false
        }
    }

    // ── 持久化小件 ──────────────────────────────────────────────────────────

    /**
     * 「改了就记一笔」的状态位：值没变不记（免得白写盘），变了才既更新界面状态、又交给 persist。
     * 界面读它照样是快照状态，重组行为与 by mutableStateOf 一致。
     */
    private inner class Saved<T>(
        private val key: String,
        initial: T,
        private val encode: (T) -> String,
    ) {
        private val state = mutableStateOf(initial)

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (state.value == value) return
            state.value = value
            persist.save(key, encode(value))
        }
    }

    private fun <T> saved(key: String, initial: T, encode: (T) -> String): Saved<T> =
        Saved(key, initial, encode)

    private fun readTab(): NavTab {
        val stored = persist.load(UiKeys.TAB) ?: return NavTab.Chat
        return NavTab.entries.firstOrNull { it.name == stored } ?: NavTab.Chat
    }

    private fun readBool(key: String, default: Boolean): Boolean =
        when (persist.load(key)?.trim()?.lowercase()) {
            null -> default
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> default
        }

    private fun readInt(key: String, default: Int): Int =
        persist.load(key)?.trim()?.toIntOrNull() ?: default

    private fun fmtConvMeta(ms: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(ms))

    /**
     * 会话库接线（启动，全同步）。刻意写成嵌套 if 而不是 init 里 return：
     * init 块里的 return 语义各版本 Kotlin 有分歧，不值得赌；嵌套清楚照样读得懂。
     * 放在类尾：convs 等属性的委托都已初始化，「先用后声明」的初始化顺序坑不存在。
     * 接成功了不出声（信任靠「字还在」建立，不靠开场白）；失败必须出声。
     */
    init {
        val s = store
        if (s != null) {
            val listing = runCatching { s.list() }.getOrNull()
            if (listing == null) {
                toast("会话目录读不了：列表这次空着，历史文件一个没动")
            } else {
                convs = listing.heads.map { (id, head) ->
                    Conv(id, head.title.ifBlank { "（未命名）" }, fmtConvMeta(head.createdAtMs))
                }
                if (listing.unreadable > 0) {
                    toast("有 ${listing.unreadable} 个会话文件读不出头，没摆进列表（文件原样保留）")
                }
                val latest = listing.heads.firstOrNull()
                if (latest != null) {
                    val note = chat.restoreFromStore(latest.first)
                    when {
                        note == null -> toast("上次的会话文件读不到了：列表还在，正文没接上")
                        note.badLines > 0 -> toast("已接上次会话（${note.count} 条）；另有 ${note.badLines} 行读不出，已跳过")
                        note.headMissing -> toast("已接上次会话（${note.count} 条）；这份会话头损坏，标题时间失真")
                        else -> Unit
                    }
                }
            }
        }
    }

    companion object {
        /** 没设置过时的默认模型（真接线后由模型清单决定，这里只是不空着）。 */
        const val DEFAULT_MODEL = "qwen3.8-flash"

        /** 仓库CI 默认看的仓库（设置「GitHub 工作台」里可改）。 */
        const val DEFAULT_GITHUB_REPO = "xf8410/hualuo-repo-tool"

        /**
         * 单条消息字符上限。选 5 万的理由：几万字的整篇粘贴对几乎所有对话模型都还在
         * 窗口内，但一 MB 级的整文件直塞必炸——闸门拦的是「事故」不是「长文」。
         */
        const val MAX_PROMPT_CHARS = 50_000

        /** 文件投递的动作键（pendingDataAction 桥上走）：RootScreen 认这三个。 */
        const val ACTION_COURIER_PICK_FILES = "courier_pick_files"
        const val ACTION_COURIER_PICK_TREE = "courier_pick_tree"
        const val ACTION_COURIER_DELIVER = "courier_deliver"

        /**
         * 投递目标前缀：courier/时间戳/，斜杠结尾（引擎件的规矩，前缀由调用方给）。
         * 一批一个目录：重投同批是原地覆盖（引擎件的「重投 = 原地修复」），
         * 换一批自动换新目录，不同批互不打架。
         */
        fun buildCourierPrefix(nowMs: Long): String =
            "courier/" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMs)) + "/"
    }
}
