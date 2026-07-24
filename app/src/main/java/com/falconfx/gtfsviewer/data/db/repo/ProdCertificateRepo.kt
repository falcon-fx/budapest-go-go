package com.falconfx.gtfsviewer.data.db.repo

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.cert.CertificateFactory
import java.util.zip.ZipInputStream
import javax.inject.Inject

class ProdCertificateRepo @Inject constructor(
    @ApplicationContext private val context: Context
) : CertificateRepo {

    private val preferences = context.getSharedPreferences("gogo_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ProdCertificateRepo"
        private const val KEY_HAS_CERTS = "has_certs"

        // Certificate names in priority order (required first, optional after)
        private const val REQUIRED_CERT = "go_bkk_hu.pem"
        private val OPTIONAL_CERTS = listOf(
            "eszigno_intermediate.pem",
            "eszigno_root.pem"
        )

        private val ALL_POSSIBLE_CERTS = listOf(REQUIRED_CERT) + OPTIONAL_CERTS

        fun isZip(bytes: ByteArray): Boolean {
            return bytes.size >= 4 &&
                   bytes[0] == 0x50.toByte() &&
                   bytes[1] == 0x4B.toByte() &&
                   bytes[2] == 0x03.toByte() &&
                   bytes[3] == 0x04.toByte()
        }

        @Throws(CertificateImportException::class)
        fun parseZip(zipBytes: ByteArray): Map<String, ByteArray> {
            val pemFiles = mutableMapOf<String, ByteArray>()
            try {
                ZipInputStream(zipBytes.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".pem")) {
                            val fileName = File(entry.name).name
                            val bytes = zis.readBytes()
                            pemFiles[fileName] = bytes
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } catch (e: Exception) {
                throw CertificateImportException("Failed to read ZIP file: ${e.message}")
            }
            return pemFiles
        }

        @Throws(CertificateImportException::class)
        fun parsePemChain(pemBytes: ByteArray): Map<String, ByteArray> {
            val pemFiles = mutableMapOf<String, ByteArray>()
            try {
                val text = String(pemBytes, Charsets.UTF_8)
                val regex = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----")
                val matches = regex.findAll(text).map { it.value.trim() }.toList()

                if (matches.isEmpty()) {
                    throw CertificateImportException("The file is not a valid ZIP and does not contain any valid PEM certificates")
                }

                if (matches.size > 3) {
                    throw CertificateImportException("The PEM file contains too many certificates (${matches.size}). A maximum of 3 is allowed.")
                }

                matches.forEachIndexed { index, pemString ->
                    val fileName = when (index) {
                        0 -> REQUIRED_CERT
                        1 -> OPTIONAL_CERTS[0]
                        2 -> OPTIONAL_CERTS[1]
                        else -> throw CertificateImportException("Invalid index mapping")
                    }
                    pemFiles[fileName] = pemString.toByteArray(Charsets.UTF_8)
                }
            } catch (e: CertificateImportException) {
                throw e
            } catch (e: Exception) {
                throw CertificateImportException("Failed to parse PEM file: ${e.message}")
            }
            return pemFiles
        }
    }

    private fun getCertsDir(): File {
        return File(context.filesDir, "certs")
    }

    override fun hasCertificates(): Boolean {
        // Check both flag AND that required certificate exists
        if (!preferences.getBoolean(KEY_HAS_CERTS, false)) {
            return false
        }

        val certsDir = getCertsDir()
        val requiredCert = File(certsDir, REQUIRED_CERT)
        return requiredCert.exists() && requiredCert.length() > 0L
    }

    override fun getCertificateFiles(): List<File> {
        val certsDir = getCertsDir()
        return ALL_POSSIBLE_CERTS
            .map { name -> File(certsDir, name) }
            .filter { file -> file.exists() && file.length() > 0L }
    }

    @Throws(CertificateImportException::class)
    override fun importCertificates(fileBytes: ByteArray) {
        Log.i(TAG, "importCertificates: Starting import (${fileBytes.size} bytes)")

        val pemFiles = if (isZip(fileBytes)) {
            Log.i(TAG, "importCertificates: ZIP file detected")
            parseZip(fileBytes)
        } else {
            Log.i(TAG, "importCertificates: Assuming PEM chain file format")
            parsePemChain(fileBytes)
        }

        if (pemFiles.isEmpty()) {
            throw CertificateImportException("No certificate files found")
        }
        if (pemFiles.size > 3) {
            throw CertificateImportException("Too many certificates: found ${pemFiles.size}, maximum of 3 allowed")
        }

        if (!pemFiles.containsKey(REQUIRED_CERT)) {
            throw CertificateImportException("Required certificate $REQUIRED_CERT must be present")
        }

        val unknownCerts = pemFiles.keys - ALL_POSSIBLE_CERTS.toSet()
        if (unknownCerts.isNotEmpty()) {
            throw CertificateImportException("Unknown certificate files: ${unknownCerts.joinToString()}")
        }

        val certFactory = CertificateFactory.getInstance("X.509")
        pemFiles.forEach { (name, pemBytes) ->
            try {
                val content = String(pemBytes, Charsets.UTF_8)
                if (!content.contains("-----BEGIN CERTIFICATE-----")) {
                    throw CertificateImportException("Certificate file $name must be in PEM format (starting with -----BEGIN CERTIFICATE-----)")
                }
                certFactory.generateCertificate(pemBytes.inputStream())
                Log.i(TAG, "importCertificates: Certificate $name is valid")
            } catch (e: CertificateImportException) {
                throw e
            } catch (e: Exception) {
                throw CertificateImportException("Invalid certificate file $name: ${e.message}")
            }
        }

        val certsDir = getCertsDir()
        certsDir.mkdirs()

        try {
            pemFiles.forEach { (name, pemBytes) ->
                val tmpFile = File(certsDir, "$name.tmp")
                val finalFile = File(certsDir, name)

                FileOutputStream(tmpFile).use { fos ->
                    fos.write(pemBytes)
                    fos.fd.sync()
                }

                if (!tmpFile.renameTo(finalFile)) {
                    throw IOException("Failed to rename $tmpFile to $finalFile")
                }
                Log.i(TAG, "importCertificates: Successfully saved $name")
            }

            preferences.edit {
                putBoolean(KEY_HAS_CERTS, true)
            }

            Log.i(TAG, "importCertificates: Certificate import completed successfully")

        } catch (e: Exception) {
            certsDir.listFiles()?.forEach { it.delete() }
            preferences.edit { remove(KEY_HAS_CERTS) }
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
