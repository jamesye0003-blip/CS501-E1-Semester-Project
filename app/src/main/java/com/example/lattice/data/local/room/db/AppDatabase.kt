package com.example.lattice.data.local.room.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.lattice.data.local.room.dao.TaskDao
import com.example.lattice.data.local.room.dao.UserDao
import com.example.lattice.data.local.room.entity.TaskEntity
import com.example.lattice.data.local.room.entity.UserEntity

/**
 * Room数据库主类。
 *
 * 包含User和Task两个实体。
 * 使用TypeConverters处理Instant等复杂类型的转换。
 *
 * Main Room database entrypoint.
 *
 * Contains User and Task entities.
 * Uses TypeConverters for Instant and other non-primitive types.
 */
@Database(
    entities = [UserEntity::class, TaskEntity::class],
    version = 3,  // Incremented due to Entity schema changes (done->isDone, added sync fields)
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun taskDao(): TaskDao
    abstract fun userDao(): UserDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lattice_database"
                )
                    .fallbackToDestructiveMigration() // 开发阶段使用，生产环境应实现Migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}




