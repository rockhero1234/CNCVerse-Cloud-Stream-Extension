package com.cncverse

import android.content.Context
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.amap
import java.io.File

@CloudstreamPlugin
class UltimaPlugin : Plugin() {
    var activity: AppCompatActivity? = null

    companion object {
        inline fun Handler.postFunction(crossinline function: () -> Unit) {
            this.post(
                    object : Runnable {
                        override fun run() {
                            function()
                        }
                    }
            )
        }
    }

    override fun load(context: Context) {
        activity = context as AppCompatActivity
        // All providers should be added in this manner
        registerMainAPI(Ultima(this))

        UltimaStorageManager.currentMetaProviders.forEach { metaProvider ->
            when (metaProvider.first) {
                "Simkl" -> if (metaProvider.second) registerMainAPI(Simkl(this))
                "AniList" -> if (metaProvider.second) registerMainAPI(AniList(this))
                "MyAnimeList" -> if (metaProvider.second) registerMainAPI(MyAnimeList(this))
                "TMDB" -> if (metaProvider.second) registerMainAPI(Tmdb(this))
                "Trakt" -> if (metaProvider.second) registerMainAPI(Trakt(this))
                else -> {}
            }
        }

        openSettings = {
            val frag = UltimaSettings(this)
            frag.show(
                    activity?.supportFragmentManager ?: throw Exception("Unable to open settings"),
                    ""
            )
        }
    }

    fun reload(context: Context?) {
        val pluginData =
            PluginManager.getPluginsOnline().find { it.internalName.contains("Ultima") }
        if (pluginData == null) {
            (PluginManager.getPluginsLocal()).toList().amap { localPlugin ->
                PluginManager.unloadPlugin(localPlugin.filePath)
                PluginManager.loadPlugin(context as AppCompatActivity, File(localPlugin.filePath), localPlugin)
            }
        } else {
            PluginManager.unloadPlugin(pluginData.filePath)
            (PluginManager.getPluginsOnline()).toList().amap { pluginData ->
             PluginManager.loadPlugin(
                context as AppCompatActivity,
                File(pluginData.filePath),
                pluginData
            )
        }
            afterPluginsLoadedEvent.invoke(true)
        }
    }
}
