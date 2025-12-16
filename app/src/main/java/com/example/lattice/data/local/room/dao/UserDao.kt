package com.example.lattice.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lattice.data.local.room.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * User实体的数据访问对象。
 * Data access object for User entities.
 */
@Dao
interface UserDao {
    // CREATE functions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    // READ functions
    @Query("SELECT * FROM users WHERE id = :id AND isDeleted = 0")
    fun observeUserById(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id AND isDeleted = 0")
    suspend fun getUserById(id: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE username = :username AND isDeleted = 0")
    suspend fun getUserByUsername(username: String): UserEntity?

    // DELETE functions
    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUser(id: String)
    
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("SELECT * FROM users WHERE remoteId = :remoteId AND isDeleted = 0 LIMIT 1")
    suspend fun getUserByRemoteId(remoteId: String): UserEntity?
}




