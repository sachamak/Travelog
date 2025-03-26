package com.example.travelog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "travel_posts")
data class TravelPostEntity(
    @PrimaryKey val postId: String,
    val userId: String,
    val username: String,
    val title: String,
    val description: String,
    val location: String,
    val latitude: Double,
    val longitude: Double,
    val imageUri: String,
    val createdAt: Long,
    val updatedAt: Long
)