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
    private const val PROVIDERS_URL = "https://cfyhkjdbjkgvhjhb65.top/cats.txt"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Fallback providers (current static list)
    private val fallbackProviders = listOf(
        mapOf("id" to 13, "title" to "TATA PLAY", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQz_qYe3Y4S5bXXVlPtXQnqtAkLw1-no57QHhPyMgWE0SQmxujzHxZKiDs&s=10", "catLink" to "https://hotstar-live.api-horizon.workers.dev/?token=240bb9-374e2e-3c13f0-4a7xz5"),
        mapOf("id" to 14, "title" to "HOTSTAR", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRWwYjMvB58DMLsL9Ii2fhvw6NBYvD1iVCjOMU8TXBLJt0eibLGOjoRkLJP&s=10", "catLink" to "https://hotstar-live-event.alpha-circuit.workers.dev/?token=154dc5-7a9126-56996d-1fa267"),
        mapOf("id" to 15, "title" to "TOFFEE", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSMyZDO9XUGRDgVFJZ1s1EsqmQmGkYZtzxtqxQEokFBN-JQ8c8DA1Z2FEl1&s=10", "catLink" to "https://tv.noobon.top/playlist/toffee.php"),
        mapOf("id" to 17, "title" to "T SPORTS ", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRJ0QvfKyjAqcCOumIXjcuYg505GnaBeVk2lQ&usqp=CAU ", "catLink" to "https://fifabangladesh2-xyz-ekkj.spidy.online/AYN/tsports.m3u  "),
        mapOf("id" to 18, "title" to "FANCODE IND", "image" to "https://play-lh.googleusercontent.com/lp1Tdhp75MQyrHqrsyRBV74HxoL3Ko8KRAjOUI1wUHREAxuuVwKR6vnamgvMEn4C4Q", "catLink" to "https://raw.githubusercontent.com/Jitendraunatti/fancode/refs/heads/main/data/fancode.m3u"),
        mapOf("id" to 19, "title" to "SONYLIV", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTzscCrHEfnHNeZdMO3haF1XSVgjskN4TNv0g&usqp=CAU ", "catLink" to "https://sony-live-event.alpha-circuit.workers.dev/?token=42e4f5-2d863b-3c37d8-7f3f51 "),
        mapOf("id" to 22, "title" to "JIO IND", "image" to "https://uxwing.com/wp-content/themes/uxwing/download/brands-and-social-media/jio-logo-icon.png ", "catLink" to "https://jiotv.byte-vault.workers.dev/?token=42e4f5-2d863b-3c37d8-7f3f50"),
        mapOf("id" to 29, "title" to "SONY BD", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRxsCm4WKugE7ubLr2J3AP7s-hqHl0dh69ImA&usqp=CAU", "catLink" to "https://bdix.short.gy/sonybd/playlist.m3u"),
        mapOf("id" to 31, "title" to "SONY IND", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRxsCm4WKugE7ubLr2J3AP7s-hqHl0dh69ImA&usqp=CAU", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/main/sliv.m3u"),
        mapOf("id" to 48, "title" to "SUN DIRECT", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSwc4OuqPmOP-Fi9dhfiDw_q-s3rOmgCPla_IaE76VD2KRQ7c4KHeI2zJY&s=10", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/suntv.m3u"),
        mapOf("id" to 70, "title" to "VOOT BD", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQfS6QZFts2FoedMGZE28H7Kh158PsrNIiabFBVJMy_jXa8Tvvb9WAlut8&s=10", "catLink" to "https://ranapk.short.gy/VOOTBD.m3u"),
        mapOf("id" to 71, "title" to "VOOT IND", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQfS6QZFts2FoedMGZE28H7Kh158PsrNIiabFBVJMy_jXa8Tvvb9WAlut8&s=10", "catLink" to "https://jiocinema-live.cloud-hatch.workers.dev/?token=42e4f5-2d413b-3c37d8-5f3f49"),
        mapOf("id" to 85, "title" to "SUN NXT", "image" to "https://upload.wikimedia.org/wikipedia/en/d/d5/Sun_NXT_logo_small.png", "catLink" to "https://raw.githubusercontent.com/alexandermail371/cricfytv/refs/heads/main/sunxt.m3u"),
        mapOf("id" to 90, "title" to "AIRTEL IND", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQf7pkggfHJKj2R8O6ttuHxgv-vQVL03xUeAg&usqp=CAU", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/main/artl.m3u"),
        mapOf("id" to 92, "title" to "DISTRO TV", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRYQjBTT5SL_kuJF7CbQtoSEA7PzyiH9RYIuDO9F1sx87CtiULDyiDf7ybt&s=10", "catLink" to "https://playlist-storage.pages.dev/PLAYLIST/DistroTV.m3u"),
        mapOf("id" to 104, "title" to "ZEE5", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQS0OT2NFe9Jb4ofg_DrXx42EKLgyGnSGwoLg&usqp=CAU", "catLink" to "https://zee5.cloud-hatch.workers.dev/?token=42e4f5-2d413b-3c37d8-7f3f49"),
        mapOf("id" to 106, "title" to "JIOTV+", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRh5KeAyYdOyxaCWDPbiUsJW7Oy4v_7uFqf06rIwGxaWc6nQuNVqZ2Q_Qej&s=10", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/jtv.m3u"),
        mapOf("id" to 110, "title" to "JIOLIVE IND", "image" to "https://lens-storage.storage.googleapis.com/png/bb364a303da24e5db23f01bac26949cf", "catLink" to "https://raw.githubusercontent.com/alex4528/jc_live/refs/heads/main/jevents_live.m3u"),
        mapOf("id" to 114, "title" to "TAPMAD PK", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT4X-7suwtvYWwoa6m0ngFTKZt5Hg5Z2kQF1g&usqp=CAU", "catLink" to "https://tv.noobon.top/playlist/tapmad.php"),
        mapOf("id" to 117, "title" to "ZEE5 IN", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQS0OT2NFe9Jb4ofg_DrXx42EKLgyGnSGwoLg&usqp=CAU", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/z5.m3u"),
        mapOf("id" to 126, "title" to "WORLD TV", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSg8OiEwOT5UL5UttBZ5Tnhgsod8i2EQlfB97FFchdBOo8e_PfxvR8RJ68&s=10", "catLink" to "https://ranapk.short.gy/WORLDTV1.m3u8"),
        mapOf("id" to 129, "title" to "AYNA", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQvAant65yQ_au1e51MFs-uiE6juswXv4ZJoNCpzBBSg4q7DzJ1NeliS80c&s=10", "catLink" to "https://tv.noobon.top/playlist/aynaott.php"),
        mapOf("id" to 130, "title" to "JIO CINEMA IND", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQc3qZ1WgzPyFRX4cWIBJF0MSjWW3gZcLFycg&usqp=CAU", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/jcinema.m3u"),
        mapOf("id" to 131, "title" to "DISH TV", "image" to "https://m.media-amazon.com/images/S/stores-image-uploads-eu-prod/1/AmazonStores/A21TJRUUN4KGV/d5086253b614724be106c06be13f7d54.w600.h600._RO299,1,0,0,0,0,0,0,0,0,15_FMpng_.jpg", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/dishtv.m3u"),
        mapOf("id" to 132, "title" to "SHOOQ PK", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSvSWLnpgyvbzV9rHkREzbsX1Rzh2IbEZBL8yPpSv8aCPmy1nVcv7BhIWQ&s=10", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/shoq.m3u"),
        mapOf("id" to 133, "title" to "SAMSUNG TV", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQI9T5vcm8wU-dLuaK5vBfoHpz8KL9Ru0aU1eoVaKNcqauxGtRTfvI1rGTA&s=10", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/samsungtv.m3u"),
        mapOf("id" to 134, "title" to "JAGOBD", "image" to "https://www.jagobd.com/wp-content/uploads/2015/10/web_hi_res_512.png", "catLink" to "https://tv.noobon.top/playlist/jagobd.php"),
        mapOf("id" to 135, "title" to "JADOO", "image" to "https://bdix.net/wp-content/uploads/2019/07/Jadoo-Digital-Logo-PNG-1002x1024.png", "catLink" to "https://fifabangladesh.site/PLAYLIST/jadoo.php"),
        mapOf("id" to 136, "title" to "PISHOW", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcS1hOo397X8uamgdXoknED8klICRLPCqwuEUtB394H2cc7YIyYiD78s-B8&s=10", "catLink" to "http://playlist-storage.pages.dev/PLAYLIST/playboxtv.m3u"),
        mapOf("id" to 146, "title" to "CRICHD", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ08h1gOe7MPxwehZBrbYKAUtjv22B6rAJ1kMkN-cea64Ka49KUyGU2lpTz&s=10", "catLink" to "https://tv.noobon.top/crichd/playlist.php"),
        mapOf("id" to 150, "title" to "ZAP SPORTS", "image" to "https://i.ibb.co/dJfysm3V/zap-Sports.png", "catLink" to "https://tv.noobon.top/zapx/api.php?action=getIPTVPlaylist"),
        mapOf("id" to 151, "title" to "Pirates TV", "image" to "https://raw.githubusercontent.com/FunctionError/Logos/main/Pirates-Tv.png", "catLink" to "https://raw.githubusercontent.com/FunctionError/PiratesTv/refs/heads/main/combined_playlist.m3u"),
        mapOf("id" to 152, "title" to "YUPPTV", "image" to "https://d229kpbsb5jevy.cloudfront.net/bott/v2/networks/circularimages/yupptv.png", "catLink" to "https://tv.noobon.top/playlist/yapp.php"),
        mapOf("id" to 153, "title" to "DANGAL TV", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTMnDuHwjd3WEFHmObqo53bAjkIB6E7JvIIPjGGGxqZzbNsKT85D_sRYNc&s=10", "catLink" to "https://playlist-storage.pages.dev/PLAYLIST/DangalPlay.m3u"),
        mapOf("id" to 157, "title" to "Movies & Series", "image" to "https://i.postimg.cc/QCVVj6D1/Movies.png", "catLink" to "http://tv.noobon.top/playlist/movies.php"),
        mapOf("id" to 158, "title" to "DEKHO 24 X 7", "image" to "https://tstatic.videoready.tv/cms-ui/images/custom-content/1739684250358.png", "catLink" to "https://dehkho24h.alpha-circuit.workers.dev/?token=1b8d9b-796c8a-36e17f-8f83a5"),
        mapOf("id" to 159, "title" to "JIOTV+ S2", "image" to "https://i.ibb.co/VY9ND7rY/image.png", "catLink" to "https://jiotvplus.byte-vault.workers.dev/?token=42e4f5-2d863b-3c37d8-7f3f51"),
        mapOf("id" to 160, "title" to "SONY IND2", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRxsCm4WKugE7ubLr2J3AP7s-hqHl0dh69ImA&usqp=CAU", "catLink" to "https://sonyliv.api-horizon.workers.dev/?token=16702d-6b058f-899eff-6dc294"),
        mapOf("id" to 163, "title" to "JIOHOTSTAR", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSPuz9ekmjh3vEpEc3lYL4nh6Gj7y2CQTswVG-ZCHnIS1foScuwPzuyxic&s=10", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/jstar.m3u"),
        mapOf("id" to 164, "title" to "World Sports", "image" to "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT7iSlqAmYv4wa-7P9aRqiLVniqbUQUtVNmgsf4BxJJqpRKNJVhlVHvFKI&s=10", "catLink" to "https://tv.noobon.top/playlist/daddyhdlive.php")
    )
    
    suspend fun fetchProviders(): List<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(PROVIDERS_URL)
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
