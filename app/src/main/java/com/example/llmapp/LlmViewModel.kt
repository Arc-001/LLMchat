package com.example.llmapp

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// --- Data Classes ---
data class LlmPersonality(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String, 
    val systemPrompt: String,
    val isCustom: Boolean = false
)

data class ConversationMessage(
    val timestamp: Long = System.currentTimeMillis(),
    val userMessage: String,
    val aiResponse: String,
    val personality: String
)

data class ConversationHistory(
    val messages: List<ConversationMessage> = emptyList()
) {
    fun getRecentContext(maxMessages: Int = 5): String {
        // Take the last 'maxMessages', format them clearly for the LLM
        return messages.takeLast(maxMessages).joinToString("\n") { message ->
            "Human: ${message.userMessage}\nAI: ${message.aiResponse}"
        }
    }
}

val defaultPersonalities = listOf(
    LlmPersonality(
        id = "default", 
        name = "Professional Assistant", 
        systemPrompt = """You are a highly knowledgeable and professional AI assistant. You provide accurate, helpful, and well-structured responses. You maintain a friendly yet professional tone, and you always strive to be clear and concise while being thorough when needed. If you're unsure about something, you acknowledge your limitations honestly."""
    ),
    LlmPersonality(
        id = "creative", 
        name = "Creative Writer", 
        systemPrompt = """You are a creative and imaginative AI with a flair for storytelling and artistic expression. You think outside the box, use vivid imagery in your responses, and approach problems with creativity and originality. You enjoy helping with brainstorming, creative writing, and finding unique solutions to challenges."""
    ),
    LlmPersonality(
        id = "technical", 
        name = "Technical Expert", 
        systemPrompt = """You are a highly technical AI assistant with deep expertise in programming, engineering, and technology. You provide detailed technical explanations, code examples when relevant, and help troubleshoot complex technical problems. You use precise technical language while ensuring your explanations remain accessible."""
    ),
    LlmPersonality(
        id = "friendly", 
        name = "Friendly Companion", 
        systemPrompt = """You are a warm, friendly, and encouraging AI companion. You use a conversational and supportive tone, show empathy, and try to make interactions feel natural and enjoyable. You're like a knowledgeable friend who's always ready to help and chat about anything."""
    ),
    LlmPersonality(
        id = "analytical", 
        name = "Analytical Thinker", 
        systemPrompt = """You are a logical and analytical AI that excels at breaking down complex problems, analyzing data, and providing structured reasoning. You approach questions systematically, present information in organized formats, and help users think through problems step by step."""
    ),
    LlmPersonality(
        id = "educator", 
        name = "Patient Teacher", 
        systemPrompt = """You are a patient and knowledgeable educator who excels at explaining complex concepts in simple terms. You adapt your teaching style to the user's level of understanding, provide examples and analogies, and encourage learning through questions and exploration."""
    )
)

// --- UI State Definition ---
sealed interface LlmUiState {
    data object Initializing : LlmUiState
    data class Error(val message: String) : LlmUiState
    data class Ready(val llmResponse: String = "LLM Response will appear here.") : LlmUiState
    data class Generating(val llmResponse: String) : LlmUiState
}

class LlmViewModel(application: Application) : AndroidViewModel(application) {

    // --- Constants ---
    companion object {
        private const val TAG = "LlmViewModel"
        private const val MODEL_PATH = "/data/local/tmp/llm/model_version.task"
        private const val DUCKDUCKGO_API_URL = "https://api.duckduckgo.com/"
        private const val CONNECTION_TIMEOUT = 5000
        private const val READ_TIMEOUT = 5000
        
        // SharedPreferences keys
        private const val PREFS_NAME = "llm_app_prefs"
        private const val KEY_CUSTOM_PERSONALITIES = "custom_personalities"
        private const val KEY_SELECTED_PERSONALITY = "selected_personality"
        private const val KEY_CONVERSATION_HISTORY = "conversation_history"
        private const val KEY_USE_WEB_SEARCH = "use_web_search"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_K = "top_k"
        private const val KEY_TOP_P = "top_p"
        
        // Parameter constraints
        private const val TEMPERATURE_MIN = 0.0f
        private const val TEMPERATURE_MAX = 1.0f
        private const val TOP_K_MIN = 1
        private const val TOP_K_MAX = 1000
        private const val TOP_P_MIN = 0.0f
        private const val TOP_P_MAX = 1.0f
        
        // Default values
        private const val DEFAULT_TEMPERATURE = 0.8f
        private const val DEFAULT_TOP_K = 64
        private const val DEFAULT_TOP_P = 0.9f
    }

