package com.cncverse

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class CricifyPlugin: BasePlugin() {
    override fun load() {
        // Register multiple IPTV providers with different parameters
        val iptvProviders = listOf(
            mapOf("id" to 13, "title" to "TATA PLAY", "catLink" to "https://hotstar-live.developed-for-cricfy.workers.dev/?token=240bb9-374e2e-3c13f0-4a6c75"),
            mapOf("id" to 14, "title" to "HOTSTAR", "catLink" to "https://hotstar-live-event.developed-for-jiohotstar-live-event.workers.dev/?token=154dc5-7a9126-56996d-1fa267"),
            // mapOf("id" to 15, "title" to "TOFFEE", "catLink" to "https://tv.noobon.top/playlist/toffee.php"),
            mapOf("id" to 17, "title" to "T SPORTS", "catLink" to "https://fifabangladesh2-xyz-ekkj.spidy.online/AYN/tsports.m3u"),
            mapOf("id" to 18, "title" to "FANCODE IND", "catLink" to "https://raw.githubusercontent.com/Jitendraunatti/fancode/refs/heads/main/data/fancode.m3u"),
            mapOf("id" to 19, "title" to "SONYLIV", "catLink" to "https://play.yuvraj.news/extra/sliv-live-event.php"),
            mapOf("id" to 20, "title" to "JIO BD", "catLink" to "https://tv.noobon.top/playlist/jio/jio.php"),
            mapOf("id" to 22, "title" to "JIO IND", "catLink" to "https://jiotv.developed-for-temp.workers.dev/?token=42e4f5-2d863b-3c37d8-7f3f49"),
            mapOf("id" to 29, "title" to "SONY BD", "catLink" to "https://bdix.short.gy/sonybd/playlist.m3u"),
            mapOf("id" to 31, "title" to "SONY IND", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/main/sliv.m3u"),
            mapOf("id" to 48, "title" to "SUN DIRECT", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/suntv.m3u"),
            mapOf("id" to 70, "title" to "VOOT BD", "catLink" to "https://ranapk.short.gy/VOOTBD.m3u"),
            mapOf("id" to 71, "title" to "VOOT IND", "catLink" to "https://jiocinema-live.developed-for-temp.workers.dev/?token=1b8d9b-796c8a-36e17f-8f83a5"),
            mapOf("id" to 85, "title" to "SUN NXT", "catLink" to "https://raw.githubusercontent.com/alexandermail371/cricfytv/refs/heads/main/sunxt.m3u"),
            mapOf("id" to 90, "title" to "AIRTEL IND", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/main/artl.m3u"),
            mapOf("id" to 92, "title" to "DISTRO TV", "catLink" to "https://playlist-storage.pages.dev/PLAYLIST/DistroTV.m3u"),
            mapOf("id" to 104, "title" to "ZEE5", "catLink" to "https://zee5.developed-for-temp.workers.dev/?token=16702d-6b058f-899eff-6dc294"),
            mapOf("id" to 106, "title" to "JIOTV+", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/jtv.m3u"),
            mapOf("id" to 110, "title" to "JIOLIVE IND", "catLink" to "https://raw.githubusercontent.com/alex4528/jc_live/refs/heads/main/jevents_live.m3u"),
            mapOf("id" to 114, "title" to "TAPMAD PK", "catLink" to "https://tv.noobon.top/playlist/tapmad.php"),
            mapOf("id" to 117, "title" to "ZEE5 IN", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/z5.m3u"),
            mapOf("id" to 126, "title" to "WORLD TV", "catLink" to "https://ranapk.short.gy/WORLDTV1.m3u8"),
            mapOf("id" to 129, "title" to "AYNA", "catLink" to "https://tv.noobon.top/playlist/aynaott.php"),
            mapOf("id" to 130, "title" to "JIO CINEMA IND", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/jcinema.m3u"),
            mapOf("id" to 131, "title" to "DISH TV", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/dishtv.m3u"),
            mapOf("id" to 132, "title" to "SHOOQ PK", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/shoq.m3u"),
            mapOf("id" to 133, "title" to "SAMSUNG TV", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/samsungtv.m3u"),
            mapOf("id" to 134, "title" to "JAGOBD", "catLink" to "https://tv.noobon.top/playlist/jagobd.php"),
            mapOf("id" to 135, "title" to "JADOO", "catLink" to "https://fifabangladesh.site/PLAYLIST/jadoo.php"),
            mapOf("id" to 136, "title" to "PISHOW", "catLink" to "http://playlist-storage.pages.dev/PLAYLIST/playboxtv.m3u"),
            // mapOf("id" to 140, "title" to "TOFFEE IND", "catLink" to "https://noob-cricfy.iptvbd.online/toffee-ww.m3u"),
            mapOf("id" to 146, "title" to "CRICHD", "catLink" to "https://tv.noobon.top/crichd/playlist.php"),
            mapOf("id" to 150, "title" to "ZAP SPORTS", "catLink" to "https://tv.noobon.top/zapx/api.php?action=getIPTVPlaylist"),
            mapOf("id" to 151, "title" to "Pirates TV", "catLink" to "https://raw.githubusercontent.com/FunctionError/PiratesTv/refs/heads/main/combined_playlist.m3u"),
            mapOf("id" to 152, "title" to "YUPPTV", "catLink" to "https://tv.noobon.top/playlist/yapp.php"),
            mapOf("id" to 153, "title" to "DANGAL TV", "catLink" to "https://playlist-storage.pages.dev/PLAYLIST/DangalPlay.m3u"),
            mapOf("id" to 154, "title" to "BDIX TV", "catLink" to "https://tv.noobon.top/playlist/bdixtv.m3u"),
            mapOf("id" to 157, "title" to "Movies & Series", "catLink" to "http://tv.noobon.top/playlist/movies.php"),
            mapOf("id" to 158, "title" to "DEKHO 24 X 7", "catLink" to "https://dehkho24h.developed-for-jiohotstar-live-event.workers.dev/?token=1b8d9b-796c8a-36e17f-8f83a5"),
            mapOf("id" to 159, "title" to "JIOTV+ S2", "catLink" to "https://jiotvplus.developed-for-scarlet-witch.workers.dev?token=42e4f5-2d863b-3c37d8-7f3f49"),
            mapOf("id" to 160, "title" to "SONY IND2", "catLink" to "https://sonyliv.developed-for-cricfy.workers.dev/?token=16702d-6b058f-899eff-6dc294"),
            // mapOf("id" to 161, "title" to "AKASH GO", "catLink" to "https://tv.noobon.top/playlist/akashgobd.php"),
            mapOf("id" to 162, "title" to "FANCODE BD", "catLink" to "https://tv.noobon.top/playlist/fancode.php"),
            mapOf("id" to 163, "title" to "JIOHOTSTAR", "catLink" to "https://raw.githubusercontent.com/alex4528/m3u/refs/heads/main/jstar.m3u"),
            mapOf("id" to 163, "title" to "OPPLX", "catLink" to "https://ranapk.short.gy/TGmx@RANAPKX73/OPPLEXTV.m3u")
        )
        
        // Register each provider
        iptvProviders.forEach { provider ->
            registerMainAPI(Cricify(
                customName = provider["title"] as String,
                customMainUrl = provider["catLink"] as String
            ))
        }
    }
}