package com.example.travelog.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class User(
    val id: String,
    val username: String,
    val email: String,
    val profileImageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class ProfileViewModel : ViewModel() {

    sealed class OperationStatus {
        object Success : OperationStatus()
        data class Error(val message: String) : OperationStatus()
        object Loading : OperationStatus()
        object Idle : OperationStatus()
    }

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _userProfile = MutableLiveData<User?>()
    val userProfile: LiveData<User?> = _userProfile

    private val _userPosts = MutableLiveData<List<Post>>()
    val userPosts: LiveData<List<Post>> = _userPosts

    private val _isLoggedOut = MutableLiveData<Boolean>()
    val isLoggedOut: LiveData<Boolean> = _isLoggedOut

    private val _operationStatus = MutableLiveData<OperationStatus>(OperationStatus.Idle)
    val operationStatus: LiveData<OperationStatus> = _operationStatus


    fun updateUserProfileLocal(user: User?) {
        _userProfile.value = user
        _operationStatus.value = OperationStatus.Success
    }

    fun updateUserPostsLocal(posts: List<Post>) {
        _userPosts.value = posts
    }

    fun setLoggedOut(loggedOut: Boolean) {
        _isLoggedOut.value = loggedOut
    }

    fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }
}