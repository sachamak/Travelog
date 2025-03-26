package com.example.travelog.ui.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelog.data.repository.FirebaseRepository
import com.example.travelog.model.TravelPost
import kotlinx.coroutines.launch

class FeedViewModel : ViewModel() {
    private val repository = FirebaseRepository()
    private val _travelPosts = MutableLiveData<List<TravelPost>>()
    val travelPosts: LiveData<List<TravelPost>> = _travelPosts

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadTravelPosts() {
        viewModelScope.launch {
            val postsLiveData = repository.getAllTravelPosts()
            postsLiveData.observeForever { posts ->
                _travelPosts.value = posts
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}