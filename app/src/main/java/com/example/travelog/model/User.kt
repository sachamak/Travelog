package com.example.travelog.model

data class User(
    val userId: String = "",
    var username: String = "",
    var email: String = "",
    var profileImageUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)