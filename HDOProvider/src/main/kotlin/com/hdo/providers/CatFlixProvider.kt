package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup

class CatFlixProvider {
    
    companion object {
        private const val PROVIDER_NAME = "HCatFlix"
        private const val DOMAIN = "https://catflix.su"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("CatFlixProvider", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$DOMAIN/",
                    "Origin" to DOMAIN
                )
                
                // Step 1: Search for the movie/show
                val searchQuery = movieInfo.title?.replace(" ", "+") ?: ""
                val searchUrl = "$DOMAIN/search?keyword=$searchQuery"
                
                Log.d("CatFlixProvider", "Search URL: $searchUrl")
                
                val searchResponse = app.get(searchUrl, headers = headers)
                val searchDoc = Jsoup.parse(searchResponse.text)
                
                // Find matching result
                var movieLink = ""
                searchDoc.select(".film_list-wrap .flw-item").forEach { item ->
                    val title = item.select(".film-name a").text()
                    val href = item.select(".film-name a").attr("href")
                    val year = item.select(".film-infor span").first()?.text()?.replace(",", "") ?: ""
                    val type = item.select(".film-infor .fdi-type").text()
                    
                    if (title.isNotEmpty() && href.isNotEmpty() && movieLink.isEmpty()) {
                        val titleMatches = title.lowercase().contains(movieInfo.title?.lowercase() ?: "")
                        
                        if (titleMatches) {
                            if (movieInfo.type == "movie" && type.lowercase().contains("movie") && 
                                (movieInfo.year == null || year == movieInfo.year.toString())) {
                                movieLink = if (href.startsWith("/")) "$DOMAIN$href" else href
                            } else if (movieInfo.type == "tv" && type.lowercase().contains("tv")) {
                                movieLink = if (href.startsWith("/")) "$DOMAIN$href" else href
                            }
                        }
                    }
                }
                
                Log.d("CatFlixProvider", "Movie link: $movieLink")
                
                if (movieLink.isEmpty()) {
                    Log.w("CatFlixProvider", "No matching result found")
                    return false
                }
                
                // Step 2: Get the movie/show page
                val movieResponse = app.get(movieLink, headers = headers)
                val movieDoc = Jsoup.parse(movieResponse.text)
                
                var watchUrl = movieLink
                
                // If it's a TV show, find the specific episode
                if (movieInfo.type == "tv") {
                    // Look for season/episode selector
                    val seasonElements = movieDoc.select(".ss-list a")
                    var episodeUrl = ""
                    
                    for (seasonElement in seasonElements) {
                        val seasonText = seasonElement.text()
                        val seasonMatch = """Season (\d+)""".toRegex().find(seasonText)
                        
                        if (seasonMatch != null && seasonMatch.groupValues[1] == movieInfo.season.toString()) {
                            val seasonUrl = "$DOMAIN${seasonElement.attr("href")}"
                            
                            val seasonResponse = app.get(seasonUrl, headers = headers)
                            val seasonDoc = Jsoup.parse(seasonResponse.text)
                            
                            // Find the specific episode
                            val episodeElements = seasonDoc.select(".ep-item a")
                            for (episodeElement in episodeElements) {
                                val episodeText = episodeElement.text()
                                val episodeMatch = """(\d+)""".toRegex().find(episodeText)
                                
                                if (episodeMatch != null && episodeMatch.groupValues[1] == movieInfo.episode.toString()) {
                                    episodeUrl = "$DOMAIN${episodeElement.attr("href")}"
                                    break
                                }
                            }
                            break
                        }
                    }
                    
                    if (episodeUrl.isNotEmpty()) {
                        watchUrl = episodeUrl
                    }
                }
                
                Log.d("CatFlixProvider", "Watch URL: $watchUrl")
                
                // Step 3: Get the watch page
                val watchResponse = app.get(watchUrl, headers = headers)
                val watchDoc = Jsoup.parse(watchResponse.text)
                
                // Extract server list
                val serverElements = watchDoc.select(".server-item")
                
                for (serverElement in serverElements) {
                    val serverName = serverElement.select("span").text()
                    val dataId = serverElement.attr("data-id")
                    
                    if (dataId.isEmpty()) continue
                    
                    Log.d("CatFlixProvider", "Trying server: $serverName (ID: $dataId)")
                    
                    try {
                        // Get the server link
                        val ajaxUrl = "$DOMAIN/ajax/get_link/$dataId"
                        
                        val ajaxResponse = app.get(ajaxUrl, headers = headers)
                        val ajaxData = ajaxResponse.parsedSafe<ServerData>()
                        
                        if (ajaxData?.link == null) {
                            Log.w("CatFlixProvider", "No link in ajax response")
                            continue
                        }
                        
                        val embedUrl = ajaxData.link
                        Log.d("CatFlixProvider", "Embed URL: $embedUrl")
                        
                        // Try to extract from the embed
                        if (embedUrl.startsWith("http")) {
                            val embedResponse = app.get(embedUrl, headers = headers)
                            val embedText = embedResponse.text
                            
                            // Look for video URLs
                            val videoPatterns = listOf(
                                """(https?://[^"'\s]+\.m3u8[^"'\s]*)""".toRegex(),
                                """(https?://[^"'\s]+\.mp4[^"'\s]*)""".toRegex(),
                                """"file":\s*"([^"]+)"""".toRegex(),
                                """src:\s*["']([^"']+)["']""".toRegex()
                            )
                            
                            for (pattern in videoPatterns) {
                                val videoMatch = pattern.find(embedText)
                                if (videoMatch != null) {
                                    val videoUrl = videoMatch.groupValues[1]
                                    
                                    if (videoUrl.isNotEmpty() && videoUrl.startsWith("http")) {
                                        callback.invoke(
                                            ExtractorLink(
                                                source = PROVIDER_NAME,
                                                name = "$PROVIDER_NAME - $serverName",
                                                url = videoUrl,
                                                referer = embedUrl,
                                                quality = Qualities.Unknown.value,
                                                type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                                headers = headers
                                            )
                                        )
                                        
                                        Log.d("CatFlixProvider", "Successfully extracted video link: $videoUrl")
                                        return true
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("CatFlixProvider", "Error with server $serverName: ${e.message}")
                    }
                }
                
                Log.w("CatFlixProvider", "No video links found")
                false
                
            } catch (e: Exception) {
                Log.e("CatFlixProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
    }
    
    data class ServerData(
        @JsonProperty("link") val link: String?
    )
}