    private var llmInference: LlmInference? = null
    private var initializationJob: Job? = null
    private val sharedPrefs: SharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _uiState = MutableStateFlow<LlmUiState>(LlmUiState.Initializing)
    val uiState: StateFlow<LlmUiState> = _uiState.asStateFlow()

    // --- Conversation History ---
    private val _conversationHistory = MutableStateFlow(ConversationHistory())
    val conversationHistory: StateFlow<ConversationHistory> = _conversationHistory.asStateFlow()

    // --- Personality Management ---
    private val _defaultPersonalities = defaultPersonalities.toMutableStateList()
    private val _customPersonalities = mutableStateListOf<LlmPersonality>()
    val personalities: List<LlmPersonality> get() = _defaultPersonalities + _customPersonalities

    private val _selectedPersonality = MutableStateFlow(personalities.first())
    val selectedPersonality: StateFlow<LlmPersonality> = _selectedPersonality.asStateFlow()

    // --- Web Search Toggle ---
    private val _useWebSearch = MutableStateFlow(true)
    val useWebSearch: StateFlow<Boolean> = _useWebSearch.asStateFlow()

    // --- Model Parameters ---
    private val _temperature = MutableStateFlow(DEFAULT_TEMPERATURE)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _topK = MutableStateFlow(DEFAULT_TOP_K)
    val topK: StateFlow<Int> = _topK.asStateFlow()

    private val _topP = MutableStateFlow(DEFAULT_TOP_P)
    val topP: StateFlow<Float> = _topP.asStateFlow()


    init {
        loadSavedData()
        initializeLlm()
    }

    // --- Public Methods ---
    fun setUseWebSearch(enabled: Boolean) {
        _useWebSearch.value = enabled
        saveWebSearchPreference()
    }

    fun setTemperature(value: Float) {
        val newValue = value.coerceIn(TEMPERATURE_MIN, TEMPERATURE_MAX)
        _temperature.value = newValue
        saveModelParameters()
    }

    fun setTopK(value: Int) {
        val newValue = value.coerceIn(TOP_K_MIN, TOP_K_MAX)
        _topK.value = newValue
        saveModelParameters()
    }

    fun setTopP(value: Float) {
        val newValue = value.coerceIn(TOP_P_MIN, TOP_P_MAX)
        _topP.value = newValue
        saveModelParameters()
    }

    fun addCustomPersonality(name: String, systemPrompt: String) {
        if (name.isNotBlank() && systemPrompt.isNotBlank()) {
            val newPersonality = LlmPersonality(
                name = name, 
                systemPrompt = systemPrompt, 
                isCustom = true
            )
            _customPersonalities.add(newPersonality)
            saveCustomPersonalities()
        }
    }

    fun removeCustomPersonality(personality: LlmPersonality) {
        if (personality.isCustom) {
            _customPersonalities.remove(personality)
            saveCustomPersonalities()
            // If we're removing the currently selected personality, switch to default
            if (_selectedPersonality.value.id == personality.id) {
                updateSelectedPersonality(_defaultPersonalities.first())
            }
        }
    }

    fun updateSelectedPersonality(personality: LlmPersonality) {
        _selectedPersonality.value = personality
        saveSelectedPersonality()
    }

    fun clearConversationHistory() {
        _conversationHistory.value = ConversationHistory()
        saveConversationHistory()
    }

