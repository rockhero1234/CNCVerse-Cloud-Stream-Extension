package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import java.util.*

class VidsrcProvider {
    
    companion object {
        private const val PROVIDER_NAME = "RVIDSRC"
        private const val DOMAIN = "https://vidsrc.xyz"
        
        suspend fun getVideoLinks(
            movieInfo: MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("VidsrcProvider", "Starting extraction for: ${movieInfo.title}")
                
                val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"
                
                // Build the search URL
                val urlSearch = if (movieInfo.type == "tv") {
                    "$DOMAIN/embed/tv/${movieInfo.tmdbId}/${movieInfo.season}-${movieInfo.episode}"
                } else {
                    "$DOMAIN/embed/${movieInfo.tmdbId}"
                }
                
                Log.d("VidsrcProvider", "Search URL: $urlSearch")
                
                // Step 1: Get the main embed page
                val searchResponse = app.get(
                    urlSearch,
                    headers = mapOf(
                        "user-agent" to userAgent,
                        "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                    )
                )
                
                val searchDoc = Jsoup.parse(searchResponse.text)
                var parseIframe = searchDoc.select("#player_iframe").attr("src")
                
                Log.d("VidsrcProvider", "Parse iframe: $parseIframe")
                
                if (parseIframe.isEmpty()) {
                    Log.w("VidsrcProvider", "No iframe found")
                    return false
                }
                
                // Handle relative URLs
                if (parseIframe.startsWith("//")) {
                    parseIframe = "https:$parseIframe"
                }
                
                // Step 2: Get the iframe content
                val iframeResponse = app.get(
                    parseIframe,
                    headers = mapOf(
                        "user-agent" to userAgent,
                        "referer" to urlSearch
                    )
                )
                
                val iframeText = iframeResponse.text
                
                // Extract the iframe src from JavaScript
                val iframeProRegex = """src *\: *'([^']+)""".toRegex(RegexOption.IGNORE_CASE)
                val iframeProMatch = iframeProRegex.find(iframeText)
                var iframePro = iframeProMatch?.groupValues?.get(1) ?: ""
                
                Log.d("VidsrcProvider", "Iframe pro: $iframePro")
                
                if (iframePro.isEmpty()) {
                    Log.w("VidsrcProvider", "No iframe pro found")
                    return false
                }
                
                // Extract hostname from parseIframe
                val host = parseIframe.substringAfter("://").substringBefore("/")
                Log.d("VidsrcProvider", "Host: $host")
                
                if (host.isEmpty()) {
                    Log.w("VidsrcProvider", "No host found")
                    return false
                }
                
                // Handle relative URLs for iframePro
                if (iframePro.startsWith("/")) {
                    iframePro = "https://$host$iframePro"
                }
                
                Log.d("VidsrcProvider", "Final iframe pro: $iframePro")
                
                // Step 3: Get the final content with the video URL
                val finalResponse = app.get(
                    iframePro,
                    headers = mapOf(
                        "user-agent" to userAgent,
                        "referer" to parseIframe
                    )
                )
                
                val finalText = finalResponse.text
                
                // Extract the encoded URL from JavaScript
                val encodeURLRegex = """player_parent[^,]*,\s*file:\s*'([^']+)""".toRegex(RegexOption.IGNORE_CASE)
                val encodeURLMatch = encodeURLRegex.find(finalText)
                val encodeURL = encodeURLMatch?.groupValues?.get(1) ?: ""
                
                Log.d("VidsrcProvider", "Encode URL: $encodeURL")
                
                if (encodeURL.isEmpty()) {
                    Log.w("VidsrcProvider", "No encoded URL found")
                    return false
                }
                
                // Check if it's an M3U8 file
                if (!encodeURL.contains("m3u8")) {
                    Log.w("VidsrcProvider", "Not an M3U8 file")
                    return false
                }
                
                // Create the extractor link
                callback.invoke(
                    ExtractorLink(
                        source = PROVIDER_NAME,
                        name = PROVIDER_NAME,
                        url = encodeURL,
                        referer = "https://$host/",
                        quality = Qualities.Unknown.value,
                        type = ExtractorLinkType.M3U8,
                        headers = mapOf(
                            "User-Agent" to userAgent,
                            "referer" to "https://$host/",
                            "Origin" to "https://$host"
                        )
                    )
                )
                
                Log.d("VidsrcProvider", "Successfully extracted video link: $encodeURL")
                true
                
            } catch (e: Exception) {
                Log.e("VidsrcProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
    }
    
    data class MovieInfo(
        val tmdbId: Int?,
        val imdbId: String?,
        val title: String?,
        val year: Int?,
        val season: Int?,
        val episode: Int?,
        val type: String // "movie" or "tv"
    )
}
