package com.wearsic.app.data.cache

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Process-wide offline audio cache backed by Media3 SimpleCache. The playback
 * service and the Settings screen share this single instance because
 * SimpleCache cannot be opened twice on the same directory.
 *
 * A cache failure is never fatal: the playback service falls back to plain
 * network streaming when [configure] returns null.
 */
@OptIn(UnstableApi::class)
object PlaybackCache {
    private const val TAG = "WearsicCache"
    const val CACHE_DIR_NAME = "wearsic_cache"

    private var cache: SimpleCache? = null
    private var maxBytes = 0L

    /**
     * Create (or reuse) the cache with the given size limit. Pass 0 or a
     * negative value to disable caching. Reconfigures the evictor limit when
     * the requested size differs.
     */
    @Synchronized
    fun configure(context: Context, maxBytes: Long): Cache? {
        if (maxBytes <= 0L) return null
        if (cache != null && this.maxBytes == maxBytes) return cache
        return try {
            cache?.release()
            cache = null
            val dir = File(context.cacheDir, CACHE_DIR_NAME)
            dir.mkdirs()
            val fresh = SimpleCache(dir, LeastRecentlyUsedCacheEvictor(maxBytes))
            this.maxBytes = maxBytes
            cache = fresh
            Log.i(TAG, "Cache configured: $maxBytes bytes limit")
            fresh
        } catch (error: Exception) {
            // Never break playback because of the cache.
            Log.w(TAG, "Cache init failed, streaming without cache", error)
            cache?.release()
            cache = null
            this.maxBytes = 0L
            null
        }
    }

    @Synchronized
    fun get(): Cache? = cache

    /**
     * Remove cached spans while keeping the live SimpleCache handle usable by
     * the player. This is the safe clear path while playback is active.
     */
    @Synchronized
    fun clearContents(context: Context) {
        val active = cache
        if (active != null) {
            runCatching {
                active.keys.toList().forEach(active::removeResource)
            }.onFailure { error ->
                Log.w(TAG, "Could not remove cache entries", error)
            }
            return
        }
        runCatching { File(context.cacheDir, CACHE_DIR_NAME).deleteRecursively() }
    }

    /** Release the cache handle and delete every cached file. */
    @Synchronized
    fun clear(context: Context) {
        try {
            cache?.release()
        } catch (_: Exception) {
        }
        cache = null
        maxBytes = 0L
        try {
            File(context.cacheDir, CACHE_DIR_NAME).deleteRecursively()
        } catch (_: Exception) {
        }
        Log.i(TAG, "Cache cleared")
    }

    /** Total bytes currently stored on disk (includes in-flight writes). */
    fun sizeBytes(context: Context): Long =
        try {
            File(context.cacheDir, CACHE_DIR_NAME)
                .walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
}
