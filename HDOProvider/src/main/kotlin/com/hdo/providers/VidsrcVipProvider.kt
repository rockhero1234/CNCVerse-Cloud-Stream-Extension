package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup

class VidsrcVipProvider {
    
    companion object {
        private const val PROVIDER_NAME = "XVidsrcVip"
        private const val DOMAIN = "https://vidsrc.vip"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("VidsrcVipProvider", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$DOMAIN/",
                    "Origin" to DOMAIN
                )
                
                // Build the embed URL
                val embedUrl = if (movieInfo.type == "tv") {
                    "$DOMAIN/embed/tv/${movieInfo.tmdbId}/${movieInfo.season}/${movieInfo.episode}"
                } else {
                    "$DOMAIN/embed/movie/${movieInfo.tmdbId}"
                }
                
                Log.d("VidsrcVipProvider", "Embed URL: $embedUrl")
                
                // Get the embed page
                val embedResponse = app.get(embedUrl, headers = headers)
                val embedDoc = Jsoup.parse(embedResponse.text)
                
                // Look for iframe sources
                val iframes = embedDoc.select("iframe")
                
                for (iframe in iframes) {
                    val src = iframe.attr("src")
                    if (src.isNotEmpty()) {
                        val fullSrc = if (src.startsWith("//")) "https:$src" 
                                     else if (src.startsWith("/")) "$DOMAIN$src" 
                                     else src
                        
                        if (fullSrc.startsWith("http")) {
                            Log.d("VidsrcVipProvider", "Found iframe: $fullSrc")
                            
                            try {
                                val iframeResponse = app.get(fullSrc, headers = headers)
                                val iframeText = iframeResponse.text
                                
                                // Look for video URLs
                                val videoPatterns = listOf(
                                    """(https?://[^"'\s]+\.m3u8[^"'\s]*)""".toRegex(),
                                    """(https?://[^"'\s]+\.mp4[^"'\s]*)""".toRegex(),
                                    """"file":\s*"([^"]+)"""".toRegex(),
                                    """src:\s*["']([^"']+)["']""".toRegex(),
                                    """"source":\s*"([^"]+)"""".toRegex()
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
                                                    referer = fullSrc,
                                                    quality = Qualities.Unknown.value,
                                                    type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                                    headers = headers
                                                )
                                            )
                                            
                                            Log.d("VidsrcVipProvider", "Successfully extracted video link: $videoUrl")
                                            return true
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("VidsrcVipProvider", "Error processing iframe: ${e.message}")
                            }
                        }
                    }
                }
                
                Log.w("VidsrcVipProvider", "No video links found")
                false
                
            } catch (e: Exception) {
                Log.e("VidsrcVipProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
    }
}
