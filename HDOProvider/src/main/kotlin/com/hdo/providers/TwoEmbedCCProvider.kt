package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.util.regex.Pattern

class TwoEmbedCCProvider {
    
    companion object {
        private const val PROVIDER_NAME = "PTOWEMBEDCC"
        private const val DOMAIN = "https://www.2embed.cc"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("TwoEmbedCCProvider", "Starting extraction for: ${movieInfo.title}")
                
                // Build the search URL
                val urlSearch = if (movieInfo.type == "tv") {
                    "$DOMAIN/embedtv/${movieInfo.tmdbId}&s=${movieInfo.season}&e=${movieInfo.episode}"
                } else {
                    "$DOMAIN/embed/${movieInfo.tmdbId}"
                }
                
                Log.d("TwoEmbedCCProvider", "Search URL: $urlSearch")
                
                // Step 1: Get the main embed page
                val iframeResponse = app.get(urlSearch)
                val iframeDoc = Jsoup.parse(iframeResponse.text)
                
                var linkDetail = ""
                
                // Find the player4u link in dropdown
                iframeDoc.select("#myDropdown a").forEach { element ->
                    val onclick = element.attr("onclick")
                    Log.d("TwoEmbedCCProvider", "Onclick: $onclick")
                    
                    if (onclick.contains("player4u")) {
                        val goMatch = """go\('([^']+)""".toRegex().find(onclick)
                        if (goMatch != null) {
                            linkDetail = goMatch.groupValues[1].replace("\\s".toRegex(), "%20")
                        }
                    }
                }
                
                Log.d("TwoEmbedCCProvider", "Link detail: $linkDetail")
                
                if (linkDetail.isEmpty()) {
                    Log.w("TwoEmbedCCProvider", "No link detail found")
                    return false
                }
                
                // Step 2: Get the detail page
                val detailResponse = app.get(linkDetail)
                val detailDoc = Jsoup.parse(detailResponse.text)
                
                val linkIds = mutableListOf<String>()
                
                // Extract IDs from playbtnx elements
                detailDoc.select(".playbtnx").forEach { element ->
                    val onclick = element.attr("onclick")
                    Log.d("TwoEmbedCCProvider", "Detail onclick: $onclick")
                    
                    val idMatch = """/swp/\?id=([^&]+)""".toRegex().find(onclick)
                    if (idMatch != null) {
                        linkIds.add(idMatch.groupValues[1])
                    }
                }
                
                Log.d("TwoEmbedCCProvider", "Link IDs: $linkIds")
                
                if (linkIds.isEmpty()) {
                    Log.w("TwoEmbedCCProvider", "No link IDs found")
                    return false
                }
                
                // Step 3: Process each ID
                for (id in linkIds) {
                    try {
                        val embedUrl = "https://uqloads.xyz/e/$id"
                        val headerEmbed = mapOf("Referer" to "https://streamsrcs.2embed.cc/")
                        
                        val embedResponse = app.get(embedUrl, headers = headerEmbed)
                        val embedDoc = Jsoup.parse(embedResponse.text)
                        
                        var scriptEval = ""
                        embedDoc.select("script").forEach { script ->
                            val scriptText = script.html()
                            if (scriptText.contains("eval")) {
                                scriptEval = scriptText
                            }
                        }
                        
                        if (scriptEval.isEmpty()) {
                            Log.w("TwoEmbedCCProvider", "No eval script found for ID: $id")
                            continue
                        }
                        
                        // Extract eval function
                        val evalMatch = """eval\(function\(p,a,c,k,e,.*\)\)""".toRegex().find(scriptEval)
                        val evalData = evalMatch?.value ?: ""
                        
                        if (evalData.isEmpty()) {
                            Log.w("TwoEmbedCCProvider", "No eval data found for ID: $id")
                            continue
                        }
                        
                        // Unpack the JavaScript (simplified version)
                        val unpacked = unpackJavaScript(evalData)
                        
                        // Extract direct file URL
                        val fileMatch = """hls2"\s*:\s*"([^"]+)""".toRegex().find(unpacked)
                        val fileDirect = fileMatch?.groupValues?.get(1) ?: ""
                        
                        Log.d("TwoEmbedCCProvider", "File direct: $fileDirect")
                        
                        if (fileDirect.isEmpty() || !fileDirect.startsWith("https://")) {
                            Log.w("TwoEmbedCCProvider", "Invalid file URL for ID: $id")
                            continue
                        }
                        
                        // Create the extractor link
                        callback.invoke(
                            ExtractorLink(
                                source = PROVIDER_NAME,
                                name = PROVIDER_NAME,
                                url = fileDirect,
                                referer = embedUrl,
                                quality = Qualities.Unknown.value,
                                type = ExtractorLinkType.M3U8
                            )
                        )
                        
                        Log.d("TwoEmbedCCProvider", "Successfully extracted video link: $fileDirect")
                        return true
                        
                    } catch (e: Exception) {
                        Log.e("TwoEmbedCCProvider", "Error processing ID $id: ${e.message}")
                        continue
                    }
                }
                
                false
                
            } catch (e: Exception) {
                Log.e("TwoEmbedCCProvider", "Error extracting video: ${e.message}", e)
                false
            }
        }
        
        private fun unpackJavaScript(packedJs: String): String {
            // Simplified JavaScript unpacking - you might need to implement a proper unpacker
            // For now, return the original string and let regex try to find patterns
            return packedJs
        }
    }
}
