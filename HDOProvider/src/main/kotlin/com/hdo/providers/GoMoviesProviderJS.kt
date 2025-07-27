package com.hdo.providers

import android.util.Log
import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup
import java.util.*
import kotlin.collections.HashMap

class GoMoviesProviderJS {
    
    companion object {
        private const val PROVIDER_NAME = "GOMOVIES"
        private const val DOMAIN = "https://gomovies-online.cam"
        
        suspend fun getVideoLinks(
            movieInfo: VidsrcProvider.MovieInfo,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                Log.d("GoMoviesProviderJS", "Starting extraction for: ${movieInfo.title}")
                
                val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
                
                // Build search URL - exact same as JavaScript
                val urlSearch = "$DOMAIN/search/${movieInfo.title?.replace(" ", "%20") ?: ""}"
                
                Log.d("GoMoviesProviderJS", "URL Search: $urlSearch")
                
                // Step 1: Search for the movie/show - exact same logic as JavaScript
                val headers = mapOf("user-agent" to userAgent)
                val parseSearch = app.get(urlSearch, headers = headers, timeout = 30)
                val searchDoc = Jsoup.parse(parseSearch.text)
                
                var linkDetail = ""
                
                Log.d("GoMoviesProviderJS", "Search results count: ${searchDoc.select("div._smQamBQsETb").size}")
                
                // Parse search results - exact same logic as JavaScript
                searchDoc.select("div._smQamBQsETb").forEach { item ->
                    val title = item.attr("data-filmname")
                    val year = item.attr("data-year").toIntOrNull() ?: 0
                    val href = item.select("a").attr("href")
                    
                    Log.d("GoMoviesProviderJS", "Search item - Title: $title, Year: $year, Href: $href")
                    
                    if (title.isNotEmpty() && href.isNotEmpty() && linkDetail.isEmpty()) {
                        // Extract season from title - exact same logic as JavaScript
                        val seasonMatch = """season *([0-9]+)""".toRegex(RegexOption.IGNORE_CASE).find(title)
                        val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        val cleanTitle = title.replace("""\- *season .*""".toRegex(RegexOption.IGNORE_CASE), "").trim()
                        
                        Log.d("GoMoviesProviderJS", "Clean title: $cleanTitle, Season: $season")
                        
                        // String matching title - simplified version
                        val titleMatches = cleanTitle.lowercase().contains(movieInfo.title?.lowercase() ?: "")
                        
                        if (titleMatches) {
                            if (movieInfo.type == "movie" && season == 0 && movieInfo.year == year) {
                                linkDetail = "$DOMAIN$href"
                            }
                            if (movieInfo.type == "tv" && season > 0 && season == movieInfo.season) {
                                linkDetail = "$DOMAIN$href"
                            }
                        }
                    }
                }
                
                Log.d("GoMoviesProviderJS", "Link Detail: $linkDetail")
                
                if (linkDetail.isEmpty()) {
                    Log.w("GoMoviesProviderJS", "No link detail found")
                    return false
                }
                
                var linkTvDetail = ""
                
                // For TV shows, find the correct episode - exact same logic as JavaScript
                if (movieInfo.type == "tv") {
                    val parseTv = app.get(linkDetail, headers = headers)
                    val tvDoc = Jsoup.parse(parseTv.text)
                    
                    val episodeSelector = if ((movieInfo.episode ?: 0) >= 10) {
                        "div#_sBWcqbTBMaT a:contains(Episode ${movieInfo.episode})"
                    } else {
                        "div#_sBWcqbTBMaT a:contains(Episode 0${movieInfo.episode})"
                    }
                    
                    val hrefTv = tvDoc.select(episodeSelector).attr("href")
                    Log.d("GoMoviesProviderJS", "Episode href: $hrefTv")
                    
                    if (hrefTv.isNotEmpty()) {
                        linkTvDetail = "$DOMAIN$hrefTv"
                        linkDetail = linkTvDetail
                    }
                }
                
                Log.d("GoMoviesProviderJS", "Final link detail: $linkDetail")
                
                if (movieInfo.type == "tv" && linkTvDetail.isEmpty()) {
                    Log.w("GoMoviesProviderJS", "No TV link detail found")
                    return false
                }
                
                // Get the detail page to extract endpoint - exact same logic as JavaScript
                val htmlDetail = app.get(linkDetail, headers = headers).text
                
                val parseEndpointMatch = """pushState\( *\{\} *\, *\' *\' *\, *\'([^\']+)""".toRegex(RegexOption.IGNORE_CASE).find(htmlDetail)
                val parseEndpoint = parseEndpointMatch?.groupValues?.get(1) ?: ""
                
                Log.d("GoMoviesProviderJS", "Parse endpoint: $parseEndpoint")
                
                if (parseEndpoint.isEmpty()) {
                    Log.w("GoMoviesProviderJS", "No parse endpoint found")
                    return false
                }
                
                // Extract ID and episode ID - exact same logic as JavaScript
                val parseIdParts = parseEndpoint.split("/")
                val id = if (movieInfo.type == "tv") {
                    parseIdParts[parseIdParts.size - 3]
                } else {
                    parseIdParts[parseIdParts.size - 2]
                }
                
                val idEpisode = if (movieInfo.type == "tv") {
                    parseIdParts[parseIdParts.size - 2]
                } else {
                    "0"
                }
                
                Log.d("GoMoviesProviderJS", "ID: $id, Episode ID: $idEpisode")
                
                // Build cookie data - exact same logic as JavaScript (simplified)
                val cookieData = "_identitygomovies7=52fdc70b008c0b1d881dac0f01cca819edd512de01cc8bbc1224ed4aafb78b52a%3A2%3A%7Bi%3A0%3Bs%3A18%3A%22_identitygomovies7%22%3Bi%3A1%3Bs%3A52%3A%22%5B2050366%2C%22HnVRRAObTASOJEr45YyCM8wiHol0V1ko%22%2C2592000%5D%22%3B%7D"
                
                // Get servers - exact same logic as JavaScript
                val urlServer = "$DOMAIN/user/servers/$id?ep=$idEpisode"
                val serverHeaders = mapOf(
                    "Cookie" to cookieData,
                    "user-agent" to userAgent
                )
                
                Log.d("GoMoviesProviderJS", "Server URL: $urlServer")
                
                val htmlServer = app.get(urlServer, headers = serverHeaders).text
                val parseServer = Jsoup.parse(htmlServer)
                
                val servers = mutableListOf<String>()
                parseServer.select("ul li").forEach { item ->
                    val dataValue = item.attr("data-value")
                    if (dataValue.isNotEmpty()) {
                        servers.add(dataValue)
                    }
                }
                
                Log.d("GoMoviesProviderJS", "Servers: $servers")
                
                if (servers.isEmpty()) {
                    Log.w("GoMoviesProviderJS", "No servers found")
                    return false
                }
                
                // Extract decryption key - exact same logic as JavaScript
                val evalDataMatch = """eval\(function\(p,a,c,k,e,.*\)\)""".toRegex(RegexOption.IGNORE_CASE).find(htmlServer)
                val evalData = evalDataMatch?.value ?: ""
                
                if (evalData.isEmpty()) {
                    Log.w("GoMoviesProviderJS", "No eval data found")
                    return false
                }
                
                val unpacker = getAndUnpack(evalData)
                Log.d("GoMoviesProviderJS", "Unpacker: ${unpacker.take(200)}...")
                
                var dKey = ""
                
                // Extract dKey with multiple patterns - exact same logic as JavaScript
                val dKeyPatterns = listOf(
                    """\(response *\, *\"*([A-z0-9]+)""".toRegex(RegexOption.IGNORE_CASE),
                    """j *\< *\" *([A-z0-9]+)""".toRegex(RegexOption.IGNORE_CASE),
                    """string *\, *key *\=\"*([A-z0-9]+)""".toRegex(RegexOption.IGNORE_CASE)
                )
                
                for (pattern in dKeyPatterns) {
                    val dKeyMatch = pattern.find(unpacker)
                    if (dKeyMatch != null) {
                        dKey = dKeyMatch.groupValues[1]
                        break
                    }
                }
                
                Log.d("GoMoviesProviderJS", "Decryption key: $dKey")
                
                if (dKey.isEmpty()) {
                    Log.w("GoMoviesProviderJS", "No decryption key found")
                    return false
                }
                
                // Try each server - exact same logic as JavaScript
                val qualities = listOf(360)
                
                for (server in servers) {
                    try {
                        val embedUrl = "$DOMAIN$parseEndpoint?server=$server&_=${System.currentTimeMillis()}"
                        val embedHeaders = mapOf(
                            "Cookie" to cookieData,
                            "user-agent" to userAgent,
                            "x-requested-with" to "XMLHttpRequest"
                        )
                        
                        Log.d("GoMoviesProviderJS", "Embed URL: $embedUrl")
                        
                        val parseEmbed = app.get(embedUrl, headers = embedHeaders).text
                        
                        // Decode and decrypt - exact same logic as JavaScript
                        val atobIframe = try {
                            Base64.decode(parseEmbed, Base64.DEFAULT).toString(Charsets.UTF_8)
                        } catch (e: Exception) {
                            Log.w("GoMoviesProviderJS", "Error decoding base64: ${e.message}")
                            continue
                        }
                        
                        if (atobIframe.isEmpty()) continue
                        
                        val decrypt = decryptGomoviesJson(dKey, atobIframe)
                        Log.d("GoMoviesProviderJS", "Decrypt: $decrypt")
                        
                        if (decrypt.isEmpty()) continue
                        
                        // Parse JSON - exact same logic as JavaScript
                        val parseEncode = try {
                            val jsonText = "{\"a\": $decrypt}"
                            AppUtils.parseJson<GoMoviesResponse>(jsonText)
                        } catch (e: Exception) {
                            Log.w("GoMoviesProviderJS", "Error parsing JSON: ${e.message}")
                            continue
                        }
                        
                        val parseFirstEncode = parseEncode?.a?.firstOrNull()
                        if (parseFirstEncode?.src == null) continue
                        
                        Log.d("GoMoviesProviderJS", "First encode: $parseFirstEncode")
                        
                        // Generate quality URLs - exact same logic as JavaScript
                        val directQuality = mutableListOf<QualityItem>()
                        
                        for (qualityItem in qualities) {
                            val urlReplace = parseFirstEncode.src.replace("360", qualityItem.toString())
                            
                            if ((parseFirstEncode.max ?: 0) >= qualityItem) {
                                directQuality.add(QualityItem(urlReplace, qualityItem))
                            }
                        }
                        
                        // Sort by quality descending - exact same logic as JavaScript
                        directQuality.sortByDescending { it.quality }
                        
                        Log.d("GoMoviesProviderJS", "Direct quality: $directQuality")
                        
                        if (directQuality.isNotEmpty()) {
                            callback.invoke(
                                ExtractorLink(
                                    source = PROVIDER_NAME,
                                    name = PROVIDER_NAME,
                                    url = directQuality[0].file,
                                    referer = embedUrl,
                                    quality = Qualities.Unknown.value,
                                    type = ExtractorLinkType.M3U8,
                                    headers = embedHeaders
                                )
                            )
                            
                            Log.d("GoMoviesProviderJS", "Successfully extracted video link: ${directQuality[0].file}")
                            return true
                        }
                        
                    } catch (e: Exception) {
                        Log.w("GoMoviesProviderJS", "Error with server $server: ${e.message}")
                    }
                }
                
                Log.w("GoMoviesProviderJS", "No video links found")
                false
                
            } catch (e: Exception) {
                Log.e("GoMoviesProviderJS", "Error extracting video: ${e.message}", e)
                false
            }
        }
        
        // Decrypt function - exact same as JavaScript
        private fun decryptGomoviesJson(key: String, str: String): String {
            val b = StringBuilder()
            var i = 0
            
            while (i < str.length) {
                var j = 0
                while (j < key.length && i < str.length) {
                    val charCode = str[i].code xor key[j].code
                    b.append(charCode.toChar())
                    j++
                    i++
                }
            }
            
            return b.toString()
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
                Log.w("GoMoviesProviderJS", "Error in unpacker: ${e.message}")
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
                Log.w("GoMoviesProviderJS", "JS unpacking failed: ${e.message}")
                packed
            }
        }
    }
    
    data class GoMoviesResponse(
        @JsonProperty("a") val a: List<EncodeItem>?
    )
    
    data class EncodeItem(
        @JsonProperty("src") val src: String?,
        @JsonProperty("max") val max: Int?
    )
    
    data class QualityItem(
        val file: String,
        val quality: Int
    )
}
