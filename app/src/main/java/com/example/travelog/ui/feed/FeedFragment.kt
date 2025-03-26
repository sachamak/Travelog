package com.example.travelog.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travelog.databinding.FragmentFeedBinding
import com.example.travelog.model.TravelPost

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: FeedViewModel
    private lateinit var postsAdapter: TravelPostsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[FeedViewModel::class.java]

        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        postsAdapter = TravelPostsAdapter { post ->
            navigateToPostDetail(post)
        }

        binding.postsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = postsAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.travelPosts.observe(viewLifecycleOwner) { posts ->
            binding.loadingProgress.visibility = View.GONE

            if (posts.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.postsRecyclerView.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.postsRecyclerView.visibility = View.VISIBLE
                postsAdapter.submitList(posts)
            }
        }

        viewModel.loadTravelPosts()
    }

    private fun navigateToPostDetail(post: TravelPost) {
        val action = FeedFragmentDirections.actionFeedFragmentToPostDetailFragment(post.postId)
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}