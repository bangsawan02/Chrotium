package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.UserScript
import kotlinx.coroutines.flow.Flow

@Dao
interface UserScriptDao {
    @Query("SELECT * FROM userscripts ORDER BY isEnabled DESC, id ASC")
    fun getAllScripts(): Flow<List<UserScript>>

    @Query("SELECT * FROM userscripts WHERE isEnabled = 1")
    suspend fun getEnabledScripts(): List<UserScript>

    @Query("SELECT * FROM userscripts WHERE id = :id LIMIT 1")
    suspend fun getScriptById(id: Long): UserScript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: UserScript): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scripts: List<UserScript>)

    @Update
    suspend fun updateScript(script: UserScript)

    @Query("UPDATE userscripts SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun toggleScript(id: Long, isEnabled: Boolean)

    @Query("UPDATE userscripts SET isEnabled = :isEnabled")
    suspend fun toggleAll(isEnabled: Boolean)

    @Query("UPDATE userscripts SET executionCount = executionCount + 1, lastExecutedTimestamp = :timestamp WHERE id = :id")
    suspend fun incrementExecution(id: Long, timestamp: Long)

    @Delete
    suspend fun deleteScript(script: UserScript)

    @Query("DELETE FROM userscripts WHERE id = :id")
    suspend fun deleteScriptById(id: Long)

    @Query("DELETE FROM userscripts WHERE isBuiltIn = 1 AND name != :name")
    suspend fun deleteBuiltInScriptsExcept(name: String)

    @Query("DELETE FROM userscripts")
    suspend fun deleteAllScripts()

    @Query("SELECT COUNT(*) FROM userscripts")
    suspend fun getScriptsCount(): Int
}
