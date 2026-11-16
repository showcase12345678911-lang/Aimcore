package com.aimcore

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small SharedPreferences/JSON store; uses only Android platform APIs. */
class AimcoreStore(context: Context) {
    private val preferences = context.getSharedPreferences("aimcore_phase2", Context.MODE_PRIVATE)

    fun startingSensitivity(): Double {
        val settings = readSettings()
        return settings.optDouble("starting_sensitivity_deg_per_dp", 0.15).takeIf { it.isFinite() && it > 0.0 } ?: 0.15
    }

    fun saveStartingSensitivity(value: Double) {
        val settings = readSettings().put("starting_sensitivity_deg_per_dp", value)
        preferences.edit().putString("settings_json", settings.toString()).apply()
    }

    fun calculatorDpi(): Double = readSettings().optDouble("calculator_dpi", 800.0)
    fun calculatorSensitivity(): Double = readSettings().optDouble("calculator_sensitivity", 1.0)

    fun saveCalculatorSettings(dpi: Double, sensitivity: Double) {
        val settings = readSettings()
            .put("calculator_dpi", dpi)
            .put("calculator_sensitivity", sensitivity)
        preferences.edit().putString("settings_json", settings.toString()).apply()
    }

    fun saveCounterflameSensitivity(value: Double) {
        val profiles = readProfiles()
        val profile = profiles.optJSONObject("counterflame") ?: JSONObject()
        profile.put("name", "Counterflame")
        profile.put("sensitivity_deg_per_dp", value)
        profiles.put("counterflame", profile)
        preferences.edit().putString("profiles_json", profiles.toString()).apply()
    }

    fun counterflameSensitivity(): Double? {
        val profile = readProfiles().optJSONObject("counterflame") ?: return null
        if (!profile.has("sensitivity_deg_per_dp")) return null
        return profile.optDouble("sensitivity_deg_per_dp", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
    }

    fun profileNames(): List<String> {
        val profiles = readProfiles()
        val names = mutableListOf("Counterflame")
        val iterator = profiles.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val name = profiles.optJSONObject(key)?.optString("name").orEmpty()
            if (name.isNotBlank() && name !in names) names.add(name)
        }
        return names
    }

    fun saveFinderResult(result: FinderResult) {
        val history = readHistory()
        val item = JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("recommended_sens", result.recommendedSensitivity)
            .put("score", result.score)
            .put("confidence", result.confidence)
            .put("tendency", result.tendency)
            .put("accuracy", result.metrics.accuracyPercent)
            .put("hits", result.metrics.hits)
            .put("shots", result.metrics.shots)
            .put("time_to_target_ms", result.metrics.timeToTargetMs)
            .put("overshoots", result.metrics.overshoots)
            .put("undershoots", result.metrics.undershoots)
            .put("path_efficiency", result.metrics.pathEfficiency)
            .put("time_on_target", result.metrics.timeOnTargetPercent)
        history.put(item)
        while (history.length() > 50) history.remove(0)
        preferences.edit().putString("finder_history_json", history.toString()).apply()
    }

    fun historyCount(): Int = readHistory().length()

    private fun readSettings(): JSONObject = try {
        JSONObject(preferences.getString("settings_json", "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun readProfiles(): JSONObject = try {
        JSONObject(preferences.getString("profiles_json", "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun readHistory(): JSONArray = try {
        JSONArray(preferences.getString("finder_history_json", "[]") ?: "[]")
    } catch (_: Exception) {
        JSONArray()
    }
}
