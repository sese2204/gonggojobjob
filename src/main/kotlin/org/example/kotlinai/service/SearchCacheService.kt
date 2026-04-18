package org.example.kotlinai.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class SearchCacheService {

    private val cache = ConcurrentHashMap<String, CacheEntry<*>>()

    fun <T> get(key: String): T? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(key)
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return entry.value as T
    }

    fun <T> put(key: String, value: T) {
        cache[key] = CacheEntry(value, System.currentTimeMillis() + TTL_MS)
    }

    fun buildKey(userId: Long?, type: String, tags: List<String>, query: String): String {
        val userPart = userId?.toString() ?: "anon"
        val tagsPart = tags.sorted().joinToString(",")
        val queryPart = query.trim().lowercase()
        return "$type:$userPart:$tagsPart:$queryPart"
    }

    @Scheduled(fixedRate = 60_000)
    fun evictExpired() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { it.value.expiresAt < now }
    }

    private data class CacheEntry<T>(val value: T, val expiresAt: Long)

    companion object {
        private const val TTL_MS = 5 * 60 * 1000L
    }
}
