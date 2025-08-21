package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.base64Decode
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class IdFlixProvider {
    
    companion object {
        private const val PROVIDER_NAME = "JIdFlix"
        private const val DOMAIN = "https://tv.idlixofficials.com"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("IdFlixProvider", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$DOMAIN/",
                    "Origin" to DOMAIN
                )
                
                // Build search URL
                val urlSearch = if (movieInfo.type == "tv") {
                    val slug = movieInfo.title?.replace(" ", "-")?.lowercase() ?: ""
                    "$DOMAIN/episode/$slug-season-${movieInfo.season}-episode-${movieInfo.episode}"
                } else {
                    val slug = movieInfo.title?.replace(" ", "-")?.lowercase() ?: ""
                    "$DOMAIN/movie/$slug-${movieInfo.year}"
                }
                
                Log.d("IdFlixProvider", "Search URL: $urlSearch")
                
                // Step 1: Get the main page
                val searchResponse = app.get(urlSearch, headers = headers)
                val searchDoc = Jsoup.parse(searchResponse.text)
                
                // Extract the post ID
                val postId = searchDoc.select("#player-option-1").attr("data-post")
                if (postId.isEmpty()) {
                    Log.w("IdFlixProvider", "No post ID found")
                    return false
                }
                
                Log.d("IdFlixProvider", "Post ID: $postId")
                
                // Get all player options
                val playerOptions = mutableListOf<String>()
                searchDoc.select(".dooplay_player_option").forEach { element ->
                    val nume = element.attr("data-nume")
                    if (nume.isNotEmpty()) {
                        playerOptions.add(nume)
                    }
                }
                
                Log.d("IdFlixProvider", "Player options: $playerOptions")
                
                // Step 2: Try each player option
                for (nume in playerOptions) {
                    try {
                        val ajaxHeaders = mapOf(
                            "content-type" to "application/x-www-form-urlencoded; charset=UTF-8",
                            "Referer" to urlSearch,
                            "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        )
                        
                        val postData = mapOf(
                            "action" to "doo_player_ajax",
                            "post" to postId,
                            "nume" to nume,
                            "type" to if (movieInfo.type == "movie") "movie" else "tv"
                        )
                        
                        val ajaxUrl = "$DOMAIN/wp-admin/admin-ajax.php"
                        val embedResponse = app.post(ajaxUrl, headers = ajaxHeaders, data = postData)
                        val embedData = embedResponse.parsedSafe<EmbedData>()
                        
                        Log.d("IdFlixProvider", "Embed data for $nume: $embedData")
                        
                        if (embedData?.embed_url == null || embedData.key == null) {
                            Log.w("IdFlixProvider", "No embed URL or key for $nume")
                            continue
                        }
                        
                        // Decrypt the embed URL
                        val decryptedUrl = try {
                            decryptAES(embedData.embed_url, embedData.key)
                        } catch (e: Exception) {
                            Log.w("IdFlixProvider", "Failed to decrypt for $nume: ${e.message}")
                            continue
                        }
                        
                        Log.d("IdFlixProvider", "Decrypted URL: $decryptedUrl")
                        
                        if (decryptedUrl.isNotEmpty() && decryptedUrl.startsWith("http")) {
                            // Try to extract video from the decrypted URL
                            try {
                                val embedPageResponse = app.get(decryptedUrl, headers = headers)
                                val embedPageText = embedPageResponse.text
                                
                                // Look for various video URL patterns
                                val patterns = listOf(
                                    """(https?://[^"'\s]+\.m3u8[^"'\s]*)""".toRegex(),
                                    """(https?://[^"'\s]+\.mp4[^"'\s]*)""".toRegex(),
                                    """"file":\s*"([^"]+)"""".toRegex(),
                                    """src:\s*["']([^"']+)["']""".toRegex()
                                )
                                
                                for (pattern in patterns) {
                                    val match = pattern.find(embedPageText)
                                    if (match != null) {
                                        val videoUrl = match.groupValues[1]
                                        
                                        if (videoUrl.isNotEmpty() && videoUrl.startsWith("http")) {
                                            callback.invoke(
                                                ExtractorLink(
                                                    source = PROVIDER_NAME,
                                                    name = "$PROVIDER_NAME - Player $nume",
                                                    url = videoUrl,
                                                    referer = decryptedUrl,
                                                    quality = Qualities.Unknown.value,
                                                    type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                                    headers = headers
                                                )
                                            )
                                            
                                            Log.d("IdFlixProvider", "Successfully extracted video link: $videoUrl")
                                            return true
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("IdFlixProvider", "Error extracting from embed: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("IdFlixProvider", "Error with player $nume: ${e.message}")
                    }
                }
                
                Log.w("IdFlixProvider", "No video links found")
                false
                
            } catch (e: Exception) {
                Log.e("IdFlixProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
        
        private fun decryptAES(encryptedData: String, key: String): String {
            return try {
                // Parse the encrypted JSON
                val encryptedJson = AppUtils.parseJson<EncryptedJson>(encryptedData)
                    ?: return ""
                
                val cipherText = base64Decode(encryptedJson.ct)
                val salt = base64Decode(encryptedJson.s)
                
                // Derive key and IV from password and salt
                val keyIv = deriveKeyAndIV(key, salt)
                val secretKey = SecretKeySpec(keyIv.first, "AES")
                
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, javax.crypto.spec.IvParameterSpec(keyIv.second))
                
                val decrypted = cipher.doFinal(cipherText)
                String(decrypted, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w("IdFlixProvider", "AES decryption failed: ${e.message}")
                ""
            }
        }
        
        private fun deriveKeyAndIV(password: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
            val passwordBytes = password.toByteArray()
            val keyLength = 32 // 256 bits
            val ivLength = 16  // 128 bits
            
            val result = ByteArray(keyLength + ivLength)
            var currentHash = ByteArray(0)
            var i = 0
            
            while (i < keyLength + ivLength) {
                val md = java.security.MessageDigest.getInstance("MD5")
                md.update(currentHash)
                md.update(passwordBytes)
                md.update(salt)
                currentHash = md.digest()
                
                val copyLength = minOf(currentHash.size, result.size - i)
                System.arraycopy(currentHash, 0, result, i, copyLength)
                i += copyLength
            }
            
            val key = result.sliceArray(0 until keyLength)
            val iv = result.sliceArray(keyLength until keyLength + ivLength)
            
            return Pair(key, iv)
        }
    }
    
    data class EmbedData(
        @JsonProperty("embed_url") val embed_url: String?,
        @JsonProperty("key") val key: String?
    )
    
    data class EncryptedJson(
        @JsonProperty("ct") val ct: String,
        @JsonProperty("iv") val iv: String,
        @JsonProperty("s") val s: String
    )
}
