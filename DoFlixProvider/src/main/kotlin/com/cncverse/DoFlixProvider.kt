package com.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class DoFlixProvider : MainAPI() {
    override var mainUrl = "https://dooflixpanel.com"
    override var name = "DoFlix"
    override val hasMainPage = true
    override var lang = "ta"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    private val headers = mapOf(
        "API-KEY" to "2pm95lc6prpdbk0ppji9rsqo",
    )

    private val mapper = jacksonObjectMapper()

    private fun getQualityFromString(qualityString: String?): SearchQuality? {
        return when (qualityString?.uppercase()) {
            "HD", "720P" -> SearchQuality.HD
            "FHD", "1080P" -> SearchQuality.HD
            "4K", "2160P" -> SearchQuality.HD
            "CAM", "CAMRIP" -> SearchQuality.Cam
            "HDCAM" -> SearchQuality.HdCam
            "TELECINE", "TC" -> SearchQuality.Telecine
            "TELESYNC", "TS" -> SearchQuality.Telesync
            "WORKPRINT", "WP" -> SearchQuality.WorkPrint
            else -> null
        }
    }

    data class SlideItem(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("image_link") val imageLink: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("action_type") val actionType: String,
        @JsonProperty("action_btn_text") val actionBtnText: String,
        @JsonProperty("action_id") val actionId: String,
        @JsonProperty("action_url") val actionUrl: String
    )

    data class SliderData(
        @JsonProperty("slider_type") val sliderType: String,
        @JsonProperty("slide") val slide: List<SlideItem>
    )

    data class PopularStar(
        @JsonProperty("star_id") val starId: String,
        @JsonProperty("star_name") val starName: String,
        @JsonProperty("image_url") val imageUrl: String
    )

    data class Country(
        @JsonProperty("country_id") val countryId: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("image_url") val imageUrl: String
    )

    data class Genre(
        @JsonProperty("genre_id") val genreId: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("image_url") val imageUrl: String
    )

    data class VideoItem(
        @JsonProperty("videos_id") val videosId: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("release") val release: String,
        @JsonProperty("is_tvseries") val isTvseries: String? = null,
        @JsonProperty("is_paid") val isPaid: String? = "0",
        @JsonProperty("runtime") val runtime: String,
        @JsonProperty("video_quality") val videoQuality: String,
        @JsonProperty("thumbnail_url") val thumbnailUrl: String,
        @JsonProperty("poster_url") val posterUrl: String
    )

    data class GenreWithVideos(
        @JsonProperty("genre_id") val genreId: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("videos") val videos: List<VideoItem>
    )

    data class HomePageApiResponse(
        @JsonProperty("slider") val slider: SliderData,
        @JsonProperty("popular_stars") val popularStars: List<PopularStar>,
        @JsonProperty("all_country") val allCountry: List<Country>,
        @JsonProperty("all_genre") val allGenre: List<Genre>,
        @JsonProperty("featured_tv_channel") val featuredTvChannel: List<Any>,
        @JsonProperty("latest_movies") val latestMovies: List<VideoItem>,
        @JsonProperty("latest_tvseries") val latestTvseries: List<VideoItem>,
        @JsonProperty("features_genre_and_movie") val featuresGenreAndMovie: List<GenreWithVideos>
    )

    data class SearchApiResponse(
        @JsonProperty("movie") val movie: List<VideoItem>,
        @JsonProperty("tvseries") val tvseries: List<VideoItem>,
        @JsonProperty("tv_channels") val tvChannels: List<Any>
    )

    data class GenreInfo(
        @JsonProperty("genre_id") val genreId: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("url") val url: String
    )

    data class CountryInfo(
        @JsonProperty("country_id") val countryId: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("url") val url: String
    )

    data class VideoFile(
        @JsonProperty("video_file_id") val videoFileId: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("stream_key") val streamKey: String,
        @JsonProperty("file_type") val fileType: String,
        @JsonProperty("file_url") val fileUrl: String,
        @JsonProperty("subtitle") val subtitle: List<Any>
    )

    data class DownloadLink(
        @JsonProperty("download_link_id") val downloadLinkId: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("videos_id") val videosId: String,
        @JsonProperty("resolution") val resolution: String,
        @JsonProperty("file_size") val fileSize: String,
        @JsonProperty("download_url") val downloadUrl: String,
        @JsonProperty("in_app_download") val inAppDownload: Boolean
    )

    data class Episode(
        @JsonProperty("episodes_id") val episodesId: String,
        @JsonProperty("episodes_name") val episodesName: String,
        @JsonProperty("stream_key") val streamKey: String,
        @JsonProperty("file_type") val fileType: String,
        @JsonProperty("image_url") val imageUrl: String,
        @JsonProperty("file_url") val fileUrl: String,
        @JsonProperty("subtitle") val subtitle: List<Any>
    )

    data class Season(
        @JsonProperty("seasons_id") val seasonsId: String,
        @JsonProperty("seasons_name") val seasonsName: String,
        @JsonProperty("episodes") val episodes: List<Episode>,
        @JsonProperty("enable_download") val enableDownload: String,
        @JsonProperty("download_links") val downloadLinks: List<Any>
    )

    data class RelatedItem(
        @JsonProperty("videos_id") val videosId: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("release") val release: String,
        @JsonProperty("is_tvseries") val isTvseries: String,
        @JsonProperty("is_paid") val isPaid: String,
        @JsonProperty("runtime") val runtime: String,
        @JsonProperty("video_quality") val videoQuality: String,
        @JsonProperty("thumbnail_url") val thumbnailUrl: String,
        @JsonProperty("poster_url") val posterUrl: String,
        @JsonProperty("genre") val genre: String? = null,
        @JsonProperty("country") val country: String? = null
    )

    data class SingleDetailsResponse(
        @JsonProperty("videos_id") val videosId: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("release") val release: String,
        @JsonProperty("runtime") val runtime: String,
        @JsonProperty("video_quality") val videoQuality: String,
        @JsonProperty("is_tvseries") val isTvseries: String,
        @JsonProperty("is_paid") val isPaid: String,
        @JsonProperty("enable_download") val enableDownload: String,
        @JsonProperty("thumbnail_url") val thumbnailUrl: String,
        @JsonProperty("poster_url") val posterUrl: String,
        @JsonProperty("genre") val genre: List<GenreInfo>,
        @JsonProperty("country") val country: List<CountryInfo>,
        @JsonProperty("director") val director: List<Any>,
        @JsonProperty("writer") val writer: List<Any>,
        @JsonProperty("cast") val cast: List<Any>,
        @JsonProperty("cast_and_crew") val castAndCrew: List<Any>,
        @JsonProperty("trailler_youtube_source") val traillerYoutubeSource: String,
        @JsonProperty("videos") val videos: List<VideoFile>? = null,
        @JsonProperty("download_links") val downloadLinks: List<DownloadLink>? = null,
        @JsonProperty("season") val season: List<Season>? = null,
        @JsonProperty("related_movie") val relatedMovie: List<RelatedItem>? = null,
        @JsonProperty("related_tvseries") val relatedTvseries: List<RelatedItem>? = null
    )

    override val mainPage = mainPageOf(
        "$mainUrl/rest-api//v130/home_content_for_android" to "Home"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val response = app.get(request.data, headers = headers)
        val homeData = mapper.readValue<HomePageApiResponse>(response.text)

        val homePageList = mutableListOf<HomePageList>()

        // Add slider content
        if (homeData.slider.slide.isNotEmpty()) {
            val sliderItems = homeData.slider.slide.map { slide ->
                val tvType = if (slide.actionType == "tvseries") TvType.TvSeries else TvType.Movie
                newMovieSearchResponse(
                    name = slide.title,
                    url = "movie,${slide.id}",
                    type = tvType
                ) {
                    this.posterUrl = slide.imageLink
                }
            }
            homePageList.add(HomePageList("Featured", sliderItems,isHorizontalImages = true))
        }

        // Add latest movies
        if (homeData.latestMovies.isNotEmpty()) {
            val movieItems = homeData.latestMovies.map { movie ->
                newMovieSearchResponse(
                    name = movie.title,
                    url = "movie,${movie.videosId}",
                    type = TvType.Movie
                ) {
                    this.posterUrl = movie.posterUrl
                    this.quality = getQualityFromString(movie.videoQuality)
                }
            }
            homePageList.add(HomePageList("Latest Movies", movieItems,isHorizontalImages = true))
        }

        // Add latest TV series
        if (homeData.latestTvseries.isNotEmpty()) {
            val tvItems = homeData.latestTvseries.map { tv ->
                newTvSeriesSearchResponse(
                    name = tv.title,
                    url = "tvseries,${tv.videosId}",
                    type = TvType.TvSeries
                ) {
                    this.posterUrl = tv.posterUrl
                    this.quality = getQualityFromString(tv.videoQuality)
                }
            }
            homePageList.add(HomePageList("Latest TV Series", tvItems,isHorizontalImages = true))
        }

        // Add featured genres with their movies
        homeData.featuresGenreAndMovie.take(5).forEach { genreData ->
            if (genreData.videos.isNotEmpty()) {
                val genreItems = genreData.videos.take(10).map { video ->
                    val tvType = if (video.isTvseries == "1") TvType.TvSeries else TvType.Movie
                    val baseUrl = if (tvType == TvType.TvSeries) "series" else "movie"
                    newMovieSearchResponse(
                        name = video.title,
                        url = "$baseUrl,${video.videosId}",
                        type = tvType
                    ) {
                        this.posterUrl = video.posterUrl
                        this.quality = getQualityFromString(video.videoQuality)
                    }
                }
                homePageList.add(HomePageList(genreData.name, genreItems,isHorizontalImages = true))
            }
        }

        return HomePageResponse(homePageList)
    }

    override suspend fun search(query: String): List<com.lagradost.cloudstream3.SearchResponse> {
        val searchUrl = "$mainUrl/rest-api//v130/search?q=${query}&type=movietvserieslive&range_to=0&range_from=0&tv_category_id=0&genre_id=0&country_id=0"
        val response = app.get(searchUrl, headers = headers)
        val searchData = mapper.readValue<SearchApiResponse>(response.text)
        val searchResults = mutableListOf<com.lagradost.cloudstream3.SearchResponse>()

        // Add movie results
        searchData.movie.forEach { movie ->
            val movieResult = newMovieSearchResponse(
                name = movie.title,
                url = "movie,${movie.videosId}",
                type = TvType.Movie
            ) {
                this.posterUrl = movie.posterUrl
                this.quality = getQualityFromString(movie.videoQuality)
                this.year = movie.release.toIntOrNull()
            }
            searchResults.add(movieResult)
        }

        // Add TV series results
        searchData.tvseries.forEach { series ->
            val seriesResult = newTvSeriesSearchResponse(
                name = series.title,
                url = "tvseries,${series.videosId}",
                type = TvType.TvSeries
            ) {
                this.posterUrl = series.posterUrl
                this.quality = getQualityFromString(series.videoQuality)
                this.year = series.release.substringBefore("-").toIntOrNull()
            }
            searchResults.add(seriesResult)
        }

        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        try {
            val parts = if (url.startsWith("https")) {
                val path = url.substringAfter("https://").substringAfter("/")
                path.split(",")
            } else {
                url.split(",")
            }
            val type = parts[0]
            val id = parts[1]
            val detailsUrl = "$mainUrl/rest-api//v130/single_details?type=$type&id=$id"
            val detailsResponse = app.get(detailsUrl, headers = headers)
            val details = mapper.readValue<SingleDetailsResponse>(detailsResponse.text)
            return if (type == "movie") {
                // Handle movie
                newMovieLoadResponse(
                    name = details.title,
                    url = url,
                    type = TvType.Movie,
                    dataUrl = details.videosId
                ) {
                    this.posterUrl = details.posterUrl
                    this.year = details.release.substringBefore("-").toIntOrNull()
                    this.plot = details.description
                    this.tags = details.genre.map { it.name }
                    this.duration = details.runtime.replace(" Min", "").replace("h ", ":").replace("m", "").toIntOrNull()
                    
                    // Add recommendations
                    details.relatedMovie?.let { related ->
                        this.recommendations = related.take(10).map { item ->
                            newMovieSearchResponse(
                                name = item.title,
                                url = "$mainUrl/movie/${item.slug}",
                                type = TvType.Movie
                            ) {
                                this.posterUrl = item.posterUrl
                                this.quality = getQualityFromString(item.videoQuality)
                                this.year = item.release.toIntOrNull()
                            }
                        }
                    }
                }
            } else {
                // Handle TV series
                newTvSeriesLoadResponse(
                    name = details.title,
                    url = url,
                    type = TvType.TvSeries,
                    episodes = details.season?.flatMap { season ->
                        season.episodes.mapIndexed { index, episode ->
                            Episode(
                                data = "${details.videosId}:${season.seasonsId}:${episode.episodesId}:${episode.streamKey}",
                                name = episode.episodesName,
                                season = season.seasonsName.replace("Season ", "").toIntOrNull() ?: 1,
                                episode = index + 1,
                                posterUrl = episode.imageUrl,
                                description = null
                            )
                        }
                    } ?: emptyList()
                ) {
                    this.posterUrl = details.posterUrl
                    this.year = details.release.substringBefore("-").toIntOrNull()
                    this.plot = details.description
                    this.tags = details.genre.map { it.name }                                      
                    // Add recommendations
                    details.relatedTvseries?.let { related ->
                        this.recommendations = related.take(10).map { item ->
                            newTvSeriesSearchResponse(
                                name = item.title,
                                url = "$mainUrl/series/${item.slug}",
                                type = TvType.TvSeries
                            ) {
                                this.posterUrl = item.posterUrl
                                this.quality = getQualityFromString(item.videoQuality)
                                this.year = item.release.toIntOrNull()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            // Data format for movies: videosId
            // Data format for episodes: videosId:seasonId:episodeId:streamKey
            val parts = data.split(":")
            
            if (parts.size == 1) {
                // Movie
                val videosId = parts[0]
                val detailsUrl = "$mainUrl/rest-api//v130/single_details?type=movie&id=$videosId"
                val detailsResponse = app.get(detailsUrl, headers = headers)
                val details = mapper.readValue<SingleDetailsResponse>(detailsResponse.text)
                
                // Add video links
                details.videos?.forEach { video ->
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = video.label,
                            url = video.fileUrl,
                            type = ExtractorLinkType.M3U8 
                        ) {
                            this.referer = mainUrl
                            this.headers = mapOf(
                                "Connection" to "Keep-Alive",
                                "Cookie" to "cf_clearance=M2_2Hy4lKRy_ruRX3dzOgm3iho1FHe2DUC1lq28BUtI-1737377622-1.2.1.1-6R8RaH94._H2BuNuotsjTZ3fAF6cLwPII0guemu9A5Xa46lpCJPuELycojdREwoonYS2kRTYcZ9_1c4h4epi2LtDvMM9jIoOZKE9pIdWa30peM1hRMpvffTjGUCraHsJNCJez8S_QZ6XkkdP7GeQ5iwiYaI6Grp6qSJWoq0Hj8lS7EITZ1LzyrALI6iLlYjgLmgLGa1VuhORWJBN8ZxrJIZ_ba_pqbrR9fjnyToqxZ0XQaZfk1d3rZyNWoZUjI98GoAxVjnKtcBQQG6b2jYPJuMbbYraGoa54N7E7BR__7o",
                                "referer" to "https://molop.art/",
                                "sec-ch-ua" to "\"Google Chrome\";v=131, \"Chromium\";v=131, \"Not_A Brand\";v=24",
                                "sec-ch-ua-mobile" to "?0",
                                "sec-ch-ua-platform" to "\"Windows\"",
                                "upgrade-insecure-requests" to "1",
                                "user-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                            )
                            this.quality = when (video.label.uppercase()) {
                                "DOOFLIX PLAYER", "FAST STREAM" -> Qualities.P1080.value
                                else -> Qualities.Unknown.value
                            }
                        }
                    )
                }
                
                return details.videos?.isNotEmpty() == true
            } else if (parts.size == 4) {
                // Episode
                val videosId = parts[0]
                val seasonId = parts[1]
                val episodeId = parts[2]
                val streamKey = parts[3]
                
                val detailsUrl = "$mainUrl/rest-api//v130/single_details?type=tvseries&id=$videosId"
                val detailsResponse = app.get(detailsUrl, headers = headers)
                val details = mapper.readValue<SingleDetailsResponse>(detailsResponse.text)
                
                // Find the specific episode
                val episode = details.season?.find { it.seasonsId == seasonId }
                    ?.episodes?.find { it.episodesId == episodeId }
                
                if (episode != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = episode.episodesName,
                            url = episode.fileUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = mainUrl
                             this.headers = mapOf(
                                "Connection" to "Keep-Alive",
                                "Cookie" to "cf_clearance=M2_2Hy4lKRy_ruRX3dzOgm3iho1FHe2DUC1lq28BUtI-1737377622-1.2.1.1-6R8RaH94._H2BuNuotsjTZ3fAF6cLwPII0guemu9A5Xa46lpCJPuELycojdREwoonYS2kRTYcZ9_1c4h4epi2LtDvMM9jIoOZKE9pIdWa30peM1hRMpvffTjGUCraHsJNCJez8S_QZ6XkkdP7GeQ5iwiYaI6Grp6qSJWoq0Hj8lS7EITZ1LzyrALI6iLlYjgLmgLGa1VuhORWJBN8ZxrJIZ_ba_pqbrR9fjnyToqxZ0XQaZfk1d3rZyNWoZUjI98GoAxVjnKtcBQQG6b2jYPJuMbbYraGoa54N7E7BR__7o",
                                "referer" to "https://molop.art/",
                                "sec-ch-ua" to "\"Google Chrome\";v=131, \"Chromium\";v=131, \"Not_A Brand\";v=24",
                                "sec-ch-ua-mobile" to "?0",
                                "sec-ch-ua-platform" to "\"Windows\"",
                                "upgrade-insecure-requests" to "1",
                                "user-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                            )
                            this.quality = when (episode.fileType.uppercase()) {
                                "HLS", "MKVCINEMAS" -> Qualities.P1080.value
                                else -> Qualities.Unknown.value
                            }
                        }
                    )
                    return true
                }
            }
            
            return false
        } catch (e: Exception) {
            return false
        }
    }
}
