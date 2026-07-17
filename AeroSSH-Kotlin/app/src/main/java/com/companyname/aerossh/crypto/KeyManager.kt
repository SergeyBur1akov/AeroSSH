package com.companyname.aerossh.crypto

import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64

object KeyManager {
    data class GeneratedKey(val name: String, val type: String, val publicKey: String, val privateKey: String)

    fun generateEd25519(name: String): GeneratedKey { val kp = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); return GeneratedKey(name, "Ed25519", formatPub(kp.public, name), formatPriv(kp)) }
    fun generateRSA(name: String, bits: Int = 2048): GeneratedKey { val kg = KeyPairGenerator.getInstance("RSA"); kg.initialize(bits); val kp = kg.generateKeyPair(); return GeneratedKey(name, "RSA-$bits", formatPub(kp.public, name), formatPriv(kp)) }
    fun generateECDSA(name: String): GeneratedKey { val kg = KeyPairGenerator.getInstance("EC"); kg.initialize(java.security.spec.ECGenParameterSpec("secp256r1")); val kp = kg.generateKeyPair(); return GeneratedKey(name, "ECDSA", formatPub(kp.public, name), formatPriv(kp)) }
    fun importOpenSSH(name: String, pem: String): GeneratedKey { val kf = OpenSSHKeyFile(); kf.init(pem.byteInputStream()); return GeneratedKey(name, KeyType.detect(kf.publicKey).toString(), formatPub(kf.publicKey, name), pem) }
    fun getPublicKeyFingerprint(pubKeyStr: String): String { val bytes = Base64.getDecoder().decode(pubKeyStr.split(" ").getOrNull(1) ?: ""); return java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02x".format(it) } }
    private fun formatPub(pub: java.security.PublicKey, name: String): String = "${pub.algorithm} ${Base64.getEncoder().encodeToString(pub.encoded)} $name"
    private fun formatPriv(kp: java.security.KeyPair): String = "-----BEGIN OPENSSH PRIVATE KEY-----\n${Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(kp.private.encoded)}\n-----END OPENSSH PRIVATE KEY-----"
}
