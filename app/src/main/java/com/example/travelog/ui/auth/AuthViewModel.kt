package com.example.travelog.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelog.data.repository.FirebaseRepository
import com.example.travelog.model.User
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val repository = FirebaseRepository()

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val result = repository.loginUser(email, password)

                result.fold(
                    onSuccess = { userId ->
                        _authState.value = AuthState.Success(userId)
                    },
                    onFailure = { e ->
                        _authState.value = AuthState.Error(e.message ?: "Authentication failed")
                    }
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                val registerResult = repository.registerUser(email, password)

                registerResult.fold(
                    onSuccess = { userId ->
                        val user = User(
                            userId = userId,
                            username = username,
                            email = email
                        )

                        val profileResult = repository.createUserProfile(user)
                        profileResult.fold(
                            onSuccess = {
                                _authState.value = AuthState.Success(userId)
                            },
                            onFailure = { e ->
                                _authState.value = AuthState.Error(e.message ?: "Failed to create profile")
                            }
                        )
                    },
                    onFailure = { e ->
                        _authState.value = AuthState.Error(e.message ?: "Registration failed")
                    }
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun logout() {
        try {
            repository.logoutUser()
            _authState.value = AuthState.Idle
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Logout failed")
        }
    }

    sealed class AuthState {
        object Idle : AuthState()
        data class Success(val userId: String) : AuthState()
        data class Error(val message: String) : AuthState()
    }
}