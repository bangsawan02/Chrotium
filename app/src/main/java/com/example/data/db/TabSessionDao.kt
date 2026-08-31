package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.TabSession

@Dao
interface TabSessionDao {
    @Query("SELECT * FROM tab_sessions ORDER BY sortOrder ASC")
    suspend fun getAllSessions(): List<TabSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<TabSession>)

    @Query("DELETE FROM tab_sessions")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(sessions: List<TabSession>) {
        deleteAll()
        insertAll(sessions)
    }
}
