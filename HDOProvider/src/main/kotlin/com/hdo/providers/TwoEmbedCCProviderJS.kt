package com.hdo.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class TwoEmbedCCProviderJS {
    
    companion object {
        private const val PROVIDER_NAME = "2EMBEDCC"
        private const val DOMAIN = "https://www.2embed.cc"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("TwoEmbedCCProviderJS", "Starting extraction for: ${movieInfo.title}")
                
                val headers = mapOf(
                    "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "$DOMAIN/",
                    "Origin" to DOMAIN
                )
                
                // Build search URL - exact same as JavaScript
                val urlSearch = if (movieInfo.type == "tv") {
                    "$DOMAIN/embedtv/${movieInfo.tmdbId}&s=${movieInfo.season}&e=${movieInfo.episode}"
                } else {
                    "$DOMAIN/embed/${movieInfo.tmdbId}"
                }
                
                Log.d("TwoEmbedCCProviderJS", "URL Search: $urlSearch")
                
                // Step 1: Get the iframe page - exact same logic as JavaScript
                val parseIframe = app.get(urlSearch, headers = headers, timeout = 30)
                val iframeDoc = Jsoup.parse(parseIframe.text)
                
                var linkDetail = ""
                
                // Extract link detail - exact same logic as JavaScript
                iframeDoc.select("#myDropdown a").forEach { item ->
                    val onclickAttr = item.attr("onclick")
                    Log.d("TwoEmbedCCProviderJS", "onclick attr: $onclickAttr")
                    
                    if (onclickAttr.isNotEmpty() && onclickAttr.contains("player4u")) {
                        val pSrcMatch = """go\('([^']+)""".toRegex(RegexOption.IGNORE_CASE).find(onclickAttr)
                        if (pSrcMatch != null) {
                            linkDetail = pSrcMatch.groupValues[1].replace("""\s""".toRegex(), "%20")
                        }
                    }
                }
                
                Log.d("TwoEmbedCCProviderJS", "Link Detail: $linkDetail")
                
                if (linkDetail.isEmpty()) {
                    Log.w("TwoEmbedCCProviderJS", "No link detail found")
                    return false
                }
                
                // Step 2: Get the detail page - exact same logic as JavaScript
                val parseDetail = app.get(linkDetail, headers = headers)
                val detailDoc = Jsoup.parse(parseDetail.text)
                
                val linkIds = mutableListOf<String>()
                
                // Extract link IDs - exact same logic as JavaScript
                detailDoc.select(".playbtnx").forEach { item ->
                    val onclickAttr = item.attr("onclick")
                    Log.d("TwoEmbedCCProviderJS", "detail onclick attr: $onclickAttr")
                    
                    if (onclickAttr.isNotEmpty()) {
                        val pIdMatch = """/swp/\?id=([^&]+)""".toRegex(RegexOption.IGNORE_CASE).find(onclickAttr)
                        if (pIdMatch != null) {
                            linkIds.add(pIdMatch.groupValues[1])
                        }
                    }
                }
                
                Log.d("TwoEmbedCCProviderJS", "Link IDs: $linkIds")
                
                if (linkIds.isEmpty()) {
                    Log.w("TwoEmbedCCProviderJS", "No link IDs found")
                    return false
                }
                
                // Step 3: Process each link ID - exact same logic as JavaScript
                for (id in linkIds) {
                    try {
                        val embedUrl = "https://uqloads.xyz/e/$id"
                        val headerEmbed = mapOf(
                            "Referer" to "https://streamsrcs.2embed.cc/",
                            "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        )
                        
                        Log.d("TwoEmbedCCProviderJS", "Embed URL: $embedUrl")
                        
                        val parseEmbed = app.get(embedUrl, headers = headerEmbed)
                        val embedDoc = Jsoup.parse(parseEmbed.text)
                        
                        var scriptEval = ""
                        
                        // Find the eval script - exact same logic as JavaScript
                        embedDoc.select("script").forEach { script ->
                            val scriptText = script.html()
                            if (scriptText.contains("eval")) {
                                scriptEval = scriptText
                            }
                        }
                        
                        if (scriptEval.isEmpty()) {
                            Log.w("TwoEmbedCCProviderJS", "No eval script found for ID: $id")
                            continue
                        }
                        
                        // Extract eval data - exact same logic as JavaScript
                        val evalDataMatch = """eval\(function\(p,a,c,k,e,.*\)\)""".toRegex(RegexOption.IGNORE_CASE).find(scriptEval)
                        val evalData = evalDataMatch?.value ?: ""
                        
                        if (evalData.isEmpty()) {
                            Log.w("TwoEmbedCCProviderJS", "No eval data found for ID: $id")
                            continue
                        }
                        
                        Log.d("TwoEmbedCCProviderJS", "Eval data: ${evalData.take(100)}...")
                        
                        // Unpack the eval - using CloudStream3's unpacker
                        try {
                            val unpacked = getAndUnpack(evalData)
                            Log.d("TwoEmbedCCProviderJS", "Unpacked: ${unpacked.take(200)}...")
                            
                            // Extract file direct - exact same logic as JavaScript
                            val fileDirectMatch = """hls2\s*"\s*:\s*"([^"]+)""".toRegex(RegexOption.IGNORE_CASE).find(unpacked)
                            val fileDirect = fileDirectMatch?.groupValues?.get(1) ?: ""
                            
                            Log.d("TwoEmbedCCProviderJS", "File Direct: $fileDirect")
                            
                            if (fileDirect.isNotEmpty() && fileDirect.startsWith("https://")) {
                                callback.invoke(
                                    ExtractorLink(
                                        source = PROVIDER_NAME,
                                        name = PROVIDER_NAME,
                                        url = fileDirect,
                                        referer = embedUrl,
                                        quality = Qualities.P1080.value,
                                        type = ExtractorLinkType.M3U8,
                                        headers = headerEmbed
                                    )
                                )
                                
                                Log.d("TwoEmbedCCProviderJS", "Successfully extracted video link: $fileDirect")
                                return true
                            }
                        } catch (e: Exception) {
                            Log.w("TwoEmbedCCProviderJS", "Error unpacking for ID $id: ${e.message}")
                        }
                        
                    } catch (e: Exception) {
                        Log.w("TwoEmbedCCProviderJS", "Error processing ID $id: ${e.message}")
                    }
                }
                
                Log.w("TwoEmbedCCProviderJS", "No video links found")
                false
                
            } catch (e: Exception) {
                Log.e("TwoEmbedCCProviderJS", "Error extracting video: ${e.message}", e)
                false
            }
        }
        
        // Helper function to unpack JavaScript eval
        private fun getAndUnpack(string: String): String {
            return try {
                // Simple JavaScript unpacker - for more complex cases, implement full unpacker
                val packedRegex = """eval\(function\(p,a,c,k,e,d\).*?\}\('([^']+)',(\d+),(\d+),'([^']+)'\.split\('\|'\)""".toRegex()
                val match = packedRegex.find(string)
                
                if (match != null) {
                    val packed = match.groupValues[1]
                    val base = match.groupValues[2].toInt()
                    val count = match.groupValues[3].toInt()
                    val keywords = match.groupValues[4].split("|")
                    
                    unpackJS(packed, base, count, keywords)
                } else {
                    string
                }
            } catch (e: Exception) {
                Log.w("TwoEmbedCCProviderJS", "Error in unpacker: ${e.message}")
                string
            }
        }
        
        private fun unpackJS(packed: String, base: Int, count: Int, keywords: List<String>): String {
            return try {
                val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                
                fun encode(num: Int, base: Int): String {
                    return if (num == 0) "0" else {
                        var result = ""
                        var n = num
                        while (n > 0) {
                            result = chars[n % base] + result
                            n /= base
                        }
                        result
                    }
                }
                
                var unpacked = packed
                
                for (i in count - 1 downTo 0) {
                    val encoded = encode(i, base)
                    val replacement = if (i < keywords.size && keywords[i].isNotEmpty()) {
                        keywords[i]
                    } else {
                        encoded
                    }
                    
                    val pattern = "\\b$encoded\\b"
                    unpacked = unpacked.replace(Regex(pattern), replacement)
                }
                
                unpacked
            } catch (e: Exception) {
                Log.w("TwoEmbedCCProviderJS", "JS unpacking failed: ${e.message}")
                packed
            }
        }
    }
}
