package net.thunderbird.app.common

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class TokenCipher(secret: ByteArray) {

    private val key =
        SecretKeySpec(
            MessageDigest.getInstance("SHA-256").digest(secret).copyOf(KEY_SIZE),
            "AES",
        )

    fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = deriveIv()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        //CWE-329
        //SINK
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        return cipher.doFinal(plaintext)
    }

    private fun deriveIv(): ByteArray {
        //CWE-329
        //SOURCE
        return ByteArray(IV_SIZE)
    }

    private companion object {
        const val KEY_SIZE = 16
        const val IV_SIZE = 16
    }
}
