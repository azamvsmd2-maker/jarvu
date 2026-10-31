package com.interview.assistant.data

import android.content.Context
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class InterviewRepository(context: Context) {

    private val prefs = context.getSharedPreferences("interview_prefs", Context.MODE_PRIVATE)
    private var chatSession: Chat? = null

    init {
        val savedKey = getApiKey()
        if (savedKey.isNotBlank()) {
            initModel(savedKey)
        }
    }

    fun getApiKey(): String = prefs.getString("gemini_api_key", "") ?: ""

    fun saveApiKey(apiKey: String) {
        val cleanKey = apiKey.trim()
        prefs.edit().putString("gemini_api_key", cleanKey).apply()
        if (cleanKey.isNotBlank()) {
            initModel(cleanKey)
        } else {
            chatSession = null
        }
    }

    private fun initModel(apiKey: String) {
        val model = GenerativeModel(
            modelName = "gemini-3.8-flash",
            apiKey = apiKey,
            systemInstruction = content { text(InterviewPromptConfig.SYSTEM_INSTRUCTION) }
        )
        chatSession = model.startChat()
    }

    fun streamAnswer(question: String): Flow<String> {
        val session = chatSession ?: return flow {
            emit("⚠️ Please tap the **KEY** button at the top right to set your Gemini API Key first.")
        }
        return session.sendMessageStream(question).map { response ->
            response.text ?: ""
        }
    }
}
