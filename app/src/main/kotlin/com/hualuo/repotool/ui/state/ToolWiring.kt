package com.hualuo.repotool.ui.state

import com.hualuo.engine.toolcalls.GitHubToolFamily
import com.hualuo.engine.toolcalls.GitHubWriteTool
import com.hualuo.engine.toolcalls.ToolRegistry
import com.hualuo.engine.toolcalls.WriteConfirmer

/**
 * 对话里模型可调工具的组装口（0.7.0 第二刀起，第三刀加写类）。
 *
 * **读类十件**（列仓、看别人的仓、浏览目录、读文件、搜代码、分支、提交历史、CI 三层）：
 * 直接进表，模型随对话可用。
 *
 * **写类一件**（github_update_file，改码/新建）：只有拿到 [confirmer] 才注册——
 * 不给闸门就不存在这个工具（默认拒写，不是默认放行）。给闸门时：模型提议先摆成
 * 确认卡，用户点头才真走 PUT（sha 对账与 409 冲突那条老路照旧）。
 *
 * 令牌与默认仓库都在**执行那一刻**从设置现场读（两个 lambda 进引擎件，不缓存、不复制）：
 * 设置页改了令牌，下一句就生效，不用重启。令牌只进请求头（引擎件老规矩），
 * 绝不进任何结果文本与报错。
 */
fun buildGithubToolRegistry(
    persist: UiPersistence,
    writeConfirmer: WriteConfirmer? = null,
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
}
