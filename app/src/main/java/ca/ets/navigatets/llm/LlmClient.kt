package ca.ets.navigatets.llm

import android.util.Log
import ca.ets.navigatets.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Client léger pour extraire une intention structurée à partir d'une commande vocale
 * via OpenAI Chat Completions + Function Calling.
 */
class LlmClient(private val okHttp: OkHttpClient = OkHttpClient()) {

    data class IntentResult(
        val intent: String, // NAVIGATE_TO_POI | CHECK_CHAIR_AVAILABILITY | UNKNOWN
        val poiName: String? = null
    )

    private val endpoint = "https://api.openai.com/v1/chat/completions"
    private val model = "gpt-4o-mini"
    private val json = "application/json; charset=utf-8".toMediaType()

    fun extractIntentAsync(utterance: String, poiNames: List<String>?, callback: (IntentResult?) -> Unit) {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            Log.w("LlmClient", "OPENAI_API_KEY is blank. Skipping LLM call.")
            callback(null)
            return
        }

        val systemPrompt = """
            Tu es un NLU qui extrait une intention structurée depuis une phrase utilisateur.
            Renvoie une invocation d'outil avec les champs: intent (NAVIGATE_TO_POI | CHECK_CHAIR_AVAILABILITY | UNKNOWN), poiName.
            Contraintes:
            - Si l'utilisateur demande si une chaise est libre/occupée ou s'il peut s'asseoir ici, intent=CHECK_CHAIR_AVAILABILITY.
            - Si l'utilisateur veut aller à un lieu, intent=NAVIGATE_TO_POI et poiName doit être exactement parmi la liste fournie (sinon laisser vide et intent=UNKNOWN).
            - Sinon, intent=UNKNOWN.
        """.trimIndent()

        val poiListStr = poiNames?.joinToString(", ") ?: ""
        val userPrompt = """
            Utterance: "$utterance"
            POIs: [$poiListStr]
        """.trimIndent()

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userPrompt))

        val tools = JSONArray().put(
            JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", "set_intent")
                    .put("description", "Répondre avec l'intention détectée et un nom de POI optionnel")
                    .put("parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject()
                            .put("intent", JSONObject()
                                .put("type", "string")
                                .put("enum", JSONArray(listOf("NAVIGATE_TO_POI", "CHECK_CHAIR_AVAILABILITY", "UNKNOWN")))
                            )
                            .put("poiName", JSONObject().put("type", "string").put("nullable", true))
                        )
                        .put("required", JSONArray(listOf("intent")))
                    )
                )
        )

        val root = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("tools", tools)
            .put("tool_choice", "auto")

        val reqBody = root.toString().toRequestBody(json)
        val req = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(reqBody)
            .build()
        val startTime = System.currentTimeMillis()

        okHttp.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LlmClient", "HTTP error", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val latency = System.currentTimeMillis() - startTime
                    Log.d("LlmClient", "LLM latency: ${latency}ms")
                    if (!it.isSuccessful) {
                        Log.w("LlmClient", "Bad response: ${it.code}")
                        callback(null)
                        return
                    }
                    val body = it.body?.string().orEmpty()
                    try {
                        val jo = JSONObject(body)
                        val choices = jo.optJSONArray("choices")
                        if (choices == null || choices.length() == 0) { callback(null); return }
                        val msg = choices.getJSONObject(0).getJSONObject("message")
                        val toolCalls = msg.optJSONArray("tool_calls")
                        if (toolCalls != null && toolCalls.length() > 0) {
                            val first = toolCalls.getJSONObject(0)
                            val f = first.optJSONObject("function")
                            val argsStr = f?.optString("arguments") ?: "{}"
                            val args = JSONObject(argsStr)
                            val intent = args.optString("intent", "UNKNOWN")
                            val poi = args.optString("poiName", null)
                            callback(IntentResult(intent = intent, poiName = poi))
                        } else {
                            callback(null)
                        }
                    } catch (t: Throwable) {
                        Log.e("LlmClient", "Parse error", t)
                        callback(null)
                    }
                }
            }
        })
    }
}
