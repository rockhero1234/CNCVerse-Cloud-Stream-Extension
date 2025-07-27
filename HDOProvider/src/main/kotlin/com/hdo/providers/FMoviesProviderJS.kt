package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup

class FMoviesProviderJS {
    
    companion object {
        private const val PROVIDER_NAME = "FMOVIES"
        private const val DOMAIN = "https://fmovies.si"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("FMoviesProviderJS", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$DOMAIN/",
                    "Origin" to DOMAIN
                )
                
                // Build search URL - exact same as JavaScript
                val urlSearch = "$DOMAIN/search?keyword=${movieInfo.title?.replace(" ", "%20") ?: ""}"
                
                Log.d("FMoviesProviderJS", "URL Search: $urlSearch")
                
                // Step 1: Search for the movie/show - exact same logic as JavaScript
                val parseSearch = app.get(urlSearch, headers = headers, timeout = 30)
                val searchDoc = Jsoup.parse(parseSearch.text)
                
                var linkDetail = ""
                val tvLinkDetails = mutableListOf<String>()
                
                Log.d("FMoviesProviderJS", "Search results count: ${searchDoc.select(".flw-item").size}")
                
                // Parse search results - exact same logic as JavaScript
                searchDoc.select(".flw-item").forEach { item ->
                    val title = item.select(".film-poster-ahref").attr("title")
                    val href = item.select(".film-poster-ahref").attr("href")
                    val year = item.select(".fdi-item").first()?.text() ?: ""
                    val type = item.select(".fdi-type").text()
                    
                    Log.d("FMoviesProviderJS", "Search item - Title: $title, Year: $year, Type: $type")
                    
                    if (title.isNotEmpty() && href.isNotEmpty() && linkDetail.isEmpty() && type.isNotEmpty()) {
                        // String matching title - simplified version
                        val titleMatches = title.lowercase().contains(movieInfo.title?.lowercase() ?: "")
                        
                        if (titleMatches) {
                            if (movieInfo.type == "tv" && type.lowercase() == "tv") {
                                val tvLinkDetail = if (href.startsWith("/")) "$DOMAIN$href" else href
                                tvLinkDetails.add(tvLinkDetail)
                            }
                            if (movieInfo.type == "movie" && type.lowercase() == "movie" && 
                                movieInfo.year.toString() == year) {
                                linkDetail = if (href.startsWith("/")) "$DOMAIN$href" else href
                            }
                        }
                    }
                }
                
                Log.d("FMoviesProviderJS", "TV Link Details: $tvLinkDetails")
                
                // For TV shows, find the correct year match - exact same logic as JavaScript
                var flagTv = false
                if (movieInfo.type == "tv" && tvLinkDetails.isNotEmpty()) {
                    for (linkTvItem in tvLinkDetails) {
                        Log.d("FMoviesProviderJS", "Checking TV link: $linkTvItem")
                        
                        val parseTvLinkDetail = app.get(linkTvItem, headers = headers)
                        val tvDetailDoc = Jsoup.parse(parseTvLinkDetail.text)
                        
                        var yearTv = 0
                        tvDetailDoc.select(".m_i-d-content .elements .row-line").forEach { itemTv ->
                            val text = itemTv.text()
                            Log.d("FMoviesProviderJS", "TV detail text: $text")
                            
                            if (text.lowercase().contains("released")) {
                                val yearMatch = """Released *\: *([0-9]+)""".toRegex(RegexOption.IGNORE_CASE).find(text)
                                yearTv = yearMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                Log.d("FMoviesProviderJS", "Year TV: $yearTv")
                                
                                if (yearTv == movieInfo.year && !flagTv) {
                                    flagTv = true
                                    linkDetail = linkTvItem
                                }
                            }
                        }
                    }
                }
                
                Log.d("FMoviesProviderJS", "Link Detail: $linkDetail")
                
                if (linkDetail.isEmpty()) {
                    Log.w("FMoviesProviderJS", "No link detail found")
                    return false
                }
                
                // Extract film ID - exact same logic as JavaScript
                val filmIdMatch = """-([0-9]+)$""".toRegex(RegexOption.IGNORE_CASE).find(linkDetail)
                val filmId = filmIdMatch?.groupValues?.get(1) ?: ""
                
                Log.d("FMoviesProviderJS", "Film ID: $filmId")
                
                if (filmId.isEmpty()) {
                    Log.w("FMoviesProviderJS", "No film ID found")
                    return false
                }
                
                val serverIds = mutableListOf<String>()
                
                if (movieInfo.type == "movie") {
                    // For movies - exact same logic as JavaScript
                    val apiUrlEmbed = "$DOMAIN/ajax/episode/list/$filmId"
                    val parseEmbedServer = app.get(apiUrlEmbed, headers = headers)
                    val embedDoc = Jsoup.parse(parseEmbedServer.text)
                    
                    Log.d("FMoviesProviderJS", "Movie embed URL: $apiUrlEmbed")
                    Log.d("FMoviesProviderJS", "Movie server count: ${embedDoc.select(".nav-link").size}")
                    
                    embedDoc.select(".nav-link").forEach { item ->
                        val serverId = item.attr("data-linkid")
                        if (serverId.isNotEmpty()) {
                            serverIds.add(serverId)
                        }
                    }
                } else if (movieInfo.type == "tv") {
                    // For TV shows - exact same logic as JavaScript
                    val apiUrlGetSeason = "$DOMAIN/ajax/season/list/$filmId"
                    val parseGetSeason = app.get(apiUrlGetSeason, headers = headers)
                    val seasonDoc = Jsoup.parse(parseGetSeason.text)
                    
                    var seasonId = ""
                    
                    Log.d("FMoviesProviderJS", "Season URL: $apiUrlGetSeason")
                    Log.d("FMoviesProviderJS", "Season count: ${seasonDoc.select(".ss-item").size}")
                    
                    // Find the correct season
                    seasonDoc.select(".ss-item").forEach { item ->
                        val season = item.text()
                        val seasonDataId = item.attr("data-id")
                        
                        if (season.isNotEmpty() && seasonDataId.isNotEmpty()) {
                            val seasonMatch = """([0-9.*]+)""".toRegex(RegexOption.IGNORE_CASE).find(season)
                            val seasonNumber = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                            
                            if (seasonNumber == movieInfo.season) {
                                seasonId = seasonDataId
                            }
                        }
                    }
                    
                    Log.d("FMoviesProviderJS", "Season ID: $seasonId")
                    
                    if (seasonId.isEmpty()) {
                        Log.w("FMoviesProviderJS", "No season ID found")
                        return false
                    }
                    
                    // Get episodes for the season
                    val apiUrlGetEpisode = "$DOMAIN/ajax/season/episodes/$seasonId"
                    val parseGetEpisode = app.get(apiUrlGetEpisode, headers = headers)
                    val episodeDoc = Jsoup.parse(parseGetEpisode.text)
                    
                    var episodeId = ""
                    
                    Log.d("FMoviesProviderJS", "Episode URL: $apiUrlGetEpisode")
                    Log.d("FMoviesProviderJS", "Episode count: ${episodeDoc.select(".eps-item").size}")
                    
                    // Find the correct episode
                    episodeDoc.select(".eps-item").forEach { item ->
                        val episode = item.select("strong").text()
                        val episodeDataId = item.attr("data-id")
                        
                        Log.d("FMoviesProviderJS", "Episode info - Episode: $episode, ID: $episodeDataId")
                        
                        if (episode.isNotEmpty() && episodeDataId.isNotEmpty()) {
                            val episodeMatch = """([0-9.]+)""".toRegex(RegexOption.IGNORE_CASE).find(episode)
                            val episodeNumber = episodeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                            
                            if (episodeNumber == movieInfo.episode) {
                                episodeId = episodeDataId
                            }
                        }
                    }
                    
                    Log.d("FMoviesProviderJS", "Episode ID: $episodeId")
                    
                    if (episodeId.isEmpty()) {
                        Log.w("FMoviesProviderJS", "No episode ID found")
                        return false
                    }
                    
                    // Get servers for the episode
                    val urlGetEmbedTv = "$DOMAIN/ajax/episode/servers/$episodeId"
                    val parseEmbedTv = app.get(urlGetEmbedTv, headers = headers)
                    val embedTvDoc = Jsoup.parse(parseEmbedTv.text)
                    
                    Log.d("FMoviesProviderJS", "TV embed URL: $urlGetEmbedTv")
                    Log.d("FMoviesProviderJS", "TV server count: ${embedTvDoc.select(".nav-link").size}")
                    
                    embedTvDoc.select(".nav-link").forEach { item ->
                        val serverId = item.attr("data-id")
                        if (serverId.isNotEmpty()) {
                            serverIds.add(serverId)
                        }
                    }
                }
                
                Log.d("FMoviesProviderJS", "Server IDs: $serverIds")
                
                // Try each server - exact same logic as JavaScript
                for (serverId in serverIds) {
                    try {
                        val apiUrlGetLink = "$DOMAIN/ajax/episode/sources/$serverId"
                        val getLinkResponse = app.get(apiUrlGetLink, headers = headers)
                        val linkData = getLinkResponse.parsedSafe<LinkData>()
                        
                        Log.d("FMoviesProviderJS", "Server $serverId - Link data: $linkData")
                        
                        if (linkData?.link != null) {
                            val linkEmbed = linkData.link
                            
                            Log.d("FMoviesProviderJS", "Link embed: $linkEmbed")
                            
                            // Try to extract from the embed using libs.embed_redirect logic
                            if (linkEmbed.startsWith("http")) {
                                val embedResponse = app.get(linkEmbed, headers = headers)
                                val embedText = embedResponse.text
                                
                                // Look for video URLs in embed
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
                                                    name = PROVIDER_NAME,
                                                    url = videoUrl,
                                                    referer = linkEmbed,
                                                    quality = Qualities.Unknown.value,
                                                    type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                                    headers = headers
                                                )
                                            )
                                            
                                            Log.d("FMoviesProviderJS", "Successfully extracted video link: $videoUrl")
                                            return true
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("FMoviesProviderJS", "Error with server $serverId: ${e.message}")
                    }
                }
                
                Log.w("FMoviesProviderJS", "No video links found")
                false
                
            } catch (e: Exception) {
                Log.e("FMoviesProviderJS", "Error extracting video: ${e.message}", e)
                false
            }
        }
    }
    
    data class LinkData(
        @JsonProperty("link") val link: String?
    )
}
