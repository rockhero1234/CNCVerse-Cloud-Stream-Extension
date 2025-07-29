package com.hdo

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import android.util.Log
import android.content.Context
import com.hdo.providers.VidsrcProvider
import com.hdo.providers.VidsrcProProvider
import com.hdo.providers.TwoEmbedCCProviderJS
import com.hdo.providers.FMoviesProviderJS
import com.hdo.providers.GoMoviesProviderJS
import com.hdo.providers.IdFlixProvider
import com.hdo.providers.RidoMovieProvider
import com.hdo.providers.YMoviesProvider
import com.hdo.providers.UniqueStreamProvider
import com.hdo.providers.CatFlixProvider
import com.hdo.providers.VidsrcVipProvider
import com.hdo.providers.VidLinkProvider
import com.hdo.providers.YesMoviesProvider
import com.hdo.providers.M4UFreeProvider

class HDO : TmdbProvider() {
    override var name = "HDO"
    override val hasMainPage = true
    override var lang = "ta"
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    companion object {
         var cont: Context? = null
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mediaData = AppUtils.parseJson<TmdbLink>(data)!!.toLinkData()
        
        Log.d("HDOProvider", "Loading links for: ${mediaData.title} (${mediaData.type})")
        
        // Use multiple native Kotlin providers
        safeApiCall {
            val movieInfo = VidsrcProvider.MovieInfo(
                tmdbId = mediaData.tmdbId,
                imdbId = mediaData.imdbId,
                title = mediaData.title,
                year = mediaData.year,
                season = mediaData.season,
                episode = mediaData.episode,
                type = mediaData.type
            )
            SubUtils.invokeWyZIESUBAPI(
                mediaData.imdbId,
                mediaData.season,
                mediaData.episode,
                subtitleCallback,
            )
        
    
            SubUtils.invokeSubtitleAPI(
                mediaData.imdbId,
                mediaData.season,
                mediaData.episode,
                subtitleCallback
            )
            var hasResults = false
            
            // Try multiple providers
            val providers = listOf(
                "Vidsrc" to suspend { VidsrcProvider.getVideoLinks(movieInfo, callback) },
                "VidsrcPro" to suspend { VidsrcProProvider.getVideoLinks(movieInfo, callback) },
                "VidsrcVip" to suspend { VidsrcVipProvider.getVideoLinks(movieInfo, callback) },
                "2EmbedCC" to suspend { TwoEmbedCCProviderJS.getVideoLinks(movieInfo, callback) },
                "FMovies" to suspend { FMoviesProviderJS.getVideoLinks(movieInfo, callback) },
                "GoMovies" to suspend { GoMoviesProviderJS.getVideoLinks(movieInfo, callback) },
                "IdFlix" to suspend { IdFlixProvider.getVideoLinks(movieInfo, callback) },
                "RidoMovie" to suspend { RidoMovieProvider.getVideoLinks(movieInfo, callback) },
                "YMovies" to suspend { YMoviesProvider.getVideoLinks(movieInfo, callback) },
                "UniqueStream" to suspend { UniqueStreamProvider.getVideoLinks(movieInfo, callback) },
                "CatFlix" to suspend { CatFlixProvider.getVideoLinks(movieInfo, callback) },
                "VidLink" to suspend { VidLinkProvider.getVideoLinks(movieInfo, callback) },
                "YesMovies" to suspend { YesMoviesProvider.getVideoLinks(movieInfo, callback) },
                "M4UFree" to suspend { M4UFreeProvider.getVideoLinks(movieInfo, callback) }
            )
            
            for ((providerName, providerFunc) in providers) {
                try {
                    Log.d("HDOProvider", "Trying provider: $providerName")
                    
                    val success = providerFunc()
                    if (success) {
                        Log.d("HDOProvider", "Successfully loaded links from $providerName")
                        hasResults = true
                        // Continue to try other providers too for more sources
                    } else {
                        Log.w("HDOProvider", "No links from $providerName")
                    }
                } catch (e: Exception) {
                    Log.e("HDOProvider", "Error with provider $providerName: ${e.message}", e)
                }
            }
            
            if (hasResults) {
                Log.d("HDOProvider", "Successfully loaded video links from one or more providers")
        
            } else {
                Log.w("HDOProvider", "Failed to load video links from all providers")
            }
        }


        
        return true
    }

    // --- Data classes and helper functions ---

    data class ProviderData(@JsonProperty("providers") val providers: List<String>)

    private fun TmdbLink.toLinkData(): VidsrcProvider.MovieInfo {
        val isMovie = this.season == null
        return VidsrcProvider.MovieInfo(
            imdbId = imdbID,
            tmdbId = tmdbID,
            title = movieName,
            year = movieName?.substringAfterLast("(", "")?.substringBefore(")", "")?.toIntOrNull(),
            season = season,
            episode = episode,
            type = if (isMovie) "movie" else "tv"
        )
    }
}