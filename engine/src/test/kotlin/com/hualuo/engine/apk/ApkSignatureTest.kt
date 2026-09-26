package com.hualuo.engine.apk

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 签名摘要测试：真 APK 在本地（sandbox1 包）跑真验证；CI 无该文件时 assume 跳过，
 * 结构遍历的错账（找不到 EOCD/无签名块）另有分支文案兜底。
 */
class ApkSignatureTest {

    private val realApk = File("/home/z/my-project/Hualuo-release-签名版-0.9.0-sandbox1.apk")

    @Test
    fun `真 APK 抽出 v2 证书摘要`() {
        assumeTrue(realApk.exists())
        val report = ApkSignature.inspect(realApk)
        assertTrue("应识别 v2 签名：${report.error}", report.v2Present || report.v3Present)
        val scheme = report.schemes.first()
        assertTrue(scheme.certSubject.isNotBlank())
        assertTrue("SHA-256 指纹 64 位 hex：${scheme.certSha256Hex}", scheme.certSha256Hex.length == 64)
        assertTrue(scheme.certCount >= 1)
    }

    @Test
    fun `非 APK 文件如实报错不猜`() {
        val f = File.createTempFile("notapk", ".bin")
        f.writeBytes(ByteArray(64))
        val report = ApkSignature.inspect(f)
        assertTrue(report.error != null || (!report.v2Present && !report.v3Present))
        f.delete()
    }
}
