package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class FMoviesProvider {
    
    companion object {
        private const val PROVIDER_NAME = "FMovies"
        private const val DOMAIN = "https://fmovies.si"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("FMoviesProvider", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$DOMAIN/",
                    "Origin" to DOMAIN
                )
                
                // Build search query
                val searchQuery = "${movieInfo.title}${if (movieInfo.year != null) " ${movieInfo.year}" else ""}"
                    .replace(" ", "%20")
                
                val searchUrl = "$DOMAIN/search?q=$searchQuery"
                
                Log.d("FMoviesProvider", "Search URL: $searchUrl")
                
                // Step 1: Search for the movie/show
                val searchResponse = app.get(searchUrl, headers = headers)
                val searchDoc = Jsoup.parse(searchResponse.text)
                
                // Find the first matching result
                val movieLink = searchDoc.select("div.film-item a").firstOrNull()?.attr("href")
                if (movieLink.isNullOrEmpty()) {
                    Log.w("FMoviesProvider", "No movie link found in search results")
                    return false
                }
                
                val fullMovieUrl = "$DOMAIN$movieLink"
                Log.d("FMoviesProvider", "Movie URL: $fullMovieUrl")
                
                // Step 2: Get the movie/show details page
                val movieResponse = app.get(fullMovieUrl, headers = headers)
                val movieDoc = Jsoup.parse(movieResponse.text)
                
                var episodeUrl = fullMovieUrl
                
                // If it's a TV show, need to find the specific episode
                if (movieInfo.type == "tv") {
                    val seasonElements = movieDoc.select("div.ss-list a")
                    var seasonFound = false
                    
                    for (seasonElement in seasonElements) {
                        val seasonText = seasonElement.text()
                        val seasonMatch = """Season (\d+)""".toRegex().find(seasonText)
                        
                        if (seasonMatch != null && seasonMatch.groupValues[1] == movieInfo.season.toString()) {
                            val seasonUrl = "$DOMAIN${seasonElement.attr("href")}"
                            
                            Log.d("FMoviesProvider", "Season URL: $seasonUrl")
                            
                            val seasonResponse = app.get(seasonUrl, headers = headers)
                            val seasonDoc = Jsoup.parse(seasonResponse.text)
                            
                            // Find the specific episode
                            val episodeElements = seasonDoc.select("div.ep-item a")
                            for (episodeElement in episodeElements) {
                                val episodeText = episodeElement.text()
                                val episodeMatch = """Ep (\d+)""".toRegex().find(episodeText)
                                
                                if (episodeMatch != null && episodeMatch.groupValues[1] == movieInfo.episode.toString()) {
                                    episodeUrl = "$DOMAIN${episodeElement.attr("href")}"
                                    seasonFound = true
                                    break
                                }
                            }
                            break
                        }
                    }
                    
                    if (!seasonFound) {
                        Log.w("FMoviesProvider", "Episode not found")
                        return false
                    }
                }
                
                Log.d("FMoviesProvider", "Episode URL: $episodeUrl")
                
                // Step 3: Get the episode/movie page to find video sources
                val episodeResponse = app.get(episodeUrl, headers = headers)
                val episodeDoc = Jsoup.parse(episodeResponse.text)
                
                // Extract server list
                val serverElements = episodeDoc.select("div.server-item")
                
                for (serverElement in serverElements) {
                    val serverName = serverElement.select("span").text()
                    
                    // Look for preferred servers
                    if (serverName.lowercase().contains("vidplay") || 
                        serverName.lowercase().contains("mycloud") ||
                        serverName.lowercase().contains("upcloud")) {
                        
                        val dataId = serverElement.attr("data-id")
                        if (dataId.isEmpty()) continue
                        
                        Log.d("FMoviesProvider", "Trying server: $serverName (ID: $dataId)")
                        
                        // Get the ajax URL
                        val ajaxUrl = "$DOMAIN/ajax/server/$dataId"
                        
                        val ajaxResponse = app.get(ajaxUrl, headers = headers)
                        val ajaxData = ajaxResponse.parsedSafe<ServerData>()
                        
                        if (ajaxData?.link == null) {
                            Log.w("FMoviesProvider", "No link in ajax response")
                            continue
                        }
                        
                        val serverUrl = ajaxData.link
                        Log.d("FMoviesProvider", "Server URL: $serverUrl")
                        
                        // If it's a direct video URL
                        if (serverUrl.contains(".m3u8") || serverUrl.contains(".mp4")) {
                            callback.invoke(
                                ExtractorLink(
                                    source = PROVIDER_NAME,
                                    name = "$PROVIDER_NAME - $serverName",
                                    url = serverUrl,
                                    referer = DOMAIN,
                                    quality = Qualities.Unknown.value,
                                    type = if (serverUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                    headers = headers
                                )
                            )
                            
                            Log.d("FMoviesProvider", "Successfully extracted video link: $serverUrl")
                            return true
                        }
                        
                        // If it's an embed URL, try to extract from it
                        if (serverUrl.startsWith("http")) {
                            try {
                                val embedResponse = app.get(serverUrl, headers = headers)
                                val embedText = embedResponse.text
                                
                                // Look for m3u8 URLs in the embed
                                val m3u8Regex = """(https?://[^"'\s]+\.m3u8[^"'\s]*)""".toRegex()
                                val m3u8Match = m3u8Regex.find(embedText)
                                
                                if (m3u8Match != null) {
                                    val m3u8Url = m3u8Match.groupValues[1]
                                    
                                    callback.invoke(
                                        ExtractorLink(
                                            source = PROVIDER_NAME,
                                            name = "$PROVIDER_NAME - $serverName",
                                            url = m3u8Url,
                                            referer = serverUrl,
                                            quality = Qualities.Unknown.value,
                                            type = ExtractorLinkType.M3U8,
                                            headers = headers
                                        )
                                    )
                                    
                                    Log.d("FMoviesProvider", "Successfully extracted embedded video link: $m3u8Url")
                                    return true
                                }
                            } catch (e: Exception) {
                                Log.w("FMoviesProvider", "Error extracting from embed: ${e.message}")
                            }
                        }
                    }
                }
                
                Log.w("FMoviesProvider", "No suitable servers found")
                false
                
            } catch (e: Exception) {
                Log.e("FMoviesProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
    }
    
    data class ServerData(
        @JsonProperty("link") val link: String?
    )
}
