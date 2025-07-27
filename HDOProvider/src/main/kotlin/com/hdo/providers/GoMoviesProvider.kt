package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup
import android.util.Base64

class GoMoviesProvider {
    
    companion object {
        private const val PROVIDER_NAME = "GoMovies"
        private const val DOMAIN = "https://gomovies.sx"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("GoMoviesProvider", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$DOMAIN/",
                    "Origin" to DOMAIN
                )
                
                // Build search query
                val searchQuery = "${movieInfo.title}${if (movieInfo.year != null) " ${movieInfo.year}" else ""}"
                    .replace(" ", "+")
                
                val searchUrl = "$DOMAIN/search/$searchQuery"
                
                Log.d("GoMoviesProvider", "Search URL: $searchUrl")
                
                // Step 1: Search for the movie/show
                val searchResponse = app.get(searchUrl, headers = headers)
                val searchDoc = Jsoup.parse(searchResponse.text)
                
                // Find the first matching result
                val movieLink = searchDoc.select("div.film_list-wrap div.flw-item a").firstOrNull()?.attr("href")
                if (movieLink.isNullOrEmpty()) {
                    Log.w("GoMoviesProvider", "No movie link found in search results")
                    return false
                }
                
                val fullMovieUrl = "$DOMAIN$movieLink"
                Log.d("GoMoviesProvider", "Movie URL: $fullMovieUrl")
                
                // Step 2: Get the movie/show details page
                val movieResponse = app.get(fullMovieUrl, headers = headers)
                val movieDoc = Jsoup.parse(movieResponse.text)
                
                var watchUrl = fullMovieUrl
                
                // If it's a TV show, need to find the specific episode
                if (movieInfo.type == "tv") {
                    // Look for season/episode selector
                    val seasonSelect = movieDoc.select("select#select-season option")
                    var seasonId: String? = null
                    
                    for (option in seasonSelect) {
                        val seasonNum = option.text().replace("Season ", "")
                        if (seasonNum == movieInfo.season.toString()) {
                            seasonId = option.attr("value")
                            break
                        }
                    }
                    
                    if (seasonId == null) {
                        Log.w("GoMoviesProvider", "Season not found")
                        return false
                    }
                    
                    // Get episodes for this season
                    val episodesUrl = "$DOMAIN/ajax/v2/season/episodes/$seasonId"
                    val episodesResponse = app.get(episodesUrl, headers = headers)
                    val episodesDoc = Jsoup.parse(episodesResponse.text)
                    
                    val episodeElements = episodesDoc.select("div.eps-item")
                    var episodeId: String? = null
                    
                    for (episodeElement in episodeElements) {
                        val episodeNum = episodeElement.select("div.episode-number").text()
                        if (episodeNum == movieInfo.episode.toString()) {
                            episodeId = episodeElement.attr("data-id")
                            break
                        }
                    }
                    
                    if (episodeId == null) {
                        Log.w("GoMoviesProvider", "Episode not found")
                        return false
                    }
                    
                    watchUrl = "$DOMAIN/ajax/v2/episode/servers/$episodeId"
                } else {
                    // For movies, get the movie ID and build servers URL
                    val movieId = movieDoc.select("div.watch_block").attr("data-id")
                    if (movieId.isEmpty()) {
                        Log.w("GoMoviesProvider", "Movie ID not found")
                        return false
                    }
                    watchUrl = "$DOMAIN/ajax/v2/movie/servers/$movieId"
                }
                
                Log.d("GoMoviesProvider", "Watch URL: $watchUrl")
                
                // Step 3: Get the servers list
                val serversResponse = app.get(watchUrl, headers = headers)
                val serversDoc = Jsoup.parse(serversResponse.text)
                
                // Find server elements
                val serverElements = serversDoc.select("div.server-item")
                
                for (serverElement in serverElements) {
                    val serverName = serverElement.select("span").text()
                    
                    // Look for preferred servers
                    if (serverName.lowercase().contains("vidcloud") || 
                        serverName.lowercase().contains("upcloud") ||
                        serverName.lowercase().contains("streamtape")) {
                        
                        val serverId = serverElement.attr("data-id")
                        if (serverId.isEmpty()) continue
                        
                        Log.d("GoMoviesProvider", "Trying server: $serverName (ID: $serverId)")
                        
                        // Get the server link
                        val serverUrl = "$DOMAIN/ajax/v2/episode/sources/$serverId"
                        
                        val serverResponse = app.get(serverUrl, headers = headers)
                        val serverData = serverResponse.parsedSafe<ServerLinkData>()
                        
                        if (serverData?.link == null) {
                            Log.w("GoMoviesProvider", "No link in server response")
                            continue
                        }
                        
                        val embedUrl = serverData.link
                        Log.d("GoMoviesProvider", "Embed URL: $embedUrl")
                        
                        // Try to extract from the embed
                        try {
                            val embedResponse = app.get(embedUrl, headers = headers)
                            val embedText = embedResponse.text
                            
                            // Look for encrypted data
                            val encryptedRegex = """window\.rabbitStream\s*=\s*"([^"]+)"""".toRegex()
                            val encryptedMatch = encryptedRegex.find(embedText)
                            
                            if (encryptedMatch != null) {
                                val encryptedData = encryptedMatch.groupValues[1]
                                
                                // Simple XOR decryption (key is usually the domain or a static key)
                                val key = "8z5Ag5wgagfsOuhz" // Common key for GoMovies
                                val decrypted = xorDecrypt(encryptedData, key)
                                
                                Log.d("GoMoviesProvider", "Decrypted: $decrypted")
                                
                                // Look for m3u8 in decrypted content
                                val m3u8Regex = """(https?://[^"'\s]+\.m3u8[^"'\s]*)""".toRegex()
                                val m3u8Match = m3u8Regex.find(decrypted)
                                
                                if (m3u8Match != null) {
                                    val m3u8Url = m3u8Match.groupValues[1]
                                    
                                    callback.invoke(
                                        ExtractorLink(
                                            source = PROVIDER_NAME,
                                            name = "$PROVIDER_NAME - $serverName",
                                            url = m3u8Url,
                                            referer = embedUrl,
                                            quality = Qualities.Unknown.value,
                                            type = ExtractorLinkType.M3U8,
                                            headers = headers
                                        )
                                    )
                                    
                                    Log.d("GoMoviesProvider", "Successfully extracted video link: $m3u8Url")
                                    return true
                                }
                            } else {
                                // Look for direct m3u8 URLs
                                val m3u8Regex = """(https?://[^"'\s]+\.m3u8[^"'\s]*)""".toRegex()
                                val m3u8Match = m3u8Regex.find(embedText)
                                
                                if (m3u8Match != null) {
                                    val m3u8Url = m3u8Match.groupValues[1]
                                    
                                    callback.invoke(
                                        ExtractorLink(
                                            source = PROVIDER_NAME,
                                            name = "$PROVIDER_NAME - $serverName",
                                            url = m3u8Url,
                                            referer = embedUrl,
                                            quality = Qualities.Unknown.value,
                                            type = ExtractorLinkType.M3U8,
                                            headers = headers
                                        )
                                    )
                                    
                                    Log.d("GoMoviesProvider", "Successfully extracted direct video link: $m3u8Url")
                                    return true
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("GoMoviesProvider", "Error extracting from embed: ${e.message}")
                        }
                    }
                }
                
                Log.w("GoMoviesProvider", "No suitable servers found")
                false
                
            } catch (e: Exception) {
                Log.e("GoMoviesProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
        
        private fun xorDecrypt(encrypted: String, key: String): String {
            return try {
                val decoded = Base64.decode(encrypted, Base64.DEFAULT)
                val result = StringBuilder()
                
                for (i in decoded.indices) {
                    val char = (decoded[i].toInt() xor key[i % key.length].code).toChar()
                    result.append(char)
                }
                
                result.toString()
            } catch (e: Exception) {
                Log.w("GoMoviesProvider", "XOR decryption failed: ${e.message}")
                encrypted
            }
        }
    }
    
    data class ServerLinkData(
        @JsonProperty("link") val link: String?
    )
}
