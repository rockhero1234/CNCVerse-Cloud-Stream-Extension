package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup

class YMoviesProvider {
    
    companion object {
        private const val PROVIDER_NAME = "YMovies"
        private const val DOMAIN = "https://ymovies.vip"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("YMoviesProvider", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$DOMAIN/",
                    "Origin" to DOMAIN
                )
                
                // Step 1: Search for the movie/show
                val searchQuery = "${movieInfo.title}${if (movieInfo.year != null) " ${movieInfo.year}" else ""}"
                    .replace(" ", "+")
                
                val searchUrl = "$DOMAIN/movie/search/$searchQuery"
                
                Log.d("YMoviesProvider", "Search URL: $searchUrl")
                
                val searchResponse = app.get(searchUrl, headers = headers)
                val searchDoc = Jsoup.parse(searchResponse.text)
                
                // Find matching result
                var linkDetail = ""
                searchDoc.select(".ml-item").forEach { item ->
                    val title = item.select(".mi-name a").text()
                    val href = item.select(".mi-name a").attr("href")
                    val year = item.select(".mi-meta span").first()?.text() ?: ""
                    val type = item.select(".mim-type").text()
                    
                    if (title.isNotEmpty() && href.isNotEmpty() && linkDetail.isEmpty()) {
                        val titleMatches = title.lowercase().contains(movieInfo.title?.lowercase() ?: "")
                        
                        if (titleMatches) {
                            if (movieInfo.type == "movie" && type.lowercase() == "movie" && 
                                (movieInfo.year == null || year == movieInfo.year.toString())) {
                                linkDetail = if (href.startsWith("/")) "$DOMAIN$href" else href
                            } else if (movieInfo.type == "tv" && type.lowercase() == "tv") {
                                linkDetail = if (href.startsWith("/")) "$DOMAIN$href" else href
                            }
                        }
                    }
                }
                
                Log.d("YMoviesProvider", "Link detail: $linkDetail")
                
                if (linkDetail.isEmpty()) {
                    Log.w("YMoviesProvider", "No matching result found")
                    return false
                }
                
                // Extract ID from URL
                val idParts = linkDetail.split("-")
                val id = idParts.lastOrNull() ?: ""
                
                Log.d("YMoviesProvider", "ID: $id")
                
                if (id.isEmpty()) {
                    Log.w("YMoviesProvider", "No ID found")
                    return false
                }
                
                // Step 2: Get episode/movie servers
                val episodeUrl = if (movieInfo.type == "movie") {
                    "$DOMAIN/ajax/movie/episode/servers/${id}_1_full"
                } else {
                    "$DOMAIN/ajax/movie/episode/servers/${id}_${movieInfo.season}_${movieInfo.episode}"
                }
                
                Log.d("YMoviesProvider", "Episode URL: $episodeUrl")
                
                val episodeResponse = app.get(episodeUrl, headers = headers)
                val episodeData = episodeResponse.parsedSafe<EpisodeData>()
                
                if (episodeData?.status != true || episodeData.html == null) {
                    Log.w("YMoviesProvider", "No episode data found")
                    return false
                }
                
                val episodeDoc = Jsoup.parse(episodeData.html)
                
                // Extract server tokens
                val tokens = mutableListOf<String>()
                episodeDoc.select(".link-item").forEach { item ->
                    val dataId = item.attr("data-id")
                    if (dataId.isNotEmpty()) {
                        tokens.add(dataId)
                    }
                }
                
                Log.d("YMoviesProvider", "Tokens: $tokens")
                
                // Step 3: Try each server token
                for (token in tokens) {
                    try {
                        val embedUrl = "$DOMAIN/ajax/movie/episode/sources/$token"
                        
                        val embedResponse = app.get(embedUrl, headers = headers)
                        val embedData = embedResponse.parsedSafe<EmbedResponse>()
                        
                        Log.d("YMoviesProvider", "Embed data for $token: $embedData")
                        
                        if (embedData?.link == null) {
                            Log.w("YMoviesProvider", "No embed link for token: $token")
                            continue
                        }
                        
                        val embedLink = embedData.link
                        Log.d("YMoviesProvider", "Embed link: $embedLink")
                        
                        // Try to extract video from embed
                        if (embedLink.startsWith("http")) {
                            try {
                                val embedPageResponse = app.get(embedLink, headers = headers)
                                val embedPageText = embedPageResponse.text
                                
                                // Look for video URLs
                                val videoPatterns = listOf(
                                    """(https?://[^"'\s]+\.m3u8[^"'\s]*)""".toRegex(),
                                    """(https?://[^"'\s]+\.mp4[^"'\s]*)""".toRegex(),
                                    """"file":\s*"([^"]+)"""".toRegex(),
                                    """src:\s*["']([^"']+)["']""".toRegex()
                                )
                                
                                for (pattern in videoPatterns) {
                                    val videoMatch = pattern.find(embedPageText)
                                    if (videoMatch != null) {
                                        val videoUrl = videoMatch.groupValues[1]
                                        
                                        if (videoUrl.isNotEmpty() && videoUrl.startsWith("http")) {
                                            callback.invoke(
                                                ExtractorLink(
                                                    source = PROVIDER_NAME,
                                                    name = "$PROVIDER_NAME - Server $token",
                                                    url = videoUrl,
                                                    referer = embedLink,
                                                    quality = Qualities.Unknown.value,
                                                    type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                                    headers = headers
                                                )
                                            )
                                            
                                            Log.d("YMoviesProvider", "Successfully extracted video link: $videoUrl")
                                            return true
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("YMoviesProvider", "Error extracting from embed: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("YMoviesProvider", "Error with token $token: ${e.message}")
                    }
                }
                
                Log.w("YMoviesProvider", "No video links found")
                false
                
            } catch (e: Exception) {
                Log.e("YMoviesProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
    }
    
    data class EpisodeData(
        @JsonProperty("status") val status: Boolean?,
        @JsonProperty("html") val html: String?
    )
    
    data class EmbedResponse(
        @JsonProperty("link") val link: String?
    )
}
