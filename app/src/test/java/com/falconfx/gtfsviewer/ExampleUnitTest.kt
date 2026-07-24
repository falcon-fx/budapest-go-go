package com.falconfx.gtfsviewer

import com.falconfx.gtfsviewer.data.db.repo.CertificateImportException
import com.falconfx.gtfsviewer.data.db.repo.ProdCertificateRepo
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testIsZip_withZipSignature() {
        val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)
        assertTrue(ProdCertificateRepo.isZip(zipBytes))
    }

    @Test
    fun testIsZip_withNonZipSignature() {
        val pemBytes = "-----BEGIN CERTIFICATE-----".toByteArray()
        assertFalse(ProdCertificateRepo.isZip(pemBytes))
    }

    @Test
    fun testParsePemChain_withSingleCert() {
        val mockCert = """
            -----BEGIN CERTIFICATE-----
            MOCK_CERTIFICATE_BODY_1
            -----END CERTIFICATE-----
        """.trimIndent()

        val result = ProdCertificateRepo.parsePemChain(mockCert.toByteArray())
        assertEquals(1, result.size)
        assertTrue(result.containsKey("go_bkk_hu.pem"))
        assertEquals(mockCert, String(result["go_bkk_hu.pem"]!!))
    }

    @Test
    fun testParsePemChain_withThreeCerts() {
        val mockChain = """
            -----BEGIN CERTIFICATE-----
            MOCK_CERTIFICATE_BODY_1
            -----END CERTIFICATE-----

            -----BEGIN CERTIFICATE-----
            MOCK_CERTIFICATE_BODY_2
            -----END CERTIFICATE-----

            -----BEGIN CERTIFICATE-----
            MOCK_CERTIFICATE_BODY_3
            -----END CERTIFICATE-----
        """.trimIndent()

        val result = ProdCertificateRepo.parsePemChain(mockChain.toByteArray())
        assertEquals(3, result.size)
        assertTrue(result.containsKey("go_bkk_hu.pem"))
        assertTrue(result.containsKey("eszigno_intermediate.pem"))
        assertTrue(result.containsKey("eszigno_root.pem"))
    }

    @Test(expected = CertificateImportException::class)
    fun testParsePemChain_withFourCerts_throwsException() {
        val mockChain = """
            -----BEGIN CERTIFICATE-----
            1
            -----END CERTIFICATE-----
            -----BEGIN CERTIFICATE-----
            2
            -----END CERTIFICATE-----
            -----BEGIN CERTIFICATE-----
            3
            -----END CERTIFICATE-----
            -----BEGIN CERTIFICATE-----
            4
            -----END CERTIFICATE-----
        """.trimIndent()

        ProdCertificateRepo.parsePemChain(mockChain.toByteArray())
    }

    @Test(expected = CertificateImportException::class)
    fun testParsePemChain_withNoCerts_throwsException() {
        val invalidContent = "This is not a certificate file at all."
        ProdCertificateRepo.parsePemChain(invalidContent.toByteArray())
    }
}