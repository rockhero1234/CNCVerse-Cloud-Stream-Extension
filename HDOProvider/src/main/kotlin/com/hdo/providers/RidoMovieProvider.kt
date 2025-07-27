package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup

class RidoMovieProvider {
    
    companion object {
        private const val PROVIDER_NAME = "LRIDOMOVIE"
        private const val DOMAIN = "https://ridomovies.tv"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("RidoMovieProvider", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$DOMAIN/",
                    "Origin" to DOMAIN
                )
                
                // Step 1: Search for the movie/show using TMDB ID
                val searchUrl = "$DOMAIN/core/api/search?q=${movieInfo.title?.replace(" ", "%20") ?: ""}"
                
                Log.d("RidoMovieProvider", "Search URL: $searchUrl")
                
                val searchResponse = app.get(searchUrl, headers = headers)
                val searchData = searchResponse.parsedSafe<SearchResponse>()
                
                if (searchData?.data?.items == null) {
                    Log.w("RidoMovieProvider", "No search results")
                    return false
                }
                
                // Find matching item by TMDB ID
                var slugDetail = ""
                for (item in searchData.data.items) {
                    if (item.contentable?.tmdbId == movieInfo.tmdbId) {
                        slugDetail = item.slug ?: ""
                        break
                    }
                }
                
                Log.d("RidoMovieProvider", "Slug detail: $slugDetail")
                
                if (slugDetail.isEmpty()) {
                    Log.w("RidoMovieProvider", "No matching slug found")
                    return false
                }
                
                var detailUrl = ""
                var episodeSlug = ""
                
                if (movieInfo.type == "tv") {
                    // For TV shows, get the episode list
                    val tvUrl = "$DOMAIN/tv/$slugDetail"
                    val tvResponse = app.get(tvUrl, headers = headers)
                    val tvText = tvResponse.text
                    
                    // Extract episode data from the page
                    val episodePattern = """"id":"(\d+)","slug":"([^"]+)"""".toRegex()
                    val episodeMatches = episodePattern.findAll(tvText)
                    
                    for (match in episodeMatches) {
                        val id = match.groupValues[1]
                        val slug = match.groupValues[2]
                        
                        // Check if this matches our season/episode
                        if (slug.contains("season-${movieInfo.season}") && 
                            slug.contains("episode-${movieInfo.episode}")) {
                            episodeSlug = slug
                            break
                        }
                    }
                    
                    if (episodeSlug.isNotEmpty()) {
                        detailUrl = "$DOMAIN/episode/$episodeSlug"
                    }
                } else {
                    // For movies
                    detailUrl = "$DOMAIN/movie/$slugDetail"
                }
                
                Log.d("RidoMovieProvider", "Detail URL: $detailUrl")
                
                if (detailUrl.isEmpty()) {
                    Log.w("RidoMovieProvider", "No detail URL found")
                    return false
                }
                
                // Step 2: Get the detail page
                val detailResponse = app.get(detailUrl, headers = headers)
                val detailText = detailResponse.text
                
                // Look for iframe URLs
                val iframePattern = """iframe[^>]+src=["']([^"']+)["']""".toRegex()
                val iframeMatches = iframePattern.findAll(detailText)
                
                for (match in iframeMatches) {
                    val iframeUrl = match.groupValues[1]
                    
                    if (iframeUrl.startsWith("http")) {
                        Log.d("RidoMovieProvider", "Found iframe: $iframeUrl")
                        
                        try {
                            val iframeResponse = app.get(iframeUrl, headers = headers)
                            val iframeText = iframeResponse.text
                            
                            // Look for video URLs
                            val videoPatterns = listOf(
                                """(https?://[^"'\s]+\.m3u8[^"'\s]*)""".toRegex(),
                                """(https?://[^"'\s]+\.mp4[^"'\s]*)""".toRegex(),
                                """"file":\s*"([^"]+)"""".toRegex(),
                                """src:\s*["']([^"']+)["']""".toRegex()
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
                                                referer = iframeUrl,
                                                quality = Qualities.Unknown.value,
                                                type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                                headers = headers
                                            )
                                        )
                                        
                                        Log.d("RidoMovieProvider", "Successfully extracted video link: $videoUrl")
                                        return true
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("RidoMovieProvider", "Error processing iframe: ${e.message}")
                        }
                    }
                }
                
                Log.w("RidoMovieProvider", "No video links found")
                false
                
            } catch (e: Exception) {
                Log.e("RidoMovieProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
    }
    
    data class SearchResponse(
        @JsonProperty("data") val data: SearchData?
    )
    
    data class SearchData(
        @JsonProperty("items") val items: List<SearchItem>?
    )
    
    data class SearchItem(
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("contentable") val contentable: Contentable?
    )
    
    data class Contentable(
        @JsonProperty("tmdbId") val tmdbId: Int?
    )
}
