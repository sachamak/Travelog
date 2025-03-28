package com.example.travelog.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelog.data.repository.FirebaseRepository
import com.example.travelog.model.User
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val repository = FirebaseRepository()
    private val TAG = "AuthViewModel"

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    init {
        _authState.value = AuthState.Idle
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val result = repository.loginUser(email, password)

                result.fold(
                    onSuccess = { userId ->
                        _authState.value = AuthState.Success(userId)
                        Log.d(TAG, "Login successful for user: $userId")
                    },
                    onFailure = { e ->
                        _authState.value = AuthState.Error(e.message ?: "Authentication failed")
                        Log.e(TAG, "Login failed: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Authentication failed")
                Log.e(TAG, "Login exception: ${e.message}")
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
                                Log.d(TAG, "Registration successful for user: $userId")
                            },
                            onFailure = { e ->
                                _authState.value = AuthState.Error(e.message ?: "Failed to create profile")
                                Log.e(TAG, "Profile creation failed: ${e.message}")
                            }
                        )
                    },
                    onFailure = { e ->
                        _authState.value = AuthState.Error(e.message ?: "Registration failed")
                        Log.e(TAG, "Registration failed: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
                Log.e(TAG, "Registration exception: ${e.message}")
            }
        }
    }

    fun logout() {
        try {
            Log.d(TAG, "Logging out user")
            repository.logoutUser()
            _authState.value = AuthState.Idle
            Log.d(TAG, "User logged out, auth state set to Idle")
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Logout failed")
            Log.e(TAG, "Logout failed: ${e.message}")
        }
    }

    fun resetAuthState() {
        Log.d(TAG, "Resetting auth state to Idle")
        _authState.value = AuthState.Idle
    }

    sealed class AuthState {
        object Idle : AuthState()
        data class Success(val userId: String) : AuthState()
        data class Error(val message: String) : AuthState()
    }
}