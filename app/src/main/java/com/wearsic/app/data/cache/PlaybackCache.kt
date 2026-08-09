package com.wearsic.app.data.cache

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
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
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var maxBytes = 0L

    /**
     * Create (or reuse) the cache with the given size limit. Pass 0 or a
     * negative value to disable caching. Reconfigures the evictor limit when
     * the requested size differs.
     *
     * Uses the current Media3 constructor (index backed by SQLite through a
     * [StandaloneDatabaseProvider], no content encryption). The legacy
     * file-based index from earlier releases is migrated automatically.
     */
    @Synchronized
    fun configure(context: Context, maxBytes: Long): Cache? {
        if (maxBytes <= 0L) return null
        if (cache != null && this.maxBytes == maxBytes) return cache
        return try {
            releaseCacheAndProvider()
            val dir = File(context.cacheDir, CACHE_DIR_NAME)
            dir.mkdirs()
            val provider = StandaloneDatabaseProvider(context)
            // The index requires a non-null secret key when a DatabaseProvider is
            // given (checked via Assertions.checkState). An empty key + encrypt
            // flag off means the cached bytes stay unencrypted.
            val fresh = SimpleCache(
                dir,
                LeastRecentlyUsedCacheEvictor(maxBytes),
                provider,
                /* legacyIndexSecretKey = */ ByteArray(0),
                /* legacyIndexEncrypt = */ false,
                /* preferLegacyIndex = */ false
            )
            this.databaseProvider = provider
            this.maxBytes = maxBytes
            cache = fresh
            Log.i(TAG, "Cache configured: $maxBytes bytes limit")
            fresh
        } catch (error: Exception) {
            // Never break playback because of the cache.
            Log.w(TAG, "Cache init failed, streaming without cache", error)
            releaseCacheAndProvider()
            this.maxBytes = 0L
            null
        }
    }

    /** Release the current cache handle and its index database. */
    private fun releaseCacheAndProvider() {
        try {
            cache?.release()
        } catch (_: Exception) {
        }
        cache = null
        try {
            databaseProvider?.close()
        } catch (_: Exception) {
        }
        databaseProvider = null
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
        releaseCacheAndProvider()
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
