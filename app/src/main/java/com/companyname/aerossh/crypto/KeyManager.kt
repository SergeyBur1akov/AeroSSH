package com.companyname.aerossh.crypto

import com.hierynomus.sshj.common.KeyType
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyFile
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import android.util.Base64

object KeyManager {
    data class GeneratedKey(val name: String, val type: String, val publicKey: String, val privateKey: CharArray) {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = name.hashCode()
        fun destroy() { privateKey.fill('\u0000') }
    }

    fun generateEd25519(name: String): GeneratedKey { val kp = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); val pk = formatPriv(kp).toCharArray(); val result = GeneratedKey(name, "Ed25519", formatPub(kp.public, name), pk); return result }
    fun generateRSA(name: String, bits: Int = 2048): GeneratedKey { val kg = KeyPairGenerator.getInstance("RSA"); kg.initialize(bits); val kp = kg.generateKeyPair(); val pk = formatPriv(kp).toCharArray(); return GeneratedKey(name, "RSA-$bits", formatPub(kp.public, name), pk) }
    fun generateECDSA(name: String): GeneratedKey { val kg = KeyPairGenerator.getInstance("EC"); kg.initialize(java.security.spec.ECGenParameterSpec("secp256r1")); val kp = kg.generateKeyPair(); val pk = formatPriv(kp).toCharArray(); return GeneratedKey(name, "ECDSA", formatPub(kp.public, name), pk) }
    fun importOpenSSH(name: String, pem: String): GeneratedKey { val kf = OpenSSHKeyFile(); kf.init(pem.byteInputStream()); val pk = pem.toCharArray(); return GeneratedKey(name, KeyType.detect(kf.publicKey).toString(), formatPub(kf.publicKey, name), pk) }
    fun getPublicKeyFingerprint(pubKeyStr: String): String { val bytes = Base64.decode(pubKeyStr.split(" ").getOrNull(1) ?: "", Base64.DEFAULT); return java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02x".format(it) } }
    private fun formatPub(pub: java.security.PublicKey, name: String): String = "${pub.algorithm} ${Base64.encodeToString(pub.encoded, Base64.NO_WRAP)} $name"
    private fun formatPriv(kp: java.security.KeyPair): String { val pem = "-----BEGIN OPENSSH PRIVATE KEY-----\n${Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP).chunked(64).joinToString("\n")}\n-----END OPENSSH PRIVATE KEY-----"; return pem }
    fun pemFromCharArray(chars: CharArray): String { val s = String(chars); return s }
}
