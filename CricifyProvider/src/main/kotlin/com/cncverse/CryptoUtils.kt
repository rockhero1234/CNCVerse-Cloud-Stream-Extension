package com.cncverse

import com.lagradost.cloudstream3.base64DecodeArray
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val SECRET = BuildConfig.CRICIFY_PROVIDER_SECRET
    
    fun decryptData(encryptedBase64: String): String? {
        return try {
            // Clean the base64 string - remove any whitespace, newlines, etc.
            val cleanBase64 = encryptedBase64.trim()
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .replace("\t", "")
            // 1. Extract IV - reverse first 16 chars
            val ivRaw = SECRET.substring(0, 16).reversed()
            val iv = ivRaw.toByteArray(Charsets.UTF_8)
            
            // 2. Extract AES key - reverse substring from 11 to len - 1
            val keyRaw = SECRET.substring(11, SECRET.length - 1).reversed()
            val key = keyRaw.toByteArray(Charsets.ISO_8859_1)
            
            
            // 3. Decode and decrypt
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKeySpec = SecretKeySpec(key, "AES")
            val ivParameterSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
            
            val decoded = base64DecodeArray(cleanBase64)
            
            val decrypted = cipher.doFinal(decoded)
            
            // 4. Remove PKCS5 padding
            val padLen = decrypted.last().toInt() and 0xFF
            if (padLen <= 0 || padLen > 16) {
                // Invalid padding, try without removing padding
                return String(decrypted, Charsets.UTF_8).trim('\u0000')
            }
            val plaintext = decrypted.sliceArray(0 until (decrypted.size - padLen))
            
            val result = String(plaintext, Charsets.UTF_8)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
