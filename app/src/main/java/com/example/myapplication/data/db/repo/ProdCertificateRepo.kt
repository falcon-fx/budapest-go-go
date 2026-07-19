package com.example.myapplication.data.db.repo

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.cert.CertificateFactory
import java.util.zip.ZipInputStream
import javax.inject.Inject

class ProdCertificateRepo @Inject constructor(
    @ApplicationContext private val context: Context
) : CertificateRepo {

    private val preferences = context.getSharedPreferences("gogo_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HAS_CERTS = "has_certs"
        private val EXPECTED_CERT_NAMES = listOf(
            "eszigno_root.pem",
            "eszigno_intermediate.pem",
            "go_bkk_hu.pem"
        )
    }

    private fun getCertsDir(): File {
        return File(context.filesDir, "certs")
    }

    override fun hasCertificates(): Boolean {
        // Check both flag AND physical file existence (defensive)
        if (!preferences.getBoolean(KEY_HAS_CERTS, false)) {
            return false
        }

        val certsDir = getCertsDir()
        return EXPECTED_CERT_NAMES.all { name ->
            val file = File(certsDir, name)
            file.exists() && file.length() > 0L
        }
    }

    override fun getCertificateFiles(): List<File> {
        val certsDir = getCertsDir()
        return EXPECTED_CERT_NAMES.map { name -> File(certsDir, name) }
    }

    @Throws(CertificateImportException::class)
    override fun importFromZipStream(inputStream: InputStream) {
        // 1. Extract all .pem files from zip
        val pemFiles = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".pem")) {
                        val fileName = File(entry.name).name // Strip path
                        pemFiles[fileName] = zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            throw CertificateImportException("Failed to read ZIP file: ${e.message}")
        }

        // 2. Validate exactly 3 .pem files
        if (pemFiles.size != 3) {
            throw CertificateImportException("ZIP must contain exactly 3 .pem files, found ${pemFiles.size}")
        }

        // 3. Validate each is a valid X.509 certificate
        val certFactory = CertificateFactory.getInstance("X.509")
        pemFiles.values.forEach { pemBytes ->
            try {
                certFactory.generateCertificate(pemBytes.inputStream())
            } catch (e: Exception) {
                throw CertificateImportException("Invalid certificate file: ${e.message}")
            }
        }

        // 4. Atomically write files (use .tmp → rename pattern)
        val certsDir = getCertsDir()
        certsDir.mkdirs()

        val pemFilesList = pemFiles.values.toList()

        try {
            EXPECTED_CERT_NAMES.forEachIndexed { idx, name ->
                val tmpFile = File(certsDir, "$name.tmp")
                val finalFile = File(certsDir, name)

                FileOutputStream(tmpFile).use { fos ->
                    fos.write(pemFilesList[idx])
                    fos.fd.sync()
                }

                if (!tmpFile.renameTo(finalFile)) {
                    throw IOException("Failed to rename $tmpFile to $finalFile")
                }
            }

            // 5. Set SharedPreferences flag only after all files written successfully
            preferences.edit {
                putBoolean(KEY_HAS_CERTS, true)
            }

        } catch (e: Exception) {
            // Clean up on failure
            certsDir.listFiles()?.forEach { it.delete() }
            preferences.edit {
                remove(KEY_HAS_CERTS)
            }
            throw CertificateImportException("Failed to store certificates: ${e.message}")
        }
    }

    override fun clearCertificates() {
        val certsDir = getCertsDir()
        certsDir.listFiles()?.forEach { it.delete() }
        preferences.edit {
            remove(KEY_HAS_CERTS)
        }
    }
}
