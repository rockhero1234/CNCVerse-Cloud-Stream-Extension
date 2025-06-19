package com.cncverse

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody

class TamilUltraProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.tamilultratv.com"
    override var name = "TamilUltra"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Live
    )

    override val mainPage = mainPageOf(
        "$mainUrl/channels/tamil/" to "Trending Channels",
        "$mainUrl/channels/sports/" to "Sports",
        "$mainUrl//channels/tamil-kids/" to "Tamil Kids",
        "$mainUrl/channels/tamil-entertainment/ " to "Tamil Entertainment",
        "$mainUrl/channels/tamil-music/" to "Tamil Music",
        "$mainUrl/channels/tamil-infotainment/" to "Tamil Infotainment",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "/page/$page/").document
        }

        val home = document.select("div.items > article.item").mapNotNull {
                it.toSearchResult()
            }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.data > h3 > a")?.text()?.toString()?.trim()
            ?: return null
        val href = fixUrl(this.selectFirst("div.data > h3 > a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("div.poster > img")?.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Live) {
                this.posterUrl = posterUrl
            }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        return document.select("div.result-item").mapNotNull {
            val title =
                it.selectFirst("article > div.details > div.title > a")?.text().toString().trim()
            val href = fixUrl(
                it.selectFirst("article > div.details > div.title > a")?.attr("href").toString()
            )

            val finalUrl = if (href.startsWith("/")) {
                mainUrl + href
            } else {
                href
            }
            val posterUrl = fixUrlNull(
                it.selectFirst("article > div.image > div.thumbnail > a > img")?.attr("src")
            )

            newMovieSearchResponse(title, finalUrl, TvType.Live) {
                    this.posterUrl = posterUrl
                }
        }
    }

    private suspend fun getEmbed(postid: String?, nume: String, referUrl: String?): NiceResponse {
        val body = FormBody.Builder()
            .addEncoded("action", "doo_player_ajax")
            .addEncoded("post", postid.toString())
            .addEncoded("nume", nume)
            .addEncoded("type", "movie")
            .build()

        return app.post(
            "$mainUrl/wp-admin/admin-ajax.php",
            requestBody = body,
            referer = referUrl
        )
    }

    data class EmbedUrl (
        @JsonProperty("embed_url") var embedUrl : String,
        @JsonProperty("type") var type : String?
    )

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("div.sheader > div.data > h1").text()
        val poster = fixUrlNull(doc.selectFirst("div.poster > img")?.attr("src"))
        val id = doc.select("#player-option-1").attr("data-post")
        

        return newMovieLoadResponse(title, id, TvType.Live, "$url,$id") {
                this.posterUrl = poster
            }
    }

    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val referer = data.substringBefore(",")
        val link = fixUrlNull(
                getEmbed(
                    data.substringAfter(","),
                    "1",
                    referer
                ).parsed<EmbedUrl>().embedUrl
            ).toString()
        callback.invoke(
            newExtractorLink(
                name,
                name,
                mainUrl + link.substringAfter(".php?"),
                type = ExtractorLinkType.M3U8
            )
            {
                this.quality = Qualities.Unknown.value
                this.referer = referer
            }
        )

        return true
    }

}

