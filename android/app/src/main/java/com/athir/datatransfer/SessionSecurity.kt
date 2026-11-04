package com.athir.datatransfer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object SessionSecurity {

    private val secureRandom = SecureRandom()

    fun generateSessionSecret(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, secureRandom)
        return kpg.generateKeyPair()
    }

    fun generateSelfSignedCertificate(keyPair: KeyPair, commonName: String = "Athir Local Transfer"): X509Certificate {
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 1000L * 60 * 60 * 24) // 1 day ago
        val notAfter = Date(now + 1000L * 60 * 60 * 24 * 365) // 1 year ahead
        val serial = BigInteger(64, secureRandom).abs()

        val derBytes = buildSelfSignedCertDer(keyPair, commonName, serial, notBefore, notAfter)
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(ByteArrayInputStream(derBytes)) as X509Certificate
    }

    fun getCertificateFingerprint(cert: X509Certificate): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    fun createServerSslContext(keyPair: KeyPair, cert: X509Certificate): SSLContext {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        val password = "athir_temp_password".toCharArray()
        keyStore.setKeyEntry("athir", keyPair.private, password, arrayOf(cert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, password)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, secureRandom)
        return sslContext
    }

    fun createClientSslContext(expectedFingerprint: String): SSLContext {
        val normalizedExpected = expectedFingerprint.replace(":", "").uppercase(Locale.ROOT)

        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain.isNullOrEmpty()) {
                    throw CertificateException("Empty certificate chain from server")
                }
                val serverCert = chain[0]
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(serverCert.encoded)
                val hex = digest.joinToString("") { "%02X".format(it) }

                if (hex != normalizedExpected) {
                    throw CertificateException("Fingerprint mismatch! Expected $normalizedExpected but received $hex")
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), secureRandom)
        return sslContext
    }

    // --- Minimal ASN.1 DER Builder for RFC 5280 X.509 Certificate ---
    private fun buildSelfSignedCertDer(
        keyPair: KeyPair,
        cn: String,
        serial: BigInteger,
        notBefore: Date,
        notAfter: Date
    ): ByteArray {
        val tbsOut = ByteArrayOutputStream()

        // serialNumber
        tbsOut.write(derInteger(serial))

        // signature algorithm: 1.2.840.113549.1.1.11 (sha256WithRSAEncryption)
        val sha256RsaOid = derSequence(
            byteArrayOf(
                0x06, 0x09,
                0x2A.toByte(), 0x86.toByte(), 0x48.toByte(), 0x86.toByte(),
                0xF7.toByte(), 0x0D.toByte(), 0x01.toByte(), 0x01.toByte(), 0x0B.toByte()
            ) + byteArrayOf(0x05, 0x00) // NULL
        )
        tbsOut.write(sha256RsaOid)

        // issuer: CN=cn
        val issuerName = buildDistinguishedName(cn)
        tbsOut.write(issuerName)

        // validity
        val validityOut = ByteArrayOutputStream()
        validityOut.write(derUtcTime(notBefore))
        validityOut.write(derUtcTime(notAfter))
        tbsOut.write(derSequence(validityOut.toByteArray()))

        // subject: CN=cn (same as issuer for self-signed)
        tbsOut.write(issuerName)

        // subjectPublicKeyInfo (already DER encoded in X.509 format by Java)
        tbsOut.write(keyPair.public.encoded)

        val tbsBytes = derSequence(tbsOut.toByteArray())

        // Compute RSA signature over tbsBytes
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(tbsBytes)
        val signatureValue = signer.sign()

        // Build full Certificate sequence
        val certOut = ByteArrayOutputStream()
        certOut.write(tbsBytes)
        certOut.write(sha256RsaOid)
        certOut.write(derBitString(signatureValue))

        return derSequence(certOut.toByteArray())
    }

    private fun buildDistinguishedName(cn: String): ByteArray {
        // AttributeTypeAndValue: commonName (2.5.4.3) = cn
        val cnBytes = cn.toByteArray(Charsets.UTF_8)
        val cnOid = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)
        val cnVal = derUtf8String(cnBytes)
        val atav = derSequence(cnOid + cnVal)
        val rdn = derSet(atav)
        return derSequence(rdn)
    }

    private fun derSequence(content: ByteArray): ByteArray {
        return derWrap(0x30, content)
    }

    private fun derSet(content: ByteArray): ByteArray {
        return derWrap(0x31, content)
    }

    private fun derUtf8String(content: ByteArray): ByteArray {
        return derWrap(0x0C, content)
    }

    private fun derBitString(content: ByteArray): ByteArray {
        // First byte of BIT STRING content is number of unused bits in last byte (0)
        return derWrap(0x03, byteArrayOf(0x00) + content)
    }

    private fun derInteger(value: BigInteger): ByteArray {
        return derWrap(0x02, value.toByteArray())
    }

    private fun derUtcTime(date: Date): ByteArray {
        val sdf = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val str = sdf.format(date).toByteArray(Charsets.US_ASCII)
        return derWrap(0x17, str)
    }

    private fun derWrap(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = content.size
        if (len < 128) {
            out.write(len)
        } else if (len < 256) {
            out.write(0x81)
            out.write(len)
        } else if (len < 65536) {
            out.write(0x82)
            out.write((len ushr 8) and 0xFF)
            out.write(len and 0xFF)
        } else {
            out.write(0x83)
            out.write((len ushr 16) and 0xFF)
            out.write((len ushr 8) and 0xFF)
            out.write(len and 0xFF)
        }
        out.write(content)
        return out.toByteArray()
    }
}
