package com.example.travelog.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.travelog.data.local.entity.TravelPostEntity

@Dao
interface TravelPostDao {
    @Query("SELECT * FROM travel_posts ORDER BY createdAt DESC")
    fun getAllPosts(): LiveData<List<TravelPostEntity>>

    @Query("SELECT * FROM travel_posts WHERE userId = :userId ORDER BY createdAt DESC")
    fun getPostsByUserId(userId: String): LiveData<List<TravelPostEntity>>

    @Query("SELECT * FROM travel_posts WHERE postId = :postId")
    suspend fun getPostById(postId: String): TravelPostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<TravelPostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: TravelPostEntity)

    @Update
    suspend fun updatePost(post: TravelPostEntity)

    @Delete
    suspend fun deletePost(post: TravelPostEntity)

    @Query("DELETE FROM travel_posts WHERE postId = :postId")
    suspend fun deletePostById(postId: String)
}