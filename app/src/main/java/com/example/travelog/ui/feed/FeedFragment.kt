package com.example.travelog.ui.feed

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.travelog.R
import com.example.travelog.databinding.FragmentFeedBinding
import com.example.travelog.model.TravelPost
import android.widget.Toast
import com.example.travelog.ui.viewmodel.FeedViewModel
import com.google.firebase.auth.FirebaseAuth

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: FeedViewModel
    private lateinit var postsAdapter: TravelPostsAdapter
    private val TAG = "FeedFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "FeedFragment: onViewCreated")

        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.d(TAG, "User not authenticated, navigating to login")
            findNavController().navigate(R.id.loginFragment, null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .build()
            )
            return
        }

        viewModel = ViewModelProvider(
            this,
            FeedViewModel.Factory(requireActivity().application)
        )[FeedViewModel::class.java]

        setupRecyclerView()
        observeViewModel()

        binding.swipeRefreshLayout.setOnRefreshListener {
            if (FirebaseAuth.getInstance().currentUser == null) {
                Log.d(TAG, "Refresh attempted but user not authenticated")
                binding.swipeRefreshLayout.isRefreshing = false
                findNavController().navigate(R.id.loginFragment, null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .build()
                )
                return@setOnRefreshListener
            }
            viewModel.loadTravelPosts()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "FeedFragment: onResume")
        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.d(TAG, "User not authenticated in onResume, navigating to login")
            findNavController().navigate(R.id.loginFragment, null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .build()
            )
        }
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
            binding.swipeRefreshLayout.isRefreshing = false
            if (posts.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.postsRecyclerView.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.postsRecyclerView.visibility = View.VISIBLE
                postsAdapter.submitList(posts)
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.isOffline.observe(viewLifecycleOwner) { isOffline ->
            if (isOffline) {
                binding.offlineBar.visibility = View.VISIBLE
            } else {
                binding.offlineBar.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.loadingProgress.visibility = View.VISIBLE
            } else {
                binding.loadingProgress.visibility = View.GONE
            }
        }

        viewModel.authenticationRequired.observe(viewLifecycleOwner) { required ->
            if (required) {
                Log.d(TAG, "Authentication required, navigating to login")
                findNavController().navigate(R.id.loginFragment, null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .build()
                )
            }
        }

        if (FirebaseAuth.getInstance().currentUser != null) {
            viewModel.loadTravelPosts()
        }
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