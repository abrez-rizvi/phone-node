package com.androidbot.node

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.androidbot.node.agent.AgentEngine
import com.androidbot.node.agent.GroqClient
import com.androidbot.node.server.KtorServerManager
import com.androidbot.node.storage.StorageManager

class AndroidBotApp : Application() {

    lateinit var storageManager: StorageManager
        private set
    lateinit var groqClient: GroqClient
        private set
    lateinit var agentEngine: AgentEngine
        private set
    lateinit var ktorServerManager: KtorServerManager
        private set

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("androidbot_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        storageManager = StorageManager(this)
        groqClient = GroqClient { getGroqApiKey() }
        agentEngine = AgentEngine(storageManager, groqClient)
        ktorServerManager = KtorServerManager(this, storageManager, agentEngine)
    }

    fun getGroqApiKey(): String {
        return prefs.getString("groq_api_key", "") ?: ""
    }

    fun setGroqApiKey(key: String) {
        prefs.edit().putString("groq_api_key", key).apply()
    }

    fun getTunnelUrl(): String {
        return prefs.getString("tunnel_url", DEFAULT_TUNNEL_URL) ?: DEFAULT_TUNNEL_URL
    }

    fun setTunnelUrl(url: String) {
        prefs.edit().putString("tunnel_url", url).apply()
    }

    companion object {
        const val DEFAULT_TUNNEL_URL = "https://used-arnold-authentication-format.trycloudflare.com"

        lateinit var instance: AndroidBotApp
            private set
    }
}
