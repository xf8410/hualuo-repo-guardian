package com.hualuo.repotool.ui.state

import com.hualuo.engine.toolcalls.GitHubToolFamily
import com.hualuo.engine.toolcalls.ToolRegistry

/**
 * 对话里模型可调工具的组装口（0.7.0 第二刀）。
 *
 * 现在只有一族：GitHub 读类十件（列仓、看别人的仓、浏览目录、读文件、搜代码、
 * 分支、提交历史、CI 运行三层）。写类（改码提交）刻意不进这张表——写操作必须有
 * 独立的确认与对账通道，不许模型随对话自动执行改仓库；测试钉死「写类名字一个都不许出现」。
 *
 * 令牌与默认仓库都在**执行那一刻**从设置现场读（两个 lambda 进引擎件，不缓存、不复制）：
 * 设置页改了令牌，下一句就生效，不用重启。令牌只进请求头（引擎件老规矩），
 * 绝不进任何结果文本与报错。
 */
fun buildGithubToolRegistry(persist: UiPersistence): ToolRegistry = GitHubToolFamily.build(
    loadToken = { persist.load(UiKeys.GITHUB_TOKEN) },
    defaultRepo = { persist.load(UiKeys.GITHUB_REPO) },
)
