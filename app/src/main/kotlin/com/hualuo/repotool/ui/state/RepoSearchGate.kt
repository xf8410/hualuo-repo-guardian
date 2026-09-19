package com.hualuo.repotool.ui.state

/**
 * 仓库内搜索的四道闸（纯函数，纯 JVM 好测）：全部在发网之前判，失败必给一句话。
 *
 * 返回 null = 四道全过；否则返回指名道姓的一句话（缺了什么、去哪儿补）：
 *  1. 没在浏览仓库——搜索没得搜；
 *  2. 在非默认分支——GitHub 代码索引只覆盖默认分支，切回去再搜；
 *  3. 没写搜索词——先写点什么；
 *  4. 没令牌——搜索接口的死规矩，指路去设置。
 *
 * 判词与状态舱 runSearch 出声的话**同一来源**（挪进这里就为了一处改、两处对得上）。
 */
internal fun repoSearchGate(browsing: String, ref: String?, query: String, token: String?): String? = when {
    browsing.isEmpty() -> "没在浏览仓库，搜索没得搜"
    ref != null -> "搜索只走默认分支的索引：现在在「$ref」，切回默认分支再搜"
    query.trim().isEmpty() -> "先写搜索词：类名、函数名、报错原文都行"
    token.isNullOrEmpty() -> "代码搜索必须带令牌（GitHub 搜索接口的死规矩）：去设置「GitHub 工作台」填"
    else -> null
}
