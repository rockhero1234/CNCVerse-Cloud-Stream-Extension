package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup

class UniqueStreamProvider {
    
    companion object {
        private const val PROVIDER_NAME = "DUniqueStream"
        private const val DOMAIN = "https://uniquestream.net"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("UniqueStreamProvider", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$DOMAIN/",
                    "Origin" to DOMAIN
                )
                
                // Build the embed URL
                val embedUrl = if (movieInfo.type == "tv") {
                    "$DOMAIN/streaming.php?id=${movieInfo.tmdbId}&season=${movieInfo.season}&episode=${movieInfo.episode}"
                } else {
                    "$DOMAIN/streaming.php?id=${movieInfo.tmdbId}"
                }
                
                Log.d("UniqueStreamProvider", "Embed URL: $embedUrl")
                
                // Get the embed page
                val embedResponse = app.get(embedUrl, headers = headers)
                val embedDoc = Jsoup.parse(embedResponse.text)
                
                // Look for iframe sources
                val iframes = embedDoc.select("iframe")
                
                for (iframe in iframes) {
                    val src = iframe.attr("src")
                    if (src.isNotEmpty() && src.startsWith("http")) {
                        Log.d("UniqueStreamProvider", "Found iframe: $src")
                        
                        try {
                            val iframeResponse = app.get(src, headers = headers)
                            val iframeText = iframeResponse.text
                            
                            // Look for video URLs
                            val videoPatterns = listOf(
                                """(https?://[^"'\s]+\.m3u8[^"'\s]*)""".toRegex(),
                                """(https?://[^"'\s]+\.mp4[^"'\s]*)""".toRegex(),
                                """"file":\s*"([^"]+)"""".toRegex(),
                                """src:\s*["']([^"']+)["']""".toRegex(),
                                """source:\s*["']([^"']+)["']""".toRegex()
                            )
                            
                            for (pattern in videoPatterns) {
                                val videoMatch = pattern.find(iframeText)
                                if (videoMatch != null) {
                                    val videoUrl = videoMatch.groupValues[1]
                                    
                                    if (videoUrl.isNotEmpty() && videoUrl.startsWith("http")) {
                                        callback.invoke(
                                            ExtractorLink(
                                                source = PROVIDER_NAME,
                                                name = PROVIDER_NAME,
                                                url = videoUrl,
                                                referer = src,
                                                quality = Qualities.Unknown.value,
                                                type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                                headers = headers
                                            )
                                        )
                                        
                                        Log.d("UniqueStreamProvider", "Successfully extracted video link: $videoUrl")
                                        return true
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("UniqueStreamProvider", "Error processing iframe: ${e.message}")
                        }
                    }
                }
                
                // Also check for direct video elements
                val videoElements = embedDoc.select("video source, video")
                for (videoElement in videoElements) {
                    val src = videoElement.attr("src")
                    if (src.isNotEmpty() && src.startsWith("http")) {
                        callback.invoke(
                            ExtractorLink(
                                source = PROVIDER_NAME,
                                name = PROVIDER_NAME,
                                url = src,
                                referer = embedUrl,
                                quality = Qualities.Unknown.value,
                                type = if (src.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                headers = headers
                            )
                        )
                        
                        Log.d("UniqueStreamProvider", "Successfully extracted direct video link: $src")
                        return true
                    }
                }
                
                Log.w("UniqueStreamProvider", "No video links found")
                false
                
            } catch (e: Exception) {
                Log.e("UniqueStreamProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
    }
}
