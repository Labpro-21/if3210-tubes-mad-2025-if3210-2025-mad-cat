package com.tubesmobile.purrytify.data.local.db

import androidx.room.*
import com.example.purrytify.data.local.db.entities.SongEntity
import com.example.purrytify.data.local.db.entities.LikedSongCrossRef
import com.example.purrytify.data.local.db.entities.RecentlyPlayedSong
import com.example.purrytify.data.local.db.entities.ListenedSong
import com.example.purrytify.data.local.db.entities.SongUploader
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT EXISTS(SELECT 1 FROM songs AS s JOIN song_uploader AS su WHERE s.title = :title AND s.artist = :artist AND su.uploaderEmail = :userEmail AND s.id = su.songId)")
    suspend fun isSongExistsForUser(title: String, artist: String, userEmail: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM songs WHERE title = :title AND artist = :artist)")
    suspend fun isSongExists(title: String, artist: String): Boolean

    @Query("INSERT INTO song_uploader (uploaderEmail, songId) VALUES (:uploader, :songId)")
    suspend fun registerUserToSong(uploader: String, songId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun likeSong(crossRef: LikedSongCrossRef)

    @Delete
    suspend fun unlikeSong(crossRef: LikedSongCrossRef)

    @Query("""
        SELECT s.* FROM songs AS s
        JOIN song_uploader AS su ON s.id = su.songId
        WHERE su.uploaderEmail = :userEmail
    """)
    fun getSongsByUser(userEmail: String): Flow<List<SongEntity>>

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN liked_songs ON songs.id = liked_songs.songId
        WHERE liked_songs.userEmail = :userEmail
    """)
    suspend fun getLikedSongs(userEmail: String): List<SongEntity>

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN liked_songs ON songs.id = liked_songs.songId
        WHERE liked_songs.userEmail = :userEmail
    """)
    fun getLikedSongsFlow(userEmail: String): Flow<List<SongEntity>>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM liked_songs WHERE userEmail = :userEmail AND songId = :songId
        )
    """)
    suspend fun isSongLiked(userEmail: String, songId: Int): Boolean

    @Query("SELECT id FROM songs WHERE title = :title AND artist = :artist")
    suspend fun getSongId(title: String, artist: String): Int

    // Methods for edit and delete functionality
    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSong(songId: Int)

    @Query("DELETE FROM song_uploader WHERE uploaderEmail = :userEmail AND songId = :songId")
    suspend fun deleteUserSong(userEmail: String, songId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM song_uploader WHERE songId = :songId)")
    suspend fun isSongUsedByOthers(songId: Int): Boolean

    // New methods for recently played songs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyPlayedSong(recentlyPlayedSong: RecentlyPlayedSong)

    @Query("""
        SELECT s.* FROM songs AS s
        JOIN recently_played AS rp ON s.id = rp.songId
        WHERE rp.userEmail = :userEmail
        ORDER BY rp.timestamp DESC
        LIMIT :limit
    """)
    fun getRecentlyPlayedSongs(userEmail: String, limit: Int = 10): Flow<List<SongEntity>>

    // New methods for listened songs
    @Insert(onConflict = OnConflictStrategy.IGNORE) // Use IGNORE to only insert first time
    suspend fun insertListenedSong(listenedSong: ListenedSong)

    @Query("SELECT COUNT(*) FROM listened_songs WHERE userEmail = :userEmail")
    fun getListenedSongsCount(userEmail: String): Flow<Int>
}