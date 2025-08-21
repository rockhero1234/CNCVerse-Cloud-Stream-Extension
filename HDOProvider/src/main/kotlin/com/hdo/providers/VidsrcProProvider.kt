package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.base64Decode
import com.fasterxml.jackson.annotation.JsonProperty

class VidsrcProProvider {
    
    companion object {
        private const val PROVIDER_NAME = "AVidsrcPro"
        private const val DOMAIN = "https://embed.su"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("VidsrcProProvider", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "https://embed.su/",
                    "Origin" to "https://embed.su"
                )
                
                // Build the search URL - exact same logic as JavaScript
                val urlSearch = if (movieInfo.type == "tv") {
                    "$DOMAIN/embed/tv/${movieInfo.tmdbId}/${movieInfo.season}/${movieInfo.episode}"
                } else {
                    "$DOMAIN/embed/movie/${movieInfo.tmdbId}"
                }
                
                Log.d("VidsrcProProvider", "URL Search: $urlSearch")
                
                // Step 1: Get the main page
                val htmlSearch = app.get(urlSearch, headers = headers)
                val textSearch = htmlSearch.text
                
                // Extract hash using exact same regex as JavaScript
                val hashEncodeRegex = """JSON\.parse\(atob\(\`([^\`]+)""".toRegex(RegexOption.IGNORE_CASE)
                val hashEncodeMatch = hashEncodeRegex.find(textSearch)
                val hashEncode = hashEncodeMatch?.groupValues?.get(1) ?: ""
                
                Log.d("VidsrcProProvider", "Hash Encode: $hashEncode")
                
                if (hashEncode.isEmpty()) {
                    Log.w("VidsrcProProvider", "No hash encode found")
                    return false
                }
                
                // Decode the hash - exact same as JavaScript libs.string_atob()
                val hashDecodeJson = String(base64Decode(hashEncode))
                val hashDecode = AppUtils.parseJson<HashData>(hashDecodeJson)
                
                Log.d("VidsrcProProvider", "Hash Decode: $hashDecode")
                
                val mEncrypt = hashDecode?.hash
                if (mEncrypt == null) {
                    Log.w("VidsrcProProvider", "No mEncrypt found")
                    return false
                }
                
                // First decode: base64 decode, split by '.', reverse each part - exact same logic
                val firstDecodeBase64 = String(base64Decode(mEncrypt))
                val firstDecode = firstDecodeBase64.split(".").map { item ->
                    item.reversed() // split("").reverse().join("") in JS = reversed() in Kotlin
                }
                
                // Second decode: join, reverse, base64 decode, parse JSON - exact same logic
                val secondDecodeBase64 = firstDecode.joinToString("").reversed()
                val secondDecodeJson = String(base64Decode(secondDecodeBase64))
                val secondDecode = AppUtils.parseJson<List<SourceItem>>(secondDecodeJson)
                
                Log.d("VidsrcProProvider", "Second Decode: $secondDecode")
                
                if (secondDecode == null || secondDecode.isEmpty()) {
                    Log.w("VidsrcProProvider", "No second decode data")
                    return false
                }
                
                // Find the "viper" item - exact same logic as JavaScript
                for (item in secondDecode) {
                    if (item.name.lowercase() == "viper") {
                        val urlDirect = "$DOMAIN/api/e/${item.hash}"
                        
                        Log.d("VidsrcProProvider", "URL Direct: $urlDirect")
                        
                        val dataDirect = app.get(urlDirect, headers = headers).parsedSafe<DirectData>()
                        
                        Log.d("VidsrcProProvider", "Data Direct: $dataDirect")
                        
                        if (dataDirect?.source == null) {
                            Log.w("VidsrcProProvider", "No source in data direct")
                            continue
                        }
                        
                        // Extract subtitles - exact same logic as JavaScript
                        val tracks = mutableListOf<SubtitleFile>()
                        try {
                            dataDirect.subtitles?.forEach { itemTrack ->
                                val labelMatch = """^([A-z]+)""".toRegex(RegexOption.IGNORE_CASE).find(itemTrack.label)
                                val label = labelMatch?.groupValues?.get(1) ?: ""
                                
                                if (label.isNotEmpty()) {
                                    tracks.add(
                                        SubtitleFile(
                                            lang = label,
                                            url = itemTrack.file
                                        )
                                    )
                                }
                            }
                        } catch (etrack: Exception) {
                            Log.w("VidsrcProProvider", "Error processing subtitles: ${etrack.message}")
                        }
                        
                        Log.d("VidsrcProProvider", "Tracks: $tracks")
                        
                        val urlDirectFinal = dataDirect.source
                        
                        // Create the extractor link - same as JavaScript libs.embed_callback
                        callback.invoke(
                            ExtractorLink(
                                source = PROVIDER_NAME,
                                name = PROVIDER_NAME,
                                url = urlDirectFinal,
                                referer = DOMAIN,
                                quality = Qualities.P1080.value, // quality: 1080 in JS
                                type = ExtractorLinkType.M3U8, // 'Hls' in JS
                                headers = headers
                            )
                        )
                        
                        Log.d("VidsrcProProvider", "Successfully extracted video link: $urlDirectFinal")
                        return true
                    }
                }
                
                Log.w("VidsrcProProvider", "No viper source found")
                false
                
            } catch (e: Exception) {
                Log.e("VidsrcProProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
    }
    
    data class HashData(
        @JsonProperty("hash") val hash: String?
    )
    
    data class SourceItem(
        @JsonProperty("name") val name: String,
        @JsonProperty("hash") val hash: String
    )
    
    data class DirectData(
        @JsonProperty("source") val source: String?,
        @JsonProperty("subtitles") val subtitles: List<SubtitleTrack>?
    )
    
    data class SubtitleTrack(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String
    )
}
