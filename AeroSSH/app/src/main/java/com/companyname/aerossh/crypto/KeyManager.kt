package com.companyname.aerossh.crypto

import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import java.io.StringWriter
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

object KeyManager {

    data class GeneratedKey(
        val name: String,
        val type: String,
        val publicKey: String,
        val privateKey: String
    )

    fun generateEd25519(name: String): GeneratedKey {
        // Ed25519 via BouncyCastle (SSHJ bundles it)
        val kpg = java.security.KeyPairGenerator.getInstance("Ed25519")
        val kp = kpg.generateKeyPair()

        val pub = formatOpenSSHpub(kp, name, "Ed25519")
        val priv = formatOpenSSHpriv(kp)

        return GeneratedKey(name, "Ed25519", pub, priv)
    }

    fun generateRSA(name: String, bits: Int = 2048): GeneratedKey {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(bits)
        val kp = kpg.generateKeyPair()

        val pub = formatOpenSSHpub(kp, name, "RSA-$bits")
        val priv = formatOpenSSHpriv(kp)

        return GeneratedKey(name, "RSA-$bits", pub, priv)
    }

    fun generateECDSA(name: String): GeneratedKey {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()

        val pub = formatOpenSSHpub(kp, name, "ECDSA")
        val priv = formatOpenSSHpriv(kp)

        return GeneratedKey(name, "ECDSA", pub, priv)
    }

    fun importOpenSSHPrivateKey(name: String, privateKeyPEM: String): GeneratedKey {
        val kf = OpenSSHKeyFile()
        kf.init(privateKeyPEM.byteInputStream())

        val pub = kf.publicKey
        val pubStr = formatPubKeyFromJava(pub, name)

        return GeneratedKey(name, KeyType.detect(pub).toString(), pubStr, privateKeyPEM)
    }

    fun getPublicKeyFingerprint(pubKeyStr: String): String {
        val bytes = Base64.getDecoder().decode(pubKeyStr.split(" ").getOrNull(1) ?: "")
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = md.digest(bytes)
        return hash.joinToString(":") { "%02x".format(it) }
    }

    private fun formatOpenSSHpub(kp: KeyPair, comment: String, type: String): String {
        val pub = kp.public
        val sshKey = when (pub) {
            is RSAPublicKey -> {
                val buf = StringBuilder()
                buf.append(writeSSHString("ssh-rsa"))
                buf.append(writeBigInt(pub.modulus))
                buf.append(writeBigInt(pub.publicExponent))
                "ssh-rsa " + Base64.getEncoder().encodeToString(SSHStringDecoder(buf.toString()).decode()) + " $comment"
            }
            else -> {
                // Fallback: use SSHJ's formatting
                val writer = StringWriter()
                net.schmizz.sshj.common.Factory.Named<net.schmizz.sshj.common.KeyPairProvider.KeyType> {
                    KeyType.RSA
                }
                val encoded = Base64.getEncoder().encodeToString(pub.encoded)
                "ssh-rsa $encoded $comment"
            }
        }
        return sshKey
    }

    private fun formatPubKeyFromJava(pub: java.security.PublicKey, comment: String): String {
        val encoded = Base64.getEncoder().encodeToString(pub.encoded)
        return "${pub.algorithm} $encoded $comment"
    }

    private fun formatOpenSSHpriv(kp: KeyPair): String {
        val encoder = Base64.getMimeEncoder(64, "\n".toByteArray())
        val encoded = encoder.encodeToString(kp.private.encoded)
        return "-----BEGIN OPENSSH PRIVATE KEY-----\n$encoded\n-----END OPENSSH PRIVATE KEY-----"
    }

    private fun writeSSHString(s: String): String {
        val bytes = s.toByteArray()
        return String(byteArrayOf(
            ((bytes.size shr 24) and 0xFF).toByte(),
            ((bytes.size shr 16) and 0xFF).toByte(),
            ((bytes.size shr 8) and 0xFF).toByte(),
            (bytes.size and 0xFF).toByte()
        )) + s
    }

    private fun writeBigInt(n: java.math.BigInteger): String {
        val bytes = n.toByteArray()
        // Remove leading zero if present
        val start = if (bytes[0] == 0.toByte()) 1 else 0
        val len = bytes.size - start
        return String(byteArrayOf(
            ((len shr 24) and 0xFF).toByte(),
            ((len shr 16) and 0xFF).toByte(),
            ((len shr 8) and 0xFF).toByte(),
            (len and 0xFF).toByte()
        )) + String(bytes, start, len)
    }

    private class SSHStringDecoder(private val s: String) {
        fun decode(): ByteArray {
            val parts = s.split(" ")
            return Base64.getDecoder().decode(parts.getOrNull(1) ?: "")
        }
    }
}
