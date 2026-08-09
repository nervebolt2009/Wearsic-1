package com.wearsic.server

import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class Database(databasePath: String) : AutoCloseable {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$databasePath")

    init {
        connection.createStatement().use { statement ->
            statement.executeUpdate("PRAGMA journal_mode=WAL")
            statement.executeUpdate("PRAGMA synchronous=NORMAL")
            statement.executeUpdate("PRAGMA foreign_keys=ON")
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS favorites (
                    video_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    uploader TEXT NOT NULL,
                    duration_ms INTEGER NOT NULL,
                    thumbnail_url TEXT NOT NULL
                )
            """.trimIndent())
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS playlists (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    thumbnail_url TEXT
                )
            """.trimIndent())
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS playlist_tracks (
                    playlist_id TEXT NOT NULL,
                    video_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    uploader TEXT NOT NULL,
                    duration_ms INTEGER NOT NULL,
                    thumbnail_url TEXT NOT NULL,
                    PRIMARY KEY (playlist_id, video_id),
                    FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
                )
            """.trimIndent())
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """.trimIndent())
        }
    }

    @Synchronized
    fun getSetting(key: String): String? = connection.prepareStatement(
        "SELECT value FROM settings WHERE key = ?"
    ).use { statement ->
        statement.setString(1, key)
        statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
    }

    @Synchronized
    fun saveSetting(key: String, value: String) {
        connection.prepareStatement("""
            INSERT INTO settings(key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
        """.trimIndent()).use { statement ->
            statement.setString(1, key)
            statement.setString(2, value)
            statement.executeUpdate()
        }
    }

    @Synchronized
    fun getFavorites(): List<TrackDto> = connection.prepareStatement(
        "SELECT video_id, title, uploader, duration_ms, thumbnail_url FROM favorites ORDER BY rowid DESC"
    ).use { statement ->
        statement.executeQuery().use { rows ->
            buildList {
                while (rows.next()) add(rows.toTrack())
            }
        }
    }

    @Synchronized
    fun saveFavorite(track: TrackDto) {
        connection.prepareStatement("""
            INSERT INTO favorites(video_id, title, uploader, duration_ms, thumbnail_url)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(video_id) DO UPDATE SET
                title = excluded.title,
                uploader = excluded.uploader,
                duration_ms = excluded.duration_ms,
                thumbnail_url = excluded.thumbnail_url
        """.trimIndent()).use { statement ->
            statement.setString(1, track.videoId)
            statement.setString(2, track.title)
            statement.setString(3, track.uploader)
            statement.setLong(4, track.durationMs)
            statement.setString(5, track.thumbnailUrl)
            statement.executeUpdate()
        }
    }

    @Synchronized
    fun deleteFavorite(videoId: String): Boolean = connection.prepareStatement(
        "DELETE FROM favorites WHERE video_id = ?"
    ).use { statement ->
        statement.setString(1, videoId)
        statement.executeUpdate() > 0
    }

    @Synchronized
    fun getPlaylists(): List<PlaylistDto> = connection.prepareStatement("""
        SELECT p.id, p.name, p.thumbnail_url, COUNT(t.video_id)
        FROM playlists p LEFT JOIN playlist_tracks t ON p.id = t.playlist_id
        GROUP BY p.id ORDER BY p.rowid DESC
    """.trimIndent()).use { statement ->
        statement.executeQuery().use { rows ->
            buildList {
                while (rows.next()) {
                    add(PlaylistDto(rows.getString(1), rows.getString(2), rows.getInt(4), rows.getString(3)))
                }
            }
        }
    }

    @Synchronized
    fun createPlaylist(name: String): PlaylistDto {
        val id = UUID.randomUUID().toString()
        connection.prepareStatement("INSERT INTO playlists(id, name) VALUES (?, ?)").use { statement ->
            statement.setString(1, id)
            statement.setString(2, name.trim())
            statement.executeUpdate()
        }
        return PlaylistDto(id, name.trim(), 0)
    }

    @Synchronized
    fun getPlaylist(id: String): PlaylistWithTracksDto? {
        val playlist = connection.prepareStatement("SELECT id, name, thumbnail_url FROM playlists WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return null
                Triple(rows.getString(1), rows.getString(2), rows.getString(3))
            }
        }
        val tracks = connection.prepareStatement("""
            SELECT video_id, title, uploader, duration_ms, thumbnail_url
            FROM playlist_tracks WHERE playlist_id = ? ORDER BY position
        """.trimIndent()).use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(rows.toTrack())
                }
            }
        }
        return PlaylistWithTracksDto(playlist.first, playlist.second, tracks)
    }

    @Synchronized
    fun addPlaylistTrack(playlistId: String, track: TrackDto): Boolean {
        if (!playlistExists(playlistId)) return false
        connection.prepareStatement("""
            INSERT INTO playlist_tracks(playlist_id, video_id, position, title, uploader, duration_ms, thumbnail_url)
            VALUES (?, ?, COALESCE((SELECT MAX(position) + 1 FROM playlist_tracks WHERE playlist_id = ?), 0), ?, ?, ?, ?)
            ON CONFLICT(playlist_id, video_id) DO UPDATE SET
                title = excluded.title,
                uploader = excluded.uploader,
                duration_ms = excluded.duration_ms,
                thumbnail_url = excluded.thumbnail_url
        """.trimIndent()).use { statement ->
            statement.setString(1, playlistId)
            statement.setString(2, track.videoId)
            statement.setString(3, playlistId)
            statement.setString(4, track.title)
            statement.setString(5, track.uploader)
            statement.setLong(6, track.durationMs)
            statement.setString(7, track.thumbnailUrl)
            statement.executeUpdate()
        }
        return true
    }

    @Synchronized
    fun deletePlaylistTrack(playlistId: String, videoId: String): Boolean = connection.prepareStatement(
        "DELETE FROM playlist_tracks WHERE playlist_id = ? AND video_id = ?"
    ).use { statement ->
        statement.setString(1, playlistId)
        statement.setString(2, videoId)
        statement.executeUpdate() > 0
    }

    private fun playlistExists(id: String): Boolean = connection.prepareStatement(
        "SELECT 1 FROM playlists WHERE id = ?"
    ).use { statement ->
        statement.setString(1, id)
        statement.executeQuery().use { it.next() }
    }

    private fun java.sql.ResultSet.toTrack() = TrackDto(
        videoId = getString(1),
        title = getString(2),
        uploader = getString(3),
        durationMs = getLong(4),
        thumbnailUrl = getString(5)
    )

    override fun close() {
        connection.close()
    }
}
