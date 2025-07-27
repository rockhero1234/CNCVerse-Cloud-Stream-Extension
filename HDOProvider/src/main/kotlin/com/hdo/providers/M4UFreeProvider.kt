package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup

class M4UFreeProvider {
    
    companion object {
        private const val PROVIDER_NAME = "LM4UFREE"
        private const val DOMAIN = "https://m4ufree.tv"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("M4UFreeProvider", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$DOMAIN/",
                    "Origin" to DOMAIN
                )
                
                // Step 1: Search for the movie/show
                val searchQuery = movieInfo.title?.replace(" ", "+") ?: ""
                val searchUrl = "$DOMAIN/search/$searchQuery.html"
                
                Log.d("M4UFreeProvider", "Search URL: $searchUrl")
                
                val searchResponse = app.get(searchUrl, headers = headers)
                val searchDoc = Jsoup.parse(searchResponse.text)
                
                // Find matching result
                var movieLink = ""
                searchDoc.select(".item .name a").forEach { item ->
                    val title = item.text()
                    val href = item.attr("href")
                    
                    if (title.isNotEmpty() && href.isNotEmpty() && movieLink.isEmpty()) {
                        val titleMatches = title.lowercase().contains(movieInfo.title?.lowercase() ?: "")
                        
                        if (titleMatches) {
                            movieLink = if (href.startsWith("/")) "$DOMAIN$href" else href
                        }
                    }
                }
                
                Log.d("M4UFreeProvider", "Movie link: $movieLink")
                
                if (movieLink.isEmpty()) {
                    Log.w("M4UFreeProvider", "No matching result found")
                    return false
                }
                
                // Step 2: Get the movie/show page
                val movieResponse = app.get(movieLink, headers = headers)
                val movieDoc = Jsoup.parse(movieResponse.text)
                
                var watchUrl = movieLink
                
                // If it's a TV show, find the specific episode
                if (movieInfo.type == "tv") {
                    val episodeElements = movieDoc.select(".episode_list a")
                    for (episodeElement in episodeElements) {
                        val episodeText = episodeElement.text()
                        val href = episodeElement.attr("href")
                        
                        // Look for season and episode patterns
                        val seasonEpisodePattern = """S(\d+).*?E(\d+)""".toRegex()
                        val match = seasonEpisodePattern.find(episodeText)
                        
                        if (match != null) {
                            val season = match.groupValues[1]
                            val episode = match.groupValues[2]
                            
                            if (season == movieInfo.season.toString() && episode == movieInfo.episode.toString()) {
                                watchUrl = if (href.startsWith("/")) "$DOMAIN$href" else href
                                break
                            }
                        }
                    }
                }
                
                Log.d("M4UFreeProvider", "Watch URL: $watchUrl")
                
                // Step 3: Get the watch page
                val watchResponse = app.get(watchUrl, headers = headers)
                val watchDoc = Jsoup.parse(watchResponse.text)
                
                // Look for video sources
                val sources = mutableListOf<String>()
                
                // Check for direct video elements
                watchDoc.select("video source").forEach { source ->
                    val src = source.attr("src")
                    if (src.isNotEmpty() && src.startsWith("http")) {
                        sources.add(src)
                    }
                }
                
                // Check for iframe sources
                watchDoc.select("iframe").forEach { iframe ->
                    val src = iframe.attr("src")
                    if (src.isNotEmpty()) {
                        val fullSrc = if (src.startsWith("//")) "https:$src" 
                                     else if (src.startsWith("/")) "$DOMAIN$src" 
                                     else src
                        
                        if (fullSrc.startsWith("http")) {
                            sources.add(fullSrc)
                        }
                    }
                }
                
                // Check for JavaScript embedded sources
                val pageText = watchResponse.text
                val jsSourcePatterns = listOf(
                    """(https?://[^"'\s]+\.m3u8[^"'\s]*)""".toRegex(),
                    """(https?://[^"'\s]+\.mp4[^"'\s]*)""".toRegex(),
                    """"file":\s*"([^"]+)"""".toRegex(),
                    """src:\s*["']([^"']+)["']""".toRegex()
                )
                
                for (pattern in jsSourcePatterns) {
                    val matches = pattern.findAll(pageText)
                    for (match in matches) {
                        val url = match.groupValues[1]
                        if (url.startsWith("http")) {
                            sources.add(url)
                        }
                    }
                }
                
                Log.d("M4UFreeProvider", "Found sources: $sources")
                
                // Process each source
                for (source in sources) {
                    try {
                        if (source.contains(".m3u8") || source.contains(".mp4")) {
                            // Direct video source
                            callback.invoke(
                                ExtractorLink(
                                    source = PROVIDER_NAME,
                                    name = PROVIDER_NAME,
                                    url = source,
                                    referer = watchUrl,
                                    quality = Qualities.Unknown.value,
                                    type = if (source.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                    headers = headers
                                )
                            )
                            
                            Log.d("M4UFreeProvider", "Successfully extracted direct video link: $source")
                            return true
                        } else {
                            // Try to extract from iframe/embed
                            val embedResponse = app.get(source, headers = headers)
                            val embedText = embedResponse.text
                            
                            for (pattern in jsSourcePatterns) {
                                val videoMatch = pattern.find(embedText)
                                if (videoMatch != null) {
                                    val videoUrl = videoMatch.groupValues[1]
                                    
                                    if (videoUrl.isNotEmpty() && videoUrl.startsWith("http")) {
                                        callback.invoke(
                                            ExtractorLink(
                                                source = PROVIDER_NAME,
                                                name = "$PROVIDER_NAME - Embed",
                                                url = videoUrl,
                                                referer = source,
                                                quality = Qualities.Unknown.value,
                                                type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                                headers = headers
                                            )
                                        )
                                        
                                        Log.d("M4UFreeProvider", "Successfully extracted embed video link: $videoUrl")
                                        return true
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("M4UFreeProvider", "Error processing source $source: ${e.message}")
                    }
                }
                
                Log.w("M4UFreeProvider", "No video links found")
                false
                
            } catch (e: Exception) {
                Log.e("M4UFreeProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
    }
}
