package com.example.myapplication.data.db.repo

import android.content.Context
import android.util.Log
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
        private const val TAG = "ProdCertificateRepo"
        private const val KEY_HAS_CERTS = "has_certs"

        // Certificate names in priority order (required first, optional after)
        private const val REQUIRED_CERT = "go_bkk_hu.pem"
        private val OPTIONAL_CERTS = listOf(
            "eszigno_intermediate.pem",
            "eszigno_root.pem"
        )

        private val ALL_POSSIBLE_CERTS = listOf(REQUIRED_CERT) + OPTIONAL_CERTS
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
    override fun importFromZipBytes(zipBytes: ByteArray) {
        Log.i(TAG, "importFromZipBytes: Starting certificate import (${zipBytes.size} bytes)")

        // 1. Extract all .pem files from zip
        val pemFiles = mutableMapOf<String, ByteArray>()
        try {
            Log.i(TAG, "importFromZipBytes: Opening ZipInputStream")
            ZipInputStream(zipBytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                var entryCount = 0
                while (entry != null) {
                    entryCount++
                    Log.i(TAG, "importFromZipStream: Found entry #$entryCount: ${entry.name} (size=${entry.size})")

                    if (entry.name.endsWith(".pem")) {
                        val fileName = File(entry.name).name // Strip path
                        Log.i(TAG, "importFromZipStream: Extracting .pem file: $fileName")
                        val bytes = zis.readBytes()
                        Log.i(TAG, "importFromZipStream: Read ${bytes.size} bytes from $fileName")
                        pemFiles[fileName] = bytes
                    } else {
                        Log.i(TAG, "importFromZipStream: Skipping non-.pem file: ${entry.name}")
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                Log.i(TAG, "importFromZipBytes: Finished reading ZIP. Total entries: $entryCount, PEM files: ${pemFiles.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "importFromZipBytes: Failed to read ZIP file", e)
            throw CertificateImportException("Failed to read ZIP file: ${e.message}")
        }

        Log.i(TAG, "importFromZipBytes: Extracted ${pemFiles.size} PEM files: ${pemFiles.keys.joinToString()}")

        // 2. Validate: at least 1 .pem, maximum 3 .pem files
        Log.i(TAG, "importFromZipBytes: Validating file count")
        if (pemFiles.isEmpty()) {
            Log.e(TAG, "importFromZipBytes: No .pem files found in ZIP")
            throw CertificateImportException("ZIP must contain at least one .pem file")
        }
        if (pemFiles.size > 3) {
            Log.e(TAG, "importFromZipBytes: Too many .pem files: ${pemFiles.size}")
            throw CertificateImportException("ZIP must contain at most 3 .pem files, found ${pemFiles.size}")
        }
        Log.i(TAG, "importFromZipBytes: File count valid (${pemFiles.size})")

        // 3. Validate: REQUIRED_CERT (go_bkk_hu.pem) must be present
        Log.i(TAG, "importFromZipBytes: Checking for required certificate: $REQUIRED_CERT")
        if (!pemFiles.containsKey(REQUIRED_CERT)) {
            Log.e(TAG, "importFromZipBytes: Required certificate missing. Found: ${pemFiles.keys.joinToString()}")
            throw CertificateImportException("ZIP must contain $REQUIRED_CERT (required)")
        }
        Log.i(TAG, "importFromZipBytes: Required certificate found")

        // 4. Validate: only allow known certificate names
        Log.i(TAG, "importFromZipBytes: Validating certificate names")
        val unknownCerts = pemFiles.keys - ALL_POSSIBLE_CERTS.toSet()
        if (unknownCerts.isNotEmpty()) {
            Log.e(TAG, "importFromZipBytes: Unknown certificates: ${unknownCerts.joinToString()}")
            throw CertificateImportException("Unknown certificate files: ${unknownCerts.joinToString()}")
        }
        Log.i(TAG, "importFromZipBytes: All certificate names are valid")

        // 5. Validate each is a valid X.509 certificate
        Log.i(TAG, "importFromZipBytes: Validating X.509 certificate format")
        val certFactory = CertificateFactory.getInstance("X.509")
        pemFiles.forEach { (name, pemBytes) ->
            try {
                Log.i(TAG, "importFromZipBytes: Parsing certificate: $name (${pemBytes.size} bytes)")

                // Log first 100 bytes to see what format it is
                val preview = pemBytes.take(100).map { it.toInt().toChar() }.joinToString("")
                Log.i(TAG, "importFromZipBytes: First 100 chars: $preview")

                // Check if it looks like PEM format
                val content = String(pemBytes, Charsets.UTF_8)
                if (!content.contains("-----BEGIN CERTIFICATE-----")) {
                    Log.e(TAG, "importFromZipBytes: File $name does not appear to be in PEM format (missing -----BEGIN CERTIFICATE-----)")
                    throw CertificateImportException("Certificate file $name must be in PEM format (text format starting with -----BEGIN CERTIFICATE-----). The file appears to be in DER format or is not a valid certificate.")
                }

                certFactory.generateCertificate(pemBytes.inputStream())
                Log.i(TAG, "importFromZipBytes: Certificate $name is valid")
            } catch (e: CertificateImportException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "importFromZipBytes: Invalid certificate $name", e)
                throw CertificateImportException("Invalid certificate file $name: ${e.message}")
            }
        }
        Log.i(TAG, "importFromZipBytes: All certificates are valid X.509 format")

        // 6. Atomically write files (use .tmp → rename pattern)
        Log.i(TAG, "importFromZipBytes: Writing certificates to disk")
        val certsDir = getCertsDir()
        certsDir.mkdirs()
        Log.i(TAG, "importFromZipBytes: Certificate directory: ${certsDir.absolutePath}")

        try {
            pemFiles.forEach { (name, pemBytes) ->
                val tmpFile = File(certsDir, "$name.tmp")
                val finalFile = File(certsDir, name)

                Log.i(TAG, "importFromZipBytes: Writing $name to ${tmpFile.absolutePath}")
                FileOutputStream(tmpFile).use { fos ->
                    fos.write(pemBytes)
                    fos.fd.sync()
                }
                Log.i(TAG, "importFromZipBytes: Wrote ${pemBytes.size} bytes to $name.tmp")

                Log.i(TAG, "importFromZipBytes: Renaming ${tmpFile.name} to ${finalFile.name}")
                if (!tmpFile.renameTo(finalFile)) {
                    Log.e(TAG, "importFromZipBytes: Failed to rename $tmpFile to $finalFile")
                    throw IOException("Failed to rename $tmpFile to $finalFile")
                }
                Log.i(TAG, "importFromZipBytes: Successfully saved $name")
            }

            // 7. Set SharedPreferences flag only after all files written successfully
            Log.i(TAG, "importFromZipBytes: Setting has_certs flag to true")
            preferences.edit {
                putBoolean(KEY_HAS_CERTS, true)
            }

            Log.i(TAG, "importFromZipBytes: Certificate import completed successfully")

        } catch (e: Exception) {
            // Clean up on failure
            Log.e(TAG, "importFromZipBytes: Import failed, cleaning up", e)
            certsDir.listFiles()?.forEach {
                Log.i(TAG, "importFromZipBytes: Deleting ${it.name}")
                it.delete()
            }
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
