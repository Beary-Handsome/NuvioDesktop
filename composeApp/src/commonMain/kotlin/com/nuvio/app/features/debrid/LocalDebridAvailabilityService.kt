package com.nuvio.app.features.debrid

import com.nuvio.app.features.streams.AddonStreamGroup
import com.nuvio.app.features.streams.StreamDebridCacheState
import com.nuvio.app.features.streams.StreamDebridCacheStatus
import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object LocalDebridAvailabilityService {
    fun markChecking(
        groups: List<AddonStreamGroup>,
        eligibleGroupIds: Set<String>? = null,
    ): List<AddonStreamGroup> {
        val accounts = cacheCheckAccounts()
        val account = accounts.firstOrNull() ?: return groups
        return groups.updateAvailabilityStatus(eligibleGroupIds) { stream ->
            if (stream.localAvailabilityHash() == null || stream.debridCacheStatus?.state == StreamDebridCacheState.CACHED) {
                stream
            } else {
                stream.copy(
                    debridCacheStatus = StreamDebridCacheStatus(
                        providerId = account.provider.id,
                        providerName = account.provider.displayName,
                        state = StreamDebridCacheState.CHECKING,
                    ),
                )
            }
        }
    }

    suspend fun annotateCachedAvailability(
        groups: List<AddonStreamGroup>,
        eligibleGroupIds: Set<String>? = null,
    ): List<AddonStreamGroup> {
        val accounts = cacheCheckAccounts()
        if (accounts.isEmpty()) return groups
        val settings = DebridSettingsRepository.snapshot()
        val activeProviderId = settings.activeResolverProviderId

        val hashes = groups
            .filter { group -> eligibleGroupIds == null || group.addonId in eligibleGroupIds }
            .flatMap { group ->
                group.streams.mapNotNull { stream ->
                    stream.localAvailabilityHash()
                        ?.takeUnless { stream.debridCacheStatus?.state in FINAL_CACHE_STATES }
                }
            }
            .distinct()
        if (hashes.isEmpty()) return groups

        val allHits = coroutineScope {
            accounts.map { account ->
                async {
                    val result = LocalDebridService.checkCached(account = account, hashes = hashes)
                    result?.mapValues { (_, item) ->
                        CacheHit(
                            providerId = account.provider.id,
                            providerName = account.provider.displayName,
                            item = item,
                        )
                    }.orEmpty()
                }
            }.flatMap { it.await().entries }.associate { it.key to it.value }
        }

        // Prefer hits from the active resolver provider
        val mergedHits = mutableMapOf<String, CacheHit>()
        for (account in accounts) {
            val result = LocalDebridService.checkCached(account = account, hashes = hashes) ?: continue
            for ((hash, item) in result) {
                val hit = CacheHit(account.provider.id, account.provider.displayName, item)
                val existing = mergedHits[hash]
                if (existing == null || (hit.providerId == activeProviderId && existing.providerId != activeProviderId)) {
                    mergedHits[hash] = hit
                }
            }
        }

        if (mergedHits.isEmpty()) {
            val primaryAccount = accounts.first()
            return groups.updateAvailabilityStatus(eligibleGroupIds) { stream ->
                val hash = stream.localAvailabilityHash()
                if (hash == null) {
                    stream
                } else {
                    stream.copy(
                        debridCacheStatus = StreamDebridCacheStatus(
                            providerId = primaryAccount.provider.id,
                            providerName = primaryAccount.provider.displayName,
                            state = StreamDebridCacheState.UNKNOWN,
                        ),
                    )
                }
            }
        }

        return groups.updateAvailabilityStatus(eligibleGroupIds) { stream ->
            val hash = stream.localAvailabilityHash() ?: return@updateAvailabilityStatus stream
            if (stream.debridCacheStatus?.state in FINAL_CACHE_STATES) return@updateAvailabilityStatus stream
            val hit = mergedHits[hash]
            if (hit != null) {
                stream.copy(
                    debridCacheStatus = StreamDebridCacheStatus(
                        providerId = hit.providerId,
                        providerName = hit.providerName,
                        state = StreamDebridCacheState.CACHED,
                        cachedName = hit.item.name,
                        cachedSize = hit.item.size,
                    ),
                )
            } else {
                val primaryAccount = accounts.first()
                stream.copy(
                    debridCacheStatus = StreamDebridCacheStatus(
                        providerId = primaryAccount.provider.id,
                        providerName = primaryAccount.provider.displayName,
                        state = StreamDebridCacheState.NOT_CACHED,
                    ),
                )
            }
        }
    }

    suspend fun isCached(hash: String): Boolean? {
        val accounts = cacheCheckAccounts()
        if (accounts.isEmpty()) return null
        for (account in accounts) {
            val result = LocalDebridService.isCached(account, hash)
            if (result == true) return true
        }
        return false
    }

    private fun cacheCheckAccounts(): List<DebridServiceCredential> {
        val settings = DebridSettingsRepository.snapshot()
        if (!settings.canResolvePlayableLinks) return emptyList()
        return DebridProviders.configuredServices(settings)
            .filter { credential -> credential.provider.supports(DebridProviderCapability.LocalTorrentCacheCheck) }
    }

    private data class CacheHit(
        val providerId: String,
        val providerName: String,
        val item: LocalDebridCachedItem,
    )
}

private val FINAL_CACHE_STATES = setOf(
    StreamDebridCacheState.CACHED,
    StreamDebridCacheState.NOT_CACHED,
)

internal fun StreamItem.localAvailabilityHash(): String? =
    infoHash
        ?.trim()
        ?.lowercase()
        ?.takeIf { isInstalledAddonStream && needsLocalDebridResolve && it.isNotBlank() }

private fun List<AddonStreamGroup>.updateAvailabilityStatus(
    eligibleGroupIds: Set<String>?,
    transform: (StreamItem) -> StreamItem,
): List<AddonStreamGroup> =
    map { group ->
        if (eligibleGroupIds != null && group.addonId !in eligibleGroupIds) return@map group
        var changed = false
        val updatedStreams = group.streams.map { stream ->
            val updated = transform(stream)
            if (updated != stream) changed = true
            updated
        }
        if (changed) group.copy(streams = updatedStreams) else group
    }
