package com.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newDrmExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.CLEARKEY_UUID
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStream
import java.util.UUID
import android.util.Base64
import java.nio.charset.StandardCharsets

class HeaderReplacementInterceptor(private val customHeaders: Map<String, String>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
        
        // Remove existing headers that we want to replace
        customHeaders.keys.forEach { headerName ->
            requestBuilder.removeHeader(headerName)
        }
        
        // Add our custom headers
        customHeaders.forEach { (name, value) ->
            requestBuilder.addHeader(name, value)
        }
        
        return chain.proceed(requestBuilder.build())
    }
}

class Cricify(
    private val customName: String = "IPTV Player",
    private val customMainUrl: String = "https://fifabd.site/OPLLX7/LIVE2.m3u"
) : MainAPI() {
    override var lang = "hi"
    override var mainUrl = customMainUrl
    override var name = customName
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Live,
    )
    private val headers = mapOf(
        "accept" to "*/*",
        "Cache-Control" to "no-cache, no-store",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0",
    )

    private val customHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HeaderReplacementInterceptor(headers))
            .build()
    }

    private suspend fun getWithCustomHeaders(url: String): String {
        val request = Request.Builder()
            .url(url)
            .build()
        
        return customHttpClient.newCall(request).execute().use { response ->
            response.body?.string() ?: ""
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val data = IptvPlaylistParser().parseM3U(getWithCustomHeaders(mainUrl))
        return newHomePageResponse(data.items.groupBy{it.attributes["group-title"]}.map { group ->
            val title = group.key ?: ""
            val show = group.value.map { channel ->
                val streamurl = channel.url.toString()
                val channelname = channel.title.toString()
                val posterurl = channel.attributes["tvg-logo"].toString()
                val nation = channel.attributes["group-title"].toString()
                val key= channel.key ?: ""
                val keyid= channel.keyid ?: ""
                val userAgent = channel.userAgent ?: ""
                val cookie = channel.cookie ?: ""
                newLiveSearchResponse(channelname, LoadData(streamurl, channelname, posterurl, nation, key, keyid, userAgent, cookie).toJson(), TvType.Live)
                {
                    this.posterUrl=posterurl
                    this.apiName
                    this.lang=channel.attributes["group-title"]
                }
            }
            HomePageList(
                title,
                show,
                isHorizontalImages = true
            )
        })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = IptvPlaylistParser().parseM3U(getWithCustomHeaders(mainUrl))      
        return data.items.filter { it.title?.contains(query,ignoreCase = true) ?: false }.map { channel ->
                val streamurl = channel.url.toString()
                val channelname = channel.title.toString()
                val posterurl = channel.attributes["tvg-logo"].toString()
                val nation = channel.attributes["group-title"].toString()
                val key = channel.key ?: ""
                val keyid = channel.keyid ?: ""
                val userAgent = channel.userAgent ?: ""
                val cookie = channel.cookie ?: ""
            newLiveSearchResponse(channelname, LoadData(streamurl, channelname, posterurl, nation, key, keyid, userAgent, cookie).toJson(), TvType.Live)
            {
                this.posterUrl=posterurl
                this.apiName
                this.lang=channel.attributes["group-title"]
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<LoadData>(url)
        return newLiveStreamLoadResponse(data.title,data.url,url)
        {
            this.posterUrl=data.poster
            this.plot=data.nation
        }
    }
    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val nation: String,
        val key: String,
        val keyid: String,
        val userAgent: String,
        val cookie: String,
    )
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        if (loadData.url.contains("mpd"))
        {  
            val headers = mutableMapOf<String, String>()
                if (loadData.userAgent.isNotEmpty()) {
                    headers["User-Agent"] = loadData.userAgent
                }
                if (loadData.cookie.isNotEmpty()) {
                    headers["Cookie"] = loadData.cookie
                }
            
            val hasValidKeys = loadData.key.isNotEmpty() && loadData.keyid.isNotEmpty() && 
                              loadData.key.trim() != "null" && loadData.keyid.trim() != "null"
            
            if (hasValidKeys) {
                callback.invoke(
                    newDrmExtractorLink(
                        this.name,
                        this.name,
                        loadData.url,
                        INFER_TYPE,
                        CLEARKEY_UUID
                    )
                    {
                        this.quality=Qualities.Unknown.value
                        if (headers.isNotEmpty()) {
                                this.headers = headers
                            }
                        this.key=loadData.key.trim()
                        this.kid=loadData.keyid.trim()
                    }
                )
            } else {
                // Fallback to regular MPD link if no DRM keys available
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = loadData.url,
                        ExtractorLinkType.DASH
                    ) {
                        this.referer = ""
                        this.quality = Qualities.Unknown.value
                        if (headers.isNotEmpty()) {
                            this.headers = headers
                        }
                    }
                )
            }
        }
        else if(loadData.url.contains("&e=.m3u"))
            {
                val headers = mutableMapOf<String, String>()
                if (loadData.userAgent.isNotEmpty()) {
                    headers["User-Agent"] = loadData.userAgent
                }
                if (loadData.cookie.isNotEmpty()) {
                    headers["Cookie"] = loadData.cookie
                }
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = loadData.url,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = ""
                        this.quality = Qualities.Unknown.value
                        if (headers.isNotEmpty()) {
                            this.headers = headers
                        }
                    }
                )

            }
        else if(loadData.url.contains("play.php?"))
            {
                val headers = mutableMapOf("User-Agent" to loadData.userAgent)
                if (loadData.cookie.isNotEmpty()) {
                    headers["Cookie"] = loadData.cookie
                }
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = loadData.url,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = ""
                        this.quality = Qualities.Unknown.value
                        this.headers = headers
                    }
                )

            }
        else
        {
            val headers = mutableMapOf<String, String>()
            if (loadData.userAgent.isNotEmpty()) {
                headers["User-Agent"] = loadData.userAgent
            }
            if (loadData.cookie.isNotEmpty()) {
                headers["Cookie"] = loadData.cookie
            }
            callback.invoke(
                newExtractorLink(
                    this.name,
                    loadData.title,
                    url = loadData.url,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = Qualities.Unknown.value
                    if (headers.isNotEmpty()) {
                        this.headers = headers
                    }
                }
            )

        }
        return true
    }
}


