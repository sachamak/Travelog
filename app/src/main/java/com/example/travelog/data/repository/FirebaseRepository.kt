package com.example.travelog.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.travelog.model.TravelPost
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

    fun encodeImageToBase64(context: Context, imageUri: Uri): String? {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            val compressedBitmap = compressImage(bitmap)
            return bitmapToBase64(compressedBitmap)
        } catch (e: Exception) {
            return null
        }
    }

    private fun compressImage(bitmap: Bitmap): Bitmap {
        val maxWidth = 800
        val maxHeight = 800
        val scale = Math.min(
            maxWidth.toFloat() / bitmap.width,
            maxHeight.toFloat() / bitmap.height
        )

        if (scale >= 1) return bitmap

        val width = (bitmap.width * scale).toInt()
        val height = (bitmap.height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    fun updateUserProfile(updates: HashMap<String, Any>): Task<Void> {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User empty")
        return firestore.collection("users").document(userId).update(updates)
    }

    suspend fun createPost(post: TravelPost): Result<String> {
        return try {
            val postId = UUID.randomUUID().toString()
            val newPost = post.copy(postId = postId)

            firestore.collection("posts")
                .document(postId)
                .set(newPost)
                .await()

            Result.success(postId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAllTravelPosts(): LiveData<List<TravelPost>> {
        val postsLiveData = MutableLiveData<List<TravelPost>>()

        try {
            firestore.collection("posts")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20) // Limiter le nombre de documents récupérés
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        android.util.Log.e("FirebaseRepository", "Error getAllTravelPosts: ${exception.message}")
                        postsLiveData.value = listOf()
                        return@addSnapshotListener
                    }

                    try {
                        val posts = snapshot?.documents?.mapNotNull { doc ->
                            try {
                                TravelPost(
                                    postId = doc.getString("postId") ?: "",
                                    userId = doc.getString("userId") ?: "",
                                    username = doc.getString("username") ?: "",
                                    title = doc.getString("title") ?: "",
                                    description = doc.getString("description") ?: "",
                                    location = doc.getString("location") ?: "",
                                    latitude = doc.getDouble("latitude") ?: 0.0,
                                    longitude = doc.getDouble("longitude") ?: 0.0,
                                    imageUri = doc.getString("imageUri") ?: "",
                                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("FirebaseRepository", "Error: ${e.message}")
                                null
                            }
                        } ?: listOf()

                        postsLiveData.value = posts
                    } catch (e: Exception) {
                        android.util.Log.e("FirebaseRepository", "Exception getAllTravelPosts: ${e.message}")
                        postsLiveData.value = listOf()
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Exception  getAllTravelPosts: ${e.message}")
            postsLiveData.value = listOf()
        }

        return postsLiveData
    }

    fun getUserTravelPosts(userId: String): LiveData<List<TravelPost>> {
        val postsLiveData = MutableLiveData<List<TravelPost>>()
        android.util.Log.d("FirebaseRepository", "Start: $userId")

        try {
            firestore.collection("posts")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener { documents ->
                    try {
                        val posts = documents.documents.mapNotNull { doc ->
                            try {
                                TravelPost(
                                    postId = doc.getString("postId") ?: "",
                                    userId = doc.getString("userId") ?: "",
                                    username = doc.getString("username") ?: "",
                                    title = doc.getString("title") ?: "",
                                    description = doc.getString("description") ?: "",
                                    location = doc.getString("location") ?: "",
                                    latitude = doc.getDouble("latitude") ?: 0.0,
                                    longitude = doc.getDouble("longitude") ?: 0.0,
                                    imageUri = doc.getString("imageUri") ?: "",
                                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("FirebaseRepository", "Error: ${e.message}")
                                null
                            }
                        }

                        val sortedPosts = posts.sortedByDescending { it.createdAt }
                        android.util.Log.d("FirebaseRepository", "Loading post: $userId: ${sortedPosts.size}")
                        postsLiveData.postValue(sortedPosts)
                    } catch (e: Exception) {
                        android.util.Log.e("FirebaseRepository", "Exception: ${e.message}")
                        postsLiveData.postValue(listOf())
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("FirebaseRepository", "Errore: ${e.message}")
                    postsLiveData.postValue(listOf())
                }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Exception : ${e.message}")
            postsLiveData.postValue(listOf())
        }

        return postsLiveData
    }
}