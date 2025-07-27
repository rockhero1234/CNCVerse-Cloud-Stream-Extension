package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup

class VidLinkProvider {
    
    companion object {
        private const val PROVIDER_NAME = "MVidlink"
        private const val DOMAIN = "https://vidlink.pro"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("VidLinkProvider", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$DOMAIN/",
                    "Origin" to DOMAIN
                )
                
                // Build the embed URL based on TMDB ID
                val embedUrl = if (movieInfo.type == "tv") {
                    "$DOMAIN/tv/${movieInfo.tmdbId}/${movieInfo.season}/${movieInfo.episode}"
                } else {
                    "$DOMAIN/movie/${movieInfo.tmdbId}"
                }
                
                Log.d("VidLinkProvider", "Embed URL: $embedUrl")
                
                // Get the embed page
                val embedResponse = app.get(embedUrl, headers = headers)
                val embedDoc = Jsoup.parse(embedResponse.text)
                
                // Look for server options
                val serverElements = embedDoc.select(".server-item, .link-item, .episode-server")
                
                for (serverElement in serverElements) {
                    val serverName = serverElement.text()
                    val dataLink = serverElement.attr("data-link")
                    val dataId = serverElement.attr("data-id")
                    val href = serverElement.attr("href")
                    
                    val serverUrl = when {
                        dataLink.isNotEmpty() -> dataLink
                        dataId.isNotEmpty() -> "$DOMAIN/play/$dataId"
                        href.isNotEmpty() && href.startsWith("http") -> href
                        href.isNotEmpty() -> "$DOMAIN$href"
                        else -> continue
                    }
                    
                    Log.d("VidLinkProvider", "Trying server: $serverName, URL: $serverUrl")
                    
                    try {
                        val serverResponse = app.get(serverUrl, headers = headers)
                        val serverText = serverResponse.text
                        
                        // Look for video URLs
                        val videoPatterns = listOf(
                            """(https?://[^"'\s]+\.m3u8[^"'\s]*)""".toRegex(),
                            """(https?://[^"'\s]+\.mp4[^"'\s]*)""".toRegex(),
                            """"file":\s*"([^"]+)"""".toRegex(),
                            """src:\s*["']([^"']+)["']""".toRegex(),
                            """"source":\s*"([^"]+)"""".toRegex(),
                            """video_url['"]\s*:\s*['"]([^'"]+)""".toRegex()
                        )
                        
                        for (pattern in videoPatterns) {
                            val videoMatch = pattern.find(serverText)
                            if (videoMatch != null) {
                                val videoUrl = videoMatch.groupValues[1]
                                
                                if (videoUrl.isNotEmpty() && videoUrl.startsWith("http")) {
                                    callback.invoke(
                                        ExtractorLink(
                                            source = PROVIDER_NAME,
                                            name = "$PROVIDER_NAME${if (serverName.isNotEmpty()) " - $serverName" else ""}",
                                            url = videoUrl,
                                            referer = serverUrl,
                                            quality = Qualities.Unknown.value,
                                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                            headers = headers
                                        )
                                    )
                                    
                                    Log.d("VidLinkProvider", "Successfully extracted video link: $videoUrl")
                                    return true
                                }
                            }
                        }
                        
                        // Also check for iframes
                        val serverDoc = Jsoup.parse(serverText)
                        val iframes = serverDoc.select("iframe")
                        
                        for (iframe in iframes) {
                            val src = iframe.attr("src")
                            if (src.isNotEmpty()) {
                                val fullSrc = if (src.startsWith("//")) "https:$src" 
                                             else if (src.startsWith("/")) "$DOMAIN$src" 
                                             else src
                                
                                if (fullSrc.startsWith("http")) {
                                    try {
                                        val iframeResponse = app.get(fullSrc, headers = headers)
                                        val iframeText = iframeResponse.text
                                        
                                        for (pattern in videoPatterns) {
                                            val videoMatch = pattern.find(iframeText)
                                            if (videoMatch != null) {
                                                val videoUrl = videoMatch.groupValues[1]
                                                
                                                if (videoUrl.isNotEmpty() && videoUrl.startsWith("http")) {
                                                    callback.invoke(
                                                        ExtractorLink(
                                                            source = PROVIDER_NAME,
                                                            name = "$PROVIDER_NAME - Iframe",
                                                            url = videoUrl,
                                                            referer = fullSrc,
                                                            quality = Qualities.Unknown.value,
                                                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                                            headers = headers
                                                        )
                                                    )
                                                    
                                                    Log.d("VidLinkProvider", "Successfully extracted iframe video link: $videoUrl")
                                                    return true
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("VidLinkProvider", "Error processing iframe: ${e.message}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("VidLinkProvider", "Error with server $serverName: ${e.message}")
                    }
                }
                
                Log.w("VidLinkProvider", "No video links found")
                false
                
            } catch (e: Exception) {
                Log.e("VidLinkProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
    }
}
