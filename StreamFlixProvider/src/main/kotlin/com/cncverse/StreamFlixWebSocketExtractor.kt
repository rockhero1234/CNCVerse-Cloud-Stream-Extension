package com.cncverse

import com.google.gson.annotations.SerializedName
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.api.Log
import okhttp3.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class StreamFlixWebSocketExtractor {
    private val gson = Gson()
    private val client = OkHttpClient()

    data class WebSocketRequest(
        @SerializedName("t") val t: String,
        @SerializedName("d") val d: WebSocketData
    )

    data class WebSocketData(
        @SerializedName("a") val a: String,
        @SerializedName("r") val r: Int,
        @SerializedName("b") val b: WebSocketBody
    )

    data class WebSocketBody(
        @SerializedName("p") val p: String,
        @SerializedName("h") val h: String
    )

    data class EpisodeData(
        @SerializedName("key") val key: Int,
        @SerializedName("link") val link: String,
        @SerializedName("name") val name: String,
        @SerializedName("overview") val overview: String,
        @SerializedName("runtime") val runtime: Int,
        @SerializedName("still_path") val stillPath: String?,
        @SerializedName("vote_average") val voteAverage: Double
    )

    suspend fun getEpisodesFromWebSocket(movieKey: String, totalSeasons: Int = 1): Map<Int, Map<Int, EpisodeData>> {
        return withTimeoutOrNull(30000) { // 30 second timeout
            suspendCancellableCoroutine { continuation ->
                val request = Request.Builder()
                    .url("wss://chilflix-410be-default-rtdb.asia-southeast1.firebasedatabase.app/.ws?ns=chilflix-410be-default-rtdb&v=5")
                    .build()

                val webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    private var seasonsData = mutableMapOf<Int, Map<Int, EpisodeData>>()
                    private var receivedInitialData = false
                    private var currentSeason = 1
                    private var seasonsCompleted = 0

                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d("StreamFlix", "WebSocket opened, requesting $totalSeasons seasons")
                        
                        // Request first season
                        val requestData = WebSocketRequest(
                            t = "d",
                            d = WebSocketData(
                                a = "q",
                                r = currentSeason,
                                b = WebSocketBody(
                                    p = "Data/$movieKey/seasons/$currentSeason/episodes",
                                    h = ""
                                )
                            )
                        )
                        
                        val requestJson = gson.toJson(requestData)
                        webSocket.send(requestJson)
                        Log.d("StreamFlix", "Sent request for season $currentSeason: $requestJson")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Log.d("StreamFlix", "Received: $text")
                        
                        try {
                            val jsonObject = JsonParser.parseString(text).asJsonObject
                            
                            if (jsonObject.has("t") && jsonObject.get("t").asString == "d") {
                                val data = jsonObject.getAsJsonObject("d")
                                if (data.has("b") && data.getAsJsonObject("b").has("d")) {
                                    val bObject = data.getAsJsonObject("b")
                                    val episodes = bObject.getAsJsonObject("d")
                                    
                                    // Extract season number from path
                                    val path = bObject.get("p")?.asString ?: ""
                                    val seasonMatch = Regex("seasons/(\\d+)/episodes").find(path)
                                    val seasonNumber = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                                    
                                    val episodeMap = mutableMapOf<Int, EpisodeData>()
                                    episodes.entrySet().forEach { entry ->
                                        try {
                                            val episodeData = gson.fromJson(entry.value, EpisodeData::class.java)
                                            episodeMap[entry.key.toInt()] = episodeData
                                            Log.d("StreamFlix", "Parsed episode ${entry.key}: ${episodeData.name}")
                                        } catch (e: Exception) {
                                            Log.e("StreamFlix", "Error parsing episode: ${e.message}")
                                        }
                                    }
                                    
                                    if (episodeMap.isNotEmpty()) {
                                        seasonsData[seasonNumber] = episodeMap
                                        seasonsCompleted++
                                        Log.d("StreamFlix", "Added ${episodeMap.size} episodes for season $seasonNumber ($seasonsCompleted/$totalSeasons)")
                                        
                                        // Request next season if available
                                        if (seasonsCompleted < totalSeasons) {
                                            currentSeason++
                                            val requestData = WebSocketRequest(
                                                t = "d",
                                                d = WebSocketData(
                                                    a = "q",
                                                    r = currentSeason + 10, // Use different request ID
                                                    b = WebSocketBody(
                                                        p = "Data/$movieKey/seasons/$currentSeason/episodes",
                                                        h = ""
                                                    )
                                                )
                                            )
                                            
                                            webSocket.send(gson.toJson(requestData))
                                            Log.d("StreamFlix", "Requesting season $currentSeason")
                                        } else {
                                            // All seasons completed, close and return
                                            Log.d("StreamFlix", "All $totalSeasons seasons completed")
                                            if (continuation.isActive) {
                                                continuation.resume(seasonsData)
                                                webSocket.close(1000, "Done")
                                            }
                                        }
                                    }
                                } else if (!receivedInitialData) {
                                    // First response might be different, try again
                                    receivedInitialData = true
                                } else {
                                    // Probably season 2 response or no more data
                                    if (continuation.isActive) {
                                        continuation.resume(seasonsData)
                                        webSocket.close(1000, "Done")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("StreamFlix", "Error parsing WebSocket message: ${e.message}")
                            if (continuation.isActive) {
                                continuation.resume(seasonsData)
                                webSocket.close(1000, "Error")
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e("StreamFlix", "WebSocket failed: ${t.message}")
                        if (continuation.isActive) {
                            continuation.resume(emptyMap())
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d("StreamFlix", "WebSocket closing: $code $reason")
                        if (continuation.isActive) {
                            continuation.resume(seasonsData)
                        }
                    }
                })

                continuation.invokeOnCancellation {
                    webSocket.close(1000, "Cancelled")
                }
            }
        } ?: emptyMap()
    }
}
