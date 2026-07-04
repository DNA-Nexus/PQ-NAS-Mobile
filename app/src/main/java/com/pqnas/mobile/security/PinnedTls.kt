package com.pqnas.mobile.security

import android.annotation.SuppressLint
import android.util.Base64
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object PinnedTls {
    private const val SPKI_SHA256_PREFIX = "sha256/"

    /**
     * Public CA trust mode.
     *
     * Use this when the service is exposed through Cloudflare, an operator reverse
     * proxy, or another managed HTTPS edge where SPKI pins may rotate outside
     * the NAS owner's control.
     *
     * Security model:
     * - Android default trust store validates the certificate chain.
     * - OkHttp validates the hostname against the configured origin.
     * - Pairing still requires the QR origin to match the user-selected server.
     */
    const val PUBLIC_CA_TRUST = "public_ca"

    fun normalizeSpkiSha256Pin(raw: String): String? {
        val cleaned = raw.trim().replace(" ", "+")
        if (!cleaned.startsWith(SPKI_SHA256_PREFIX)) return null

        val b64 = cleaned.removePrefix(SPKI_SHA256_PREFIX).trim()
        if (b64.isBlank()) return null

        val decoded = runCatching {
            Base64.decode(b64, Base64.DEFAULT)
        }.getOrNull() ?: return null

        if (decoded.size != 32) return null

        return SPKI_SHA256_PREFIX + Base64.encodeToString(decoded, Base64.NO_WRAP)
    }

    fun normalizePairTrust(raw: String): String? {
        val cleaned = raw.trim()
        val mode = cleaned.lowercase()
            .replace("-", "_")
            .replace(" ", "_")

        if (mode == PUBLIC_CA_TRUST ||
            mode == "system_ca" ||
            mode == "android_ca" ||
            mode == "default_ca"
        ) {
            return PUBLIC_CA_TRUST
        }

        return normalizeSpkiSha256Pin(cleaned)
    }

    fun usesPublicCaTrust(raw: String): Boolean =
        normalizePairTrust(raw) == PUBLIC_CA_TRUST

    fun applyTo(builder: OkHttpClient.Builder, tlsPinSha256: String) {
        val normalizedTrust = normalizePairTrust(tlsPinSha256)
            ?: throw IllegalArgumentException("Malformed server TLS trust setting")

        if (normalizedTrust == PUBLIC_CA_TRUST) {
            // Leave OkHttp on the Android platform defaults:
            // normal CA chain validation + hostname verification.
            return
        }

        val trustManager = SpkiPinTrustManager(normalizedTrust)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
    }

    fun certificateSpkiSha256Pin(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(certificate.publicKey.encoded)
        return SPKI_SHA256_PREFIX + Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun systemDefaultTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)

        return tmf.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: throw IllegalStateException("No default X509TrustManager available")
    }

    // Lint warning is intentional here: this trust manager is only used for
    // QR-carried SPKI pin trust on self-hosted/internal servers.
    // It does not disable validation silently: public CA mode keeps OkHttp defaults,
    // and pinned mode still requires valid dates plus exact leaf SPKI SHA-256 match.
    @SuppressLint("CustomX509TrustManager")
    private class SpkiPinTrustManager(
        private val expectedPin: String
    ) : X509TrustManager {
        private val defaultTrustManager: X509TrustManager = systemDefaultTrustManager()

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            defaultTrustManager.acceptedIssuers

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?
        ) {
            defaultTrustManager.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?
        ) {
            if (chain.isNullOrEmpty()) {
                throw CertificateException("Empty server certificate chain")
            }

            val leaf = chain[0]
            leaf.checkValidity()

            val actualPin = certificateSpkiSha256Pin(leaf)
            if (actualPin != expectedPin) {
                throw CertificateException("Server TLS identity does not match QR trust pin")
            }

            try {
                defaultTrustManager.checkServerTrusted(chain, authType)
            } catch (_: CertificateException) {
                // The QR-carried SPKI pin is the explicit trust root for
                // self-signed/internal servers.
                //
                // We still require:
                // - HTTPS
                // - non-expired leaf certificate
                // - exact leaf SPKI SHA-256 match
                // - normal OkHttp hostname verification after the TLS handshake
            }
        }
    }
}
