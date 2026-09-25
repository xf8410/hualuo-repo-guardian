package com.hualuo.repotool.ui.state

import com.hualuo.engine.sandbox.ProotSession
import com.hualuo.engine.sandbox.SandboxManager
import com.hualuo.engine.toolcalls.GitHubToolFamily
import com.hualuo.engine.toolcalls.GitHubWriteTool
import com.hualuo.engine.toolcalls.SandboxConfirmer
import com.hualuo.engine.toolcalls.SandboxToolFamily
import com.hualuo.engine.toolcalls.ToolRegistry
import com.hualuo.engine.toolcalls.WriteConfirmer
import java.io.File

/**
 * 对话里模型可调工具的组装口（0.9.0 起加沙盒族）。
 *
 * **读类十件**（列仓、看别人的仓、浏览目录、读文件、搜代码、分支、提交历史、CI 三层）：
 * 直接进表，模型随对话可用。
 *
 * **GitHub 写类一件**（github_update_file，改码/新建）：只有拿到 [confirmer] 才注册——
 * 不给闸门就不存在这个工具（默认拒写，不是默认放行）。给闸门时：模型提议先摆成
 * 确认卡，用户点头才真走 PUT（sha 对账与 409 冲突那条老路照旧）。
 *
 * **沙盒族（0.9.0 刀）**：status / list_packages 两件只读直进；
 * run_command / install / remove 三件执行类只有拿到 [sandboxConfirmer] 才注册
 * （对齐 560 功能目录 414「Shell 命令审批」——不给闸门，模型在对话里就碰不到执行）。
 * 沙盒引擎实例随构建创建：rootfs 与缓存都在应用私有目录，装过一次终身覆盖升级。
 *
 * 令牌与默认仓库都在**执行那一刻**从设置现场读（两个 lambda 进引擎件，不缓存、不复制）：
 * 设置页改了令牌，下一句就生效，不用重启。令牌只进请求头（引擎件老规矩），
 * 绝不进任何结果文本与报错。
 */
fun buildGithubToolRegistry(
    persist: UiPersistence,
    writeConfirmer: WriteConfirmer? = null,
    sandboxConfirmer: SandboxConfirmer? = null,
    sandboxRootDir: File? = null,
    prConfirmer: com.hualuo.engine.toolcalls.PrConfirmer? = null,
    memoryStore: com.hualuo.engine.memory.MemoryStore? = null,
    sessionStore: com.hualuo.engine.store.SessionStore? = null,
    webSearchEnabled: (() -> Boolean)? = null,
    skillStore: com.hualuo.engine.memory.MemoryStore? = null,
): ToolRegistry = GitHubToolFamily.build(
    loadToken = { persist.load(UiKeys.GITHUB_TOKEN) },
    defaultRepo = { persist.load(UiKeys.GITHUB_REPO) },
).also { registry ->
    if (writeConfirmer != null) {
        GitHubWriteTool.register(
            registry = registry,
            loadToken = { persist.load(UiKeys.GITHUB_TOKEN) },
            defaultRepo = { persist.load(UiKeys.GITHUB_REPO) },
            repoClient = com.hualuo.engine.github.GitHubRepoClient(),
            confirmer = writeConfirmer,
        )
    }
    if (prConfirmer != null) {
        com.hualuo.engine.toolcalls.GitHubPrTool.register(
            registry = registry,
            loadToken = { persist.load(UiKeys.GITHUB_TOKEN) },
            defaultRepo = { persist.load(UiKeys.GITHUB_REPO) },
            prClient = com.hualuo.engine.github.GitHubPrClient(),
            confirmer = prConfirmer,
        )
    }
    // 记忆族（M4 第二刀）：不注入 memoryStore 一件不注册（默认拒）
    com.hualuo.engine.toolcalls.MemoryTool.register(registry, memoryStore)
    // 对话检索族（M4 第三刀）：吃会话仓本体——会话库没建成（store=null）检索工具就不存在，
    // 「搜不到」比「工具在但永远空手」诚实
    com.hualuo.engine.toolcalls.RagTool.register(registry, sessionStore)
    // 网页族（M4 第五刀）：设置开关关掉=清单里消失（运行时可见性，翻回立刻回来）
    com.hualuo.engine.toolcalls.WebTool.register(
        registry,
        com.hualuo.engine.search.WebSearchClient(),
        fetcher = { url -> com.hualuo.engine.search.WebSearchClient.defaultFetcher(url) },
        visibleIf = webSearchEnabled,
    )
    // 技能族（M4 第六刀）：复用 MemoryStore（skill_db，无活动记忆文件）；不注入不注册
    com.hualuo.engine.toolcalls.SkillTool.register(registry, skillStore)
    if (sandboxConfirmer != null && sandboxRootDir != null) {
        val root = sandboxRootDir
        SandboxToolFamily.register(
            registry = registry,
            manager = SandboxManager(
                rootfsDir = File(root, "rootfs"),
                workDir = File(root, "work"),
                sharedDir = File(root, "shared"),
                sessionProvider = { rootfs, binds -> ProotSession(rootfsDir = rootfs, sharedDir = File(root, "shared"), bindMounts = binds) },
            ),
            confirmer = sandboxConfirmer,
        )
    }
}
