package com.wearsic.app.service

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * A partial wake lock that is held ONLY while a network fetch is actually in
 * flight, and released the moment it finishes (or fails).
 *
 * Why this matters on a watch: a background download must not be killed when
 * the screen sleeps, but holding a wake lock for the whole download keeps the
 * CPU+radio hot for minutes. The radio is the battery's worst enemy, so the
 * lock is acquired right before the fetch starts and released in a `finally`
 * — the system can suspend the CPU and drop the antenna back to sleep the
 * instant the data is on disk.
 *
 * Media3's `C.WAKE_MODE_NETWORK` already does the same tight acquire/release
 * dance for live playback; this helper covers the offline CacheWriter path,
 * which runs outside the player.
 */
class FetchWakeLock(context: Context) {

    private val wakeLock: PowerManager.WakeLock = run {
        val pm = context.getSystemService(PowerManager::class.java)
        pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "wearsic:network-fetch"
        ).apply {
            // Downloads may overlap; a reference-counted lock would need a
            // matching release per acquire. We use an explicit counter instead
            // so one final release() can never drop the lock while another
            // fetch is still running.
            setReferenceCounted(false)
        }
    }

    @Volatile
    private var holders = 0

    /** Acquire the lock (idempotent; safe to call from any thread). */
    fun acquire() {
        synchronized(this) {
            if (holders == 0) {
                runCatching { wakeLock.acquire() }
                    .onFailure { Log.w("WearsicFetchWakeLock", "acquire failed", it) }
            }
            holders++
        }
    }

    /** Release one holder. The lock drops only when the last holder leaves. */
    fun release() {
        synchronized(this) {
            if (holders <= 0) return
            holders--
            if (holders == 0 && wakeLock.isHeld) {
                runCatching { wakeLock.release() }
                    .onFailure { Log.w("WearsicFetchWakeLock", "release failed", it) }
            }
        }
    }

    /** True while at least one fetch is holding the CPU awake. */
    val isHeld: Boolean
        get() = holders > 0
}
