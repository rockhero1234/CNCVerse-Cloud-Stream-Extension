package com.cncverse

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@CloudstreamPlugin
class Xon : Plugin() {
    override fun load(context: Context) {
        val provider = XonProvider()
        registerMainAPI(provider)

        // Launch a coroutine in the background
        GlobalScope.launch {
            provider.refreshCache()
        }
    }
}
