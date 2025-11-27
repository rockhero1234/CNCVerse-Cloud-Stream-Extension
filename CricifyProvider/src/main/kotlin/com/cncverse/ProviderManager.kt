package com.cncverse

import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class ProviderData(
    val id: Int,
    val title: String,
    val image: String,
    val catLink: String?
)

object ProviderManager {
    // Default fallback URL (will be replaced by Firebase Remote Config)
    private const val DEFAULT_PROVIDERS_URL = "https://cfymarkscanjiostar80.top/cats.txt"
    
    // Cached base URL from Firebase
    private var cachedBaseUrl: String? = null
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Fallback providers (current static list)
    private val fallbackProviders = listOf(
        mapOf("id" to 13, "title" to "TATA PLAY", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQz_qYe3Y4S5bXXVlPtXQnqtAkLw1-no57QHhPyMgWE0SQmxujzHxZKiDs&s=10", "catLink" to "https://hotstarlive.delta-cloud.workers.dev/?token=a13d9c-4b782a-6c90fd-9a1b84"),
        mapOf("id" to 14, "title" to "HOTSTAR", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRWwYjMvB58DMLsL9Ii2fhvw6NBYvD1iVCjOMU8TXBLJt0eibLGOjoRkLJP&s=10", "catLink" to "https://hotstar-live-event.alpha-circuit.workers.dev/?token=154dc5-7a9126-56996d-1fa267"),
       
    )
    
    /**
     * Gets the providers URL by fetching from Firebase Remote Config first
     * Falls back to default URL if Firebase fetch fails
     */
    private suspend fun getProvidersUrl(): String {
        // Return cached URL if available
        cachedBaseUrl?.let { return "${it}cats.txt" }
        
        // Try to fetch from Firebase Remote Config
        val firebaseUrl = FirebaseRemoteConfigFetcher.getProviderApiUrl()
        if (!firebaseUrl.isNullOrBlank()) {
            cachedBaseUrl = firebaseUrl.trimEnd('/')
            return "${cachedBaseUrl}/cats.txt"
        }
        
        // Fall back to default URL
        return DEFAULT_PROVIDERS_URL
    }
    
    suspend fun fetchProviders(): List<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                // Get the providers URL (from Firebase or fallback)
                val providersUrl = getProvidersUrl()
                
                val request = Request.Builder()
                    .url(providersUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val encryptedData = response.body?.string()
                    if (!encryptedData.isNullOrBlank()) {
                        val decryptedData = CryptoUtils.decryptData(encryptedData.trim())
                        if (!decryptedData.isNullOrBlank()) {
                            val providers = parseJson<List<ProviderData>>(decryptedData)
                            // Filter providers that have catLink (exclude category headers)
                            return@withContext providers?.filter { !it.catLink.isNullOrBlank() }
                                ?.map { provider ->
                                    mapOf(
                                        "id" to provider.id,
                                        "title" to provider.title,
                                        "image" to provider.image,
                                        "catLink" to provider.catLink!!
                                    )
                                } ?: fallbackProviders
                        } else {
                        }
                    } else {
                    }
                } else {
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Return fallback providers if fetching fails
            fallbackProviders
        }
    }
}
