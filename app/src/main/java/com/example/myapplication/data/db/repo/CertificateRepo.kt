package com.example.myapplication.data.db.repo

import java.io.File

interface CertificateRepo {
    fun hasCertificates(): Boolean
    fun getCertificateFiles(): List<File>
    @Throws(CertificateImportException::class)
    fun importCertificates(fileBytes: ByteArray)
    fun clearCertificates()
}

// Custom exception for certificate import errors
class CertificateImportException(message: String) : Exception(message)
