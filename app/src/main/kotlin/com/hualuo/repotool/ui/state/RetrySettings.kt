package com.hualuo.repotool.ui.state

import com.hualuo.engine.http.RetryPolicy
import com.hualuo.repotool.ui.data.RETRY_COSTLY_DEFAULT
import com.hualuo.repotool.ui.data.RETRY_COSTLY_KEY

/**
 * 设置页那个开关到重试策略的唯一通道。
 *
 * 为什么要有这个函数：否则开关就只是「存了一格布尔没人读」的摆设。界面、策略、测试三边都从
 * 这里过，用户拨一下，524 与 502 这类网关失败的收场就真的不一样（行为由 RetrySettingsTest 钉住）。
 * HTTP 接线层只许用 [retryPolicyFor] 造策略，不许自己 new RetryPolicy 绕过开关。
 */
fun retryPolicyFor(autoRetryCostly: Boolean): RetryPolicy =
    RetryPolicy(retryOnCostlyRequests = autoRetryCostly)

/** 界面上要不要给「已自动重发」这类提示，也走同一个键名，不许两处各写一遍默认值。 */
fun AppUiState.autoRetryCostlyEnabled(): Boolean =
    flag(RETRY_COSTLY_KEY, RETRY_COSTLY_DEFAULT)

/** 从界面状态读开关并造策略：接线层就用这一个入口，省得两边各读各的。 */
fun AppUiState.retryPolicy(): RetryPolicy =
    retryPolicyFor(autoRetryCostlyEnabled())
