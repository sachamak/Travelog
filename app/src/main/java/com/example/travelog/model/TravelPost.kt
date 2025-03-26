package com.example.travelog.model

data class TravelPost(
    val postId: String = "",
    val userId: String = "",
    val username: String = "",
    var title: String = "",
    var description: String = "",
    var location: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var imageUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)