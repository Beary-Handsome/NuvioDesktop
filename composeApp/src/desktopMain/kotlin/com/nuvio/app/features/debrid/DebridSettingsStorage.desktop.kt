package com.nuvio.app.features.debrid

import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncInt
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncInt
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.desktop.DesktopPreferences
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual object DebridSettingsStorage {
    private const val preferencesName = "nuvio_debrid_settings"
    private const val enabledKey = "enabled"
    private const val cloudLibraryEnabledKey = "cloud_library_enabled"
    private const val preferredResolverProviderIdKey = "preferred_resolver_provider_id"
    private const val torboxApiKeyKey = "torbox_api_key"
    private const val realDebridApiKeyKey = "real_debrid_api_key"
    private const val premiumizeApiKeyKey = "provider_api_key_premiumize"
    private const val alldebridApiKeyKey = "provider_api_key_alldebrid"
    private const val easynewsApiKeyKey = "provider_api_key_easynews"
    private const val instantPlaybackPreparationLimitKey = "instant_playback_preparation_limit"
    private const val streamMaxResultsKey = "stream_max_results"
    private const val streamSortModeKey = "stream_sort_mode"
    private const val streamMinimumQualityKey = "stream_minimum_quality"
    private const val streamDolbyVisionFilterKey = "stream_dolby_vision_filter"
    private const val streamHdrFilterKey = "stream_hdr_filter"
    private const val streamCodecFilterKey = "stream_codec_filter"
    private const val streamPreferencesKey = "stream_preferences"
    private const val streamNameTemplateKey = "stream_name_template"
    private const val streamDescriptionTemplateKey = "stream_description_template"

    private val syncKeys = listOf(
        enabledKey,
        cloudLibraryEnabledKey,
        preferredResolverProviderIdKey,
        torboxApiKeyKey,
        realDebridApiKeyKey,
        premiumizeApiKeyKey,
        alldebridApiKeyKey,
        easynewsApiKeyKey,
        instantPlaybackPreparationLimitKey,
        streamMaxResultsKey,
        streamSortModeKey,
        streamMinimumQualityKey,
        streamDolbyVisionFilterKey,
        streamHdrFilterKey,
        streamCodecFilterKey,
        streamPreferencesKey,
        streamNameTemplateKey,
        streamDescriptionTemplateKey,
    )

    actual fun loadEnabled(): Boolean? =
        DesktopPreferences.getBoolean(preferencesName, enabledKey)

    actual fun saveEnabled(enabled: Boolean) {
        DesktopPreferences.putBoolean(preferencesName, enabledKey, enabled)
    }

    actual fun loadCloudLibraryEnabled(): Boolean? =
        DesktopPreferences.getBoolean(preferencesName, cloudLibraryEnabledKey)

    actual fun saveCloudLibraryEnabled(enabled: Boolean) {
        DesktopPreferences.putBoolean(preferencesName, cloudLibraryEnabledKey, enabled)
    }

    actual fun loadPreferredResolverProviderId(): String? =
        DesktopPreferences.getString(preferencesName, preferredResolverProviderIdKey)

    actual fun savePreferredResolverProviderId(providerId: String) {
        DesktopPreferences.putString(preferencesName, preferredResolverProviderIdKey, providerId)
    }

    actual fun loadProviderApiKey(providerId: String): String? =
        DesktopPreferences.getString(preferencesName, "provider_api_key_$providerId")

    actual fun saveProviderApiKey(providerId: String, apiKey: String) {
        DesktopPreferences.putString(preferencesName, "provider_api_key_$providerId", apiKey)
    }

    actual fun loadTorboxApiKey(): String? =
        DesktopPreferences.getString(preferencesName, torboxApiKeyKey)

    actual fun saveTorboxApiKey(apiKey: String) {
        DesktopPreferences.putString(preferencesName, torboxApiKeyKey, apiKey)
    }

    actual fun loadRealDebridApiKey(): String? =
        DesktopPreferences.getString(preferencesName, realDebridApiKeyKey)

    actual fun saveRealDebridApiKey(apiKey: String) {
        DesktopPreferences.putString(preferencesName, realDebridApiKeyKey, apiKey)
    }

    actual fun loadInstantPlaybackPreparationLimit(): Int? =
        DesktopPreferences.getInt(preferencesName, instantPlaybackPreparationLimitKey)

    actual fun saveInstantPlaybackPreparationLimit(limit: Int) {
        DesktopPreferences.putInt(preferencesName, instantPlaybackPreparationLimitKey, limit)
    }

    actual fun loadStreamMaxResults(): Int? =
        DesktopPreferences.getInt(preferencesName, streamMaxResultsKey)

    actual fun saveStreamMaxResults(maxResults: Int) {
        DesktopPreferences.putInt(preferencesName, streamMaxResultsKey, maxResults)
    }

    actual fun loadStreamSortMode(): String? =
        DesktopPreferences.getString(preferencesName, streamSortModeKey)

    actual fun saveStreamSortMode(mode: String) {
        DesktopPreferences.putString(preferencesName, streamSortModeKey, mode)
    }

    actual fun loadStreamMinimumQuality(): String? =
        DesktopPreferences.getString(preferencesName, streamMinimumQualityKey)

    actual fun saveStreamMinimumQuality(quality: String) {
        DesktopPreferences.putString(preferencesName, streamMinimumQualityKey, quality)
    }

    actual fun loadStreamDolbyVisionFilter(): String? =
        DesktopPreferences.getString(preferencesName, streamDolbyVisionFilterKey)

    actual fun saveStreamDolbyVisionFilter(filter: String) {
        DesktopPreferences.putString(preferencesName, streamDolbyVisionFilterKey, filter)
    }

    actual fun loadStreamHdrFilter(): String? =
        DesktopPreferences.getString(preferencesName, streamHdrFilterKey)

    actual fun saveStreamHdrFilter(filter: String) {
        DesktopPreferences.putString(preferencesName, streamHdrFilterKey, filter)
    }

    actual fun loadStreamCodecFilter(): String? =
        DesktopPreferences.getString(preferencesName, streamCodecFilterKey)

    actual fun saveStreamCodecFilter(filter: String) {
        DesktopPreferences.putString(preferencesName, streamCodecFilterKey, filter)
    }

    actual fun loadStreamPreferences(): String? =
        DesktopPreferences.getString(preferencesName, streamPreferencesKey)

    actual fun saveStreamPreferences(preferences: String) {
        DesktopPreferences.putString(preferencesName, streamPreferencesKey, preferences)
    }

    actual fun loadStreamNameTemplate(): String? =
        DesktopPreferences.getString(preferencesName, streamNameTemplateKey)

    actual fun saveStreamNameTemplate(template: String) {
        DesktopPreferences.putString(preferencesName, streamNameTemplateKey, template)
    }

    actual fun loadStreamDescriptionTemplate(): String? =
        DesktopPreferences.getString(preferencesName, streamDescriptionTemplateKey)

    actual fun saveStreamDescriptionTemplate(template: String) {
        DesktopPreferences.putString(preferencesName, streamDescriptionTemplateKey, template)
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadEnabled()?.let { put(enabledKey, encodeSyncBoolean(it)) }
        loadCloudLibraryEnabled()?.let { put(cloudLibraryEnabledKey, encodeSyncBoolean(it)) }
        loadPreferredResolverProviderId()?.let { put(preferredResolverProviderIdKey, encodeSyncString(it)) }
        loadTorboxApiKey()?.let { put(torboxApiKeyKey, encodeSyncString(it)) }
        loadRealDebridApiKey()?.let { put(realDebridApiKeyKey, encodeSyncString(it)) }
        loadProviderApiKey("premiumize")?.let { put(premiumizeApiKeyKey, encodeSyncString(it)) }
        loadProviderApiKey("alldebrid")?.let { put(alldebridApiKeyKey, encodeSyncString(it)) }
        loadProviderApiKey("easynews")?.let { put(easynewsApiKeyKey, encodeSyncString(it)) }
        loadInstantPlaybackPreparationLimit()?.let { put(instantPlaybackPreparationLimitKey, encodeSyncInt(it)) }
        loadStreamMaxResults()?.let { put(streamMaxResultsKey, encodeSyncInt(it)) }
        loadStreamSortMode()?.let { put(streamSortModeKey, encodeSyncString(it)) }
        loadStreamMinimumQuality()?.let { put(streamMinimumQualityKey, encodeSyncString(it)) }
        loadStreamDolbyVisionFilter()?.let { put(streamDolbyVisionFilterKey, encodeSyncString(it)) }
        loadStreamHdrFilter()?.let { put(streamHdrFilterKey, encodeSyncString(it)) }
        loadStreamCodecFilter()?.let { put(streamCodecFilterKey, encodeSyncString(it)) }
        loadStreamPreferences()?.let { put(streamPreferencesKey, encodeSyncString(it)) }
        loadStreamNameTemplate()?.let { put(streamNameTemplateKey, encodeSyncString(it)) }
        loadStreamDescriptionTemplate()?.let { put(streamDescriptionTemplateKey, encodeSyncString(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        syncKeys.forEach { DesktopPreferences.remove(preferencesName, it) }

        payload.decodeSyncBoolean(enabledKey)?.let(::saveEnabled)
        payload.decodeSyncBoolean(cloudLibraryEnabledKey)?.let(::saveCloudLibraryEnabled)
        payload.decodeSyncString(preferredResolverProviderIdKey)?.let(::savePreferredResolverProviderId)
        payload.decodeSyncString(torboxApiKeyKey)?.let(::saveTorboxApiKey)
        payload.decodeSyncString(realDebridApiKeyKey)?.let(::saveRealDebridApiKey)
        payload.decodeSyncString(premiumizeApiKeyKey)?.let { saveProviderApiKey("premiumize", it) }
        payload.decodeSyncString(alldebridApiKeyKey)?.let { saveProviderApiKey("alldebrid", it) }
        payload.decodeSyncString(easynewsApiKeyKey)?.let { saveProviderApiKey("easynews", it) }
        payload.decodeSyncInt(instantPlaybackPreparationLimitKey)?.let(::saveInstantPlaybackPreparationLimit)
        payload.decodeSyncInt(streamMaxResultsKey)?.let(::saveStreamMaxResults)
        payload.decodeSyncString(streamSortModeKey)?.let(::saveStreamSortMode)
        payload.decodeSyncString(streamMinimumQualityKey)?.let(::saveStreamMinimumQuality)
        payload.decodeSyncString(streamDolbyVisionFilterKey)?.let(::saveStreamDolbyVisionFilter)
        payload.decodeSyncString(streamHdrFilterKey)?.let(::saveStreamHdrFilter)
        payload.decodeSyncString(streamCodecFilterKey)?.let(::saveStreamCodecFilter)
        payload.decodeSyncString(streamPreferencesKey)?.let(::saveStreamPreferences)
        payload.decodeSyncString(streamNameTemplateKey)?.let(::saveStreamNameTemplate)
        payload.decodeSyncString(streamDescriptionTemplateKey)?.let(::saveStreamDescriptionTemplate)
    }
}