data class Playlist(
    val items: List<PlaylistItem> = emptyList(),
)

data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null,
    val key: String? = null,
    val keyid: String? = null,
    val cookie: String? = null,
)


class IptvPlaylistParser {


    /**
     * Parse M3U8 string into [Playlist]
     *
     * @param content M3U8 content string.
     * @throws PlaylistParserException if an error occurs.
     */
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    private fun decodeHex(hexString: String?): String {
        if (hexString.isNullOrEmpty()) return ""
        
        return try {
            // Remove any whitespace and ensure even length
            val cleanHex = hexString.trim().replace(" ", "")
            if (cleanHex.length % 2 != 0) return ""
            
            //hexStringToByteArray
            val length = cleanHex.length
            val byteArray = ByteArray(length / 2)

            for (i in 0 until length step 2) {
                val firstDigit = Character.digit(cleanHex[i], 16)
                val secondDigit = Character.digit(cleanHex[i + 1], 16)
                if (firstDigit == -1 || secondDigit == -1) return ""
                byteArray[i / 2] = ((firstDigit shl 4) + secondDigit).toByte()
            }
            //byteArrayToBase64
            val base64ByteArray = Base64.encode(byteArray, Base64.NO_PADDING)
            String(base64ByteArray, StandardCharsets.UTF_8).trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Parse M3U8 content [InputStream] into [Playlist]
     *
     * @param input Stream of input data.
     * @throws PlaylistParserException if an error occurs.
     */
    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems: MutableList<PlaylistItem> = mutableListOf()
        var currentIndex = -1

        var line: String? = reader.readLine()

        while (line != null) {
            if (line.isNotEmpty()) {
                when {
                    line.startsWith(EXT_INF) -> {
                        val title = line.getTitle()
                        val attributes = line.getAttributes()
                        
                        // Extract DRM keys from attributes if present
                        val keyFromAttr = attributes["key"] ?: attributes["drm-key"]
                        val keyidFromAttr = attributes["keyid"] ?: attributes["drm-keyid"] ?: attributes["kid"]
                        
                        playlistItems.add(
                            PlaylistItem(
                                title = title, 
                                attributes = attributes,
                                key = keyFromAttr,
                                keyid = keyidFromAttr
                            )
                        )
                        currentIndex = playlistItems.size - 1
                    }
                    line.startsWith("#EXTHTTP:") -> {
                        // Parse JSON for cookie
                        if (currentIndex >= 0 && currentIndex < playlistItems.size) {
                            val item = playlistItems[currentIndex]
                            val json = line.removePrefix("#EXTHTTP:").trim()
                            val cookie = try {
                                val map = parseJson<Map<String, String>>(json)
                                map["cookie"]
                            } catch (e: Exception) {
                                null
                            }
                            playlistItems[currentIndex] = item.copy(cookie = cookie)
                        }
                    }
                    line.startsWith(EXT_VLC_OPT) -> {
                        if (currentIndex >= 0 && currentIndex < playlistItems.size) {
                            val item = playlistItems[currentIndex]
                            val userAgent = line.getTagValue("http-user-agent")
                            val referrer = line.getTagValue("http-referrer")
                            val headers = if (referrer != null) {
                                item.headers + mapOf("referrer" to referrer)
                            } else item.headers
                            playlistItems[currentIndex] =
                                item.copy(userAgent = userAgent, headers = headers)
                        }
                    }
                    line.startsWith("#KODIPROP:inputstream.adaptive.license_key=") -> {
                        // Parse keyid and key from license_key
                        if (currentIndex >= 0 && currentIndex < playlistItems.size) {
                            val item = playlistItems[currentIndex]
                            val licenseKey = line.removePrefix("#KODIPROP:inputstream.adaptive.license_key=").trim()
                            
                            // Handle different license key formats
                            val parts = when {
                                licenseKey.contains(":") -> licenseKey.split(":")
                                licenseKey.contains(",") -> licenseKey.split(",")
                                else -> listOf(licenseKey)
                            }
                            
                            val keyid = decodeHex(parts.getOrNull(0))
                            val key = decodeHex(parts.getOrNull(1))                      
                            playlistItems[currentIndex] = item.copy(key = key, keyid = keyid)
                        }
                    }
                    !line.startsWith("#") -> {
                        if (currentIndex >= 0 && currentIndex < playlistItems.size) {
                            val item = playlistItems[currentIndex]
                            val url = line.getUrl()
                            val userAgent = line.getUrlParameter("user-agent")
                            val referrer = line.getUrlParameter("referer")
                            val key = line.getUrlParameter("key")
                            val keyid = line.getUrlParameter("keyid")
                            val urlHeaders = if (referrer != null) {
                                item.headers + mapOf("referrer" to referrer)
                            } else item.headers
                            playlistItems[currentIndex] =
                                item.copy(
                                    url = url,
                                    headers = item.headers + urlHeaders,
                                    userAgent = userAgent ?: item.userAgent,
                                    key = key ?: item.key,
                                    keyid = keyid ?: item.keyid
                                )
                        }
                    }
                }
            }
            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }

    /**
     * Replace "" (quotes) from given string.
     */
    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    /**
     * Check if given content is valid M3U8 playlist.
     */
    private fun String.isExtendedM3u(): Boolean = 
        startsWith(EXT_M3U) || startsWith(EXT_INF) || startsWith("#KODIPROP")

    /**
     * Get title of media.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTINF:-1 tvg-id="1234" group-title="Kids" tvg-logo="url/to/logo", Title
     * ```
     * Result: Title
     */
    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    /**
     * Get media url.
     *
     * Example:-
     *
     * Input:
     * ```
     * https://example.com/sample.m3u8|user-agent="Custom"
     * ```
     * Result: https://example.com/sample.m3u8
     */
    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    /**
     * Get url parameters.
     *
     * Example:-
     *
     * Input:
     * ```
     * http://192.54.104.122:8080/d/abcdef/video.mp4|User-Agent=Mozilla&Referer=CustomReferrer
     * ```
     * Result will be equivalent to kotlin map:
     * ```Kotlin
     * mapOf(
     *   "User-Agent" to "Mozilla",
     *   "Referer" to "CustomReferrer"
     * )
     * ```
     */
  /*  private fun String.getUrlParameters(): Map<String, String> {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val headersString = replace(urlRegex, "").replaceQuotesAndTrim()
        return headersString.split("&").mapNotNull {
            val pair = it.split("=")
            if (pair.size == 2) pair.first() to pair.last() else null
        }.toMap()
    }

   */

    /**
     * Get url parameter with key.
     *
     * Example:-
     *
     * Input:
     * ```
     * http://192.54.104.122:8080/d/abcdef/video.mp4|User-Agent=Mozilla&Referer=CustomReferrer
     * ```
     * If given key is `user-agent`, then
     *
     * Result: Mozilla
     */
    private fun String.getUrlParameter(key: String): String? {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex = Regex("$key=(\\w[^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()
        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    /**
     * Get attributes from `#EXTINF` tag as Map<String, String>.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTINF:-1 tvg-id="1234" group-title="Kids" tvg-logo="url/to/logo", Title
     * ```
     * Result will be equivalent to kotlin map:
     * ```Kotlin
     * mapOf(
     *   "tvg-id" to "1234",
     *   "group-title" to "Kids",
     *   "tvg-logo" to "url/to/logo"
     *)
     * ```
     */
    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()
        return attributesString.split(Regex("\\s")).mapNotNull {
            val pair = it.split("=")
            if (pair.size == 2) pair.first() to pair.last()
                .replaceQuotesAndTrim() else null
        }.toMap()
    }

    /**
     * Get value from a tag.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTVLCOPT:http-referrer=http://example.com/
     * ```
     * Result: http://example.com/
     */
    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)
        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }

}

/**
 * Exception thrown when an error occurs while parsing playlist.
 */
sealed class PlaylistParserException(message: String) : Exception(message) {

    /**
     * Exception thrown if given file content is not valid.
     */
    class InvalidHeader :
        PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")

}
