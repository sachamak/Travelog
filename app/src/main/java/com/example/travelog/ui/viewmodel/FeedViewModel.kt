package com.example.travelog.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelog.data.repository.FirebaseRepository
import com.example.travelog.model.TravelPost
import kotlinx.coroutines.launch
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.travelog.data.local.AppDatabase
import com.example.travelog.data.local.entity.TravelPostEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth

class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository()
    private val _travelPosts = MutableLiveData<List<TravelPost>>()
    val travelPosts: LiveData<List<TravelPost>> = _travelPosts

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isOffline = MutableLiveData<Boolean>(false)
    val isOffline: LiveData<Boolean> = _isOffline

    private val _authenticationRequired = MutableLiveData<Boolean>(false)
    val authenticationRequired: LiveData<Boolean> = _authenticationRequired

    private val appDatabase = AppDatabase.getDatabase(application)

    fun loadTravelPosts() {
        if (!isUserAuthenticated()) {
            Log.d("FeedViewModel", "User not authenticated, cannot load posts")
            _authenticationRequired.value = true
            _isLoading.value = false
            _travelPosts.value = emptyList()
            return
        }
        _isLoading.value = true
        _isOffline.value = false
        try {
            viewModelScope.launch {
                try {
                    if (!isUserAuthenticated()) {
                        _authenticationRequired.value = true
                        _isLoading.value = false
                        return@launch
                    }
                    val postsLiveData = repository.getAllTravelPosts()
                    postsLiveData.observeForever { posts ->
                        _travelPosts.value = posts
                        _isLoading.value = false
                        if (posts.isNotEmpty()) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val entities = posts.map { post ->
                                        TravelPostEntity(
                                            postId = post.postId,
                                            userId = post.userId,
                                            username = post.username,
                                            title = post.title,
                                            description = post.description,
                                            location = post.location,
                                            latitude = post.latitude,
                                            longitude = post.longitude,
                                            imageUri = post.imageUri,
                                            createdAt = post.createdAt,
                                            updatedAt = post.updatedAt
                                        )
                                    }
                                    appDatabase.travelPostDao().insertPosts(entities)
                                    Log.d("FeedViewModel", "Saved ${entities.size} posts to local database")
                                } catch (e: Exception) {
                                    Log.e("FeedViewModel", "Error saving posts to Room", e)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    handleNetworkError(e)
                }
            }
        } catch (e: Exception) {
            handleNetworkError(e)
        }
    }
    private fun isUserAuthenticated(): Boolean {
        val isAuthenticated = FirebaseAuth.getInstance().currentUser != null
        Log.d("FeedViewModel", "Authentication check: user is ${if (isAuthenticated) "authenticated" else "not authenticated"}")
        return isAuthenticated
    }
    private fun handleNetworkError(e: Exception) {
        Log.e("FeedViewModel", "Network error loading posts: ${e.message}")
        _error.value = "Unable to connect to network: ${e.message}"
        _isOffline.value = true
        if (isUserAuthenticated()) {
            loadFromLocalDatabase()
        } else {
            _authenticationRequired.value = true
            _isLoading.value = false
        }
    }
    private fun loadFromLocalDatabase() {
        if (!isUserAuthenticated()) {
            _authenticationRequired.value = true
            _isLoading.value = false
            return
        }
        viewModelScope.launch {
            try {
                val localPosts = withContext(Dispatchers.IO) {
                    appDatabase.travelPostDao().getAllPosts()
                }
                localPosts.observeForever { postEntities ->
                    val posts = postEntities.map { entity ->
                        TravelPost(
                            postId = entity.postId,
                            userId = entity.userId,
                            username = entity.username,
                            title = entity.title,
                            description = entity.description,
                            location = entity.location,
                            latitude = entity.latitude,
                            longitude = entity.longitude,
                            imageUri = entity.imageUri,
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt
                        )
                    }

                    _travelPosts.value = posts
                    _isLoading.value = false
                    Log.d("FeedViewModel", "Loaded ${posts.size} posts from local database")
                }
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Error loading posts from Room", e)
                _error.value = "Error loading saved posts: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FeedViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FeedViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}