package com.example.travelog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.travelog.data.local.dao.TravelPostDao
import com.example.travelog.data.local.dao.UserDao
import com.example.travelog.data.local.entity.TravelPostEntity
import com.example.travelog.data.local.entity.UserEntity

@Database(entities = [UserEntity::class, TravelPostEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun travelPostDao(): TravelPostDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "travelog_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}