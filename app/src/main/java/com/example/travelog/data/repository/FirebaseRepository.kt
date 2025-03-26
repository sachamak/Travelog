package com.example.travelog.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.travelog.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID
import android.util.Base64
import com.google.android.gms.tasks.Task

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun registerUser(email: String, password: String): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(result.user?.uid ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<String> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user?.uid ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun logoutUser() {
        auth.signOut()
    }

    suspend fun createUserProfile(user: User): Result<Unit> {
        return try {
            firestore.collection("users")
                .document(user.userId)
                .set(user)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(userId: String): Result<User> {
        return try {
            val document = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                Result.success(document.toObject(User::class.java) ?: User())
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


}