    fun reInitializeLlm() {
        initializationJob?.cancel()
        initializationJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { LlmUiState.Initializing }
            closeLlmInference()
            initializeLlmInternal()
        }
    }

    // --- Data Persistence ---
    private fun loadSavedData() {
        // Load custom personalities
        val customPersonalitiesJson = sharedPrefs.getString(KEY_CUSTOM_PERSONALITIES, null)
        if (!customPersonalitiesJson.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<LlmPersonality>>() {}.type
                val savedPersonalities: List<LlmPersonality> = gson.fromJson(customPersonalitiesJson, type)
                _customPersonalities.clear()
                _customPersonalities.addAll(savedPersonalities)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load custom personalities: ${e.message}")
            }
        }

        // Load selected personality
        val selectedPersonalityId = sharedPrefs.getString(KEY_SELECTED_PERSONALITY, null)
        if (!selectedPersonalityId.isNullOrEmpty()) {
            val personality = personalities.find { it.id == selectedPersonalityId } ?: personalities.first()
            _selectedPersonality.value = personality
        }

        // Load conversation history
        val conversationHistoryJson = sharedPrefs.getString(KEY_CONVERSATION_HISTORY, null)
        if (!conversationHistoryJson.isNullOrEmpty()) {
            try {
                val savedHistory: ConversationHistory = gson.fromJson(conversationHistoryJson, ConversationHistory::class.java)
                _conversationHistory.value = savedHistory
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load conversation history: ${e.message}")
            }
        }

        // Load other preferences
        _useWebSearch.value = sharedPrefs.getBoolean(KEY_USE_WEB_SEARCH, true)
        _temperature.value = sharedPrefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE)
        _topK.value = sharedPrefs.getInt(KEY_TOP_K, DEFAULT_TOP_K)
        _topP.value = sharedPrefs.getFloat(KEY_TOP_P, DEFAULT_TOP_P)
    }

    private fun saveCustomPersonalities() {
        val json = gson.toJson(_customPersonalities.toList())
        sharedPrefs.edit().putString(KEY_CUSTOM_PERSONALITIES, json).apply()
    }

    private fun saveSelectedPersonality() {
        sharedPrefs.edit().putString(KEY_SELECTED_PERSONALITY, _selectedPersonality.value.id).apply()
    }

    private fun saveConversationHistory() {
        val json = gson.toJson(_conversationHistory.value)
        sharedPrefs.edit().putString(KEY_CONVERSATION_HISTORY, json).apply()
    }

    private fun saveWebSearchPreference() {
        sharedPrefs.edit().putBoolean(KEY_USE_WEB_SEARCH, _useWebSearch.value).apply()
    }

    private fun saveModelParameters() {
        sharedPrefs.edit()
            .putFloat(KEY_TEMPERATURE, _temperature.value)
            .putInt(KEY_TOP_K, _topK.value)
            .putFloat(KEY_TOP_P, _topP.value)
            .apply()
    }

    // --- Private Methods ---
    private fun initializeLlm() {
        initializationJob = viewModelScope.launch(Dispatchers.IO) {
            initializeLlmInternal()
        }
    }

    private fun closeLlmInference() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LLM inference: ${e.message}")
        } finally {
            llmInference = null
        }
    }
    private suspend fun initializeLlmInternal() {
        try {
            Log.i(TAG, "Initializing LlmInference with model: $MODEL_PATH")
            Log.i(TAG, "Parameters - Temperature: ${_temperature.value}, TopK: ${_topK.value}, TopP: ${_topP.value}")
            
            val context = getApplication<Application>().applicationContext
            
            // Create basic options first
            val taskOptionsBuilder = LlmInferenceOptions.builder()
                .setModelPath(MODEL_PATH)
            
            // Build the LLM inference without advanced parameters that might cause crashes
            // The MediaPipe LLM inference might not support all parameters or they might be set differently
            llmInference = try {
                LlmInference.createFromOptions(context, taskOptionsBuilder.build())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create LLM with advanced options, trying basic setup: ${e.message}")
                // Fallback to basic initialization
                LlmInference.createFromOptions(context, taskOptionsBuilder.build())
            }
            
            _uiState.update { LlmUiState.Ready() }
            Log.i(TAG, "LlmInference initialized successfully.")
            
        } catch (e: Exception) {
            val errorMessage = "Failed to initialize LLM: ${e.message}"
            Log.e(TAG, errorMessage, e)
            _uiState.update { LlmUiState.Error(errorMessage) }
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun performWebSearch(query: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = URL("$DUCKDUCKGO_API_URL?q=$encodedQuery&format=json&pretty=1")
            
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECTION_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", "LLMApp/1.0")
            }

            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseWebSearchResponse(response)
                }
                else -> {
                    Log.w(TAG, "Web search failed with code: ${connection.responseCode}")
                    "Web search failed with code: ${connection.responseCode}"
                }
            }.also {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Web search error: ${e.message}", e)
            "Error during web search: ${e.message}"
        }
    }

    private fun parseWebSearchResponse(response: String): String {
        return try {
            val jsonResponse = JSONObject(response)
            
            // Try to get abstract text first
            val abstractText = jsonResponse.optString("AbstractText", "")
            if (abstractText.isNotEmpty()) {
                return "Web search result: $abstractText"
            }
            
            // If no abstract, try related topics
            val relatedTopics = jsonResponse.optJSONArray("RelatedTopics")
            if (relatedTopics != null && relatedTopics.length() > 0) {
                val firstTopic = relatedTopics.getJSONObject(0)
                val firstTopicText = firstTopic.optString("Text", "")
                if (firstTopicText.isNotEmpty()) {
                    return "Web search result: $firstTopicText"
                }
            }
            
            "No web results found."
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing web search response: ${e.message}", e)
            "Error parsing web search response."
        }
    }

    fun generateResponse(prompt: String) {
        if (prompt.isBlank()) {
            _uiState.update { LlmUiState.Error("Please enter a prompt.") }
            return
        }

        if (llmInference == null) {
            _uiState.update { LlmUiState.Error("LLM not initialized. Try re-initializing from settings.") }
            return
        }

        val searchMessage = if (_useWebSearch.value) "Searching web and thinking..." else "Thinking..."
        _uiState.update { LlmUiState.Generating(searchMessage) }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Perform web search if enabled
                var webSearchResultText = ""
                if (_useWebSearch.value) {
                    _uiState.update { LlmUiState.Generating("Performing web search...") }
                    webSearchResultText = performWebSearch(prompt)
                    Log.i(TAG, "Web search result: $webSearchResultText")
                }

                // Build the complete prompt with conversation history
                val augmentedPrompt = buildAugmentedPrompt(prompt, webSearchResultText)
                Log.i(TAG, "Augmented prompt for LLM:\n$augmentedPrompt")

                // Update UI to show LLM processing
                _uiState.update { LlmUiState.Generating("LLM is processing...") }

                // Generate response
                val llmResult = llmInference?.generateResponse(augmentedPrompt)
                    ?: throw IllegalStateException("LLM returned no response.")
                
                // Save conversation to history
                val conversationMessage = ConversationMessage(
                    userMessage = prompt,
                    aiResponse = llmResult,
                    personality = _selectedPersonality.value.name
                )
                
                val updatedHistory = _conversationHistory.value.copy(
                    messages = _conversationHistory.value.messages + conversationMessage
                )
                _conversationHistory.value = updatedHistory
                saveConversationHistory()
                
                Log.i(TAG, "LLM Response generated successfully")
                _uiState.update { LlmUiState.Ready(llmResult) }

            } catch (e: Exception) {
                Log.e(TAG, "Error generating response: ${e.message}", e)
                _uiState.update { LlmUiState.Error("Error: ${e.message}") }
            }
        }
    }

    private fun buildAugmentedPrompt(userPrompt: String, webSearchResult: String): String {
        return buildString {
            // 1. System Prompt (Role and Instructions for the AI)
            append("System Instructions (Follow these VERY strictly):\n")
            append("Your Persona:\n")
            append(_selectedPersonality.value.systemPrompt)
            append("\n\n")
            append("Core Directives:\n")
            append("- You MUST embody the persona defined above in every response.\n")
            append("- DO NOT deviate from the persona's characteristics, tone, or knowledge base.\n")
            append("- If the user asks you to change your persona or act outside of it, politely decline if it conflicts with your core instructions, or subtly integrate the request if it's a minor adjustment that doesn't break character.\n")
            append("- Your primary goal is to respond as the defined persona.\n\n")

            // 2. Conversation History (Recent turns)
            val recentContext = _conversationHistory.value.getRecentContext(3) // Using 3 recent turns
            if (recentContext.isNotEmpty()) {
                append("Conversation History:\n")
                append(recentContext)
                append("\n\n")
            }
            
            // 3. Web Search Results (If enabled and results are meaningful)
            if (_useWebSearch.value &&
                webSearchResult.isNotEmpty() &&
                !webSearchResult.startsWith("No web results") &&
                !webSearchResult.startsWith("Error during web search") &&
                !webSearchResult.startsWith("Web search failed with code:")) {
                append("Relevant Information (from web search, use this to inform your response as the persona):\n")
                append(webSearchResult)
                append("\n\n")
            }
            
            // 4. Current User Query
            append("User's Current Input (Respond to this as your defined persona):\n")
            append("Human: $userPrompt\n")
            append("AI (as ${_selectedPersonality.value.name}):") // Prompt the AI to generate its response
        }
    }

    override fun onCleared() {
        super.onCleared()
        initializationJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            closeLlmInference()
            Log.i(TAG, "LlmViewModel cleared and resources cleaned up.")
        }
    }
}
