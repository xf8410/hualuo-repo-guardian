package com.hualuo.engine.lsp

import com.hualuo.engine.io.sanitizeForLog

/**
 * 一个语言包的定义：按需下载与安装所需的全部事实（LSP 刀·切片②骨架）。
 *
 * 为什么 URL 与 SHA-256 必须同框：「先下下来、再随便找个渠道核对哈希」是把核对留给运气——
 * 损坏或恶意的包在下载那一刻已经落在磁盘上了。把期望哈希钉在定义里，
 * 安装器在下载之后、动任何磁盘之前先验（PackInstaller 的 verify 段）。
 *
 * 上游清单（这些定义由谁生产、怎么更新）是后面一刀的事；本切片只认「调用方给的定义」。
 *
 * 字段纪律（构造时硬验，违规抛 IllegalArgumentException，不带病进流程）：
 *  - id / version：ASCII 小写词符（a-z 0-9 点 下划线 减号），长 1 到 64——它们直接进路径；
 *  - serverBinary：ASCII 词符文件名（允许点号与减号），不许含双点——登记与查找都认它；
 *  - sha256Hex：64 位小写十六进制，大写视为不匹配（宁严勿宽）；
 *  - downloadUrl：只认 http 与 https，且不许含控制字符；
 *  - 任何字段都不许含控制字符（换行能伪造日志行）；报错消息里的原样值一律先脱敏再拼
 *    （控制字符换成点、超长截断），不把字符本体原样印出去。
 */
data class LanguagePack(
    val id: String,
    val displayName: String,
    val version: String,
    val serverBinary: String,
    val sha256Hex: String,
    val downloadUrl: String,
) {
    init {
        require(isSlug(id)) { "语言包 id 必须是 ASCII 小写词符（长 1 到 64），现在是：" + sanitizeForLog(id) }
        require(isSlug(version)) { "版本号必须是 ASCII 小写词符，现在是：" + sanitizeForLog(version) }
        require(isFileName(serverBinary)) {
            "服务入口文件名必须是 ASCII 词符（允许点号与减号、不许含双点），现在是：" + sanitizeForLog(serverBinary)
        }
        require(isSha256(sha256Hex)) { "sha256 必须是 64 位小写十六进制（实际长度 ${sha256Hex.length}）" }
        require(isHttpOrHttps(downloadUrl)) { "下载地址只认 http 与 https（现在是：" + sanitizeForLog(downloadUrl) + "）" }
        require(downloadUrl.none { it.hasControl() }) {
            "下载地址不许含控制字符（换行能伪造日志行），现在是：" + sanitizeForLog(downloadUrl)
        }
        require(displayName.isNotEmpty() && displayName.none { it.hasControl() }) { "显示名不能为空、也不许含控制字符" }
    }
}

/** 控制字符判定：U+0000 到 U+001F 与 U+007F。任何字段里出现都硬拒。 */
internal fun Char.hasControl(): Boolean = code < 0x20 || code == 0x7F

/** ASCII 小写词符：首字符是字母或数字，其后允许点、下划线、减号，总长 1 到 64。 */
internal fun isSlug(value: String): Boolean = SLUG.matches(value)

/** 文件名形状：首字符字母或数字，其后允许点、下划线、减号，总长 1 到 128，且不含双点。 */
internal fun isFileName(value: String): Boolean =
    FILE_NAME.matches(value) && !value.contains("..")

/** 64 位小写十六进制。大写、短一位、多一位都算不匹配。 */
internal fun isSha256(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

internal fun isHttpOrHttps(value: String): Boolean =
    value.startsWith("https://") || value.startsWith("http://")

private val SLUG = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
private val FILE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
