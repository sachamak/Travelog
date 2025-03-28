package com.example.travelog.ui.feed

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.travelog.R
import com.example.travelog.databinding.ItemPostBinding
import com.example.travelog.model.TravelPost

class TravelPostsAdapter(private val onPostClick: (TravelPost) -> Unit) :
    ListAdapter<TravelPost, TravelPostsAdapter.TravelPostViewHolder>(TravelPostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TravelPostViewHolder {
        val binding = ItemPostBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TravelPostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TravelPostViewHolder, position: Int) {
        val post = getItem(position)
        holder.bind(post)
    }

    inner class TravelPostViewHolder(private val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPostClick(getItem(position))
                }
            }
        }

        fun bind(post: TravelPost) {
            binding.postTitle.text = post.title
            binding.postDescription.text = post.description
            binding.postLocation.text = post.location
            binding.postUsername.text = "By ${post.username}"

            if (post.imageUri.isNotEmpty()) {
                if (post.imageUri.startsWith("http")) {
                    // C'est une URL standard
                    Glide.with(binding.root)
                        .load(post.imageUri)
                        .placeholder(R.drawable.placeholder_image)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .into(binding.postImage)
                } else {
                    try {
                        val imageBytes = Base64.decode(post.imageUri, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        binding.postImage.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        binding.postImage.setImageResource(R.drawable.placeholder_image)
                    }
                }
            } else {
                binding.postImage.setImageResource(R.drawable.placeholder_image)
            }
        }
    }

    private class TravelPostDiffCallback : DiffUtil.ItemCallback<TravelPost>() {
        override fun areItemsTheSame(oldItem: TravelPost, newItem: TravelPost): Boolean {
            return oldItem.postId == newItem.postId
        }

        override fun areContentsTheSame(oldItem: TravelPost, newItem: TravelPost): Boolean {
            return oldItem == newItem
        }
    }
}