package com.example.travelog.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.travelog.R
import com.example.travelog.databinding.FragmentLoginBinding
import com.example.travelog.ui.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AuthViewModel
    private val TAG = "LoginFragment"
    private var isInitialAuthCheck = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AuthViewModel::class.java]

        Log.d(TAG, "LoginFragment: onViewCreated")

        setupClickListeners()
        observeAuthenticationState()

        val navBackStackEntry = findNavController().previousBackStackEntry
        if (navBackStackEntry != null) {
            Log.d(TAG, "Previous destination: ${navBackStackEntry.destination.label}")

            if (navBackStackEntry.destination.id != R.id.registerFragment) {
                Log.d(TAG, "Likely returned after logout")
                viewModel.resetAuthState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "LoginFragment: onResume")

        if (isInitialAuthCheck) {
            checkCurrentUser()
            isInitialAuthCheck = false
        }
    }

    private fun checkCurrentUser() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            Log.d(TAG, "User already logged in (${currentUser.uid}), navigating to feed")
            findNavController().navigate(R.id.action_loginFragment_to_feedFragment)
        } else {
            Log.d(TAG, "No user logged in, staying at login screen")
        }
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            if (validateInput(email, password)) {
                binding.loadingProgress.visibility = View.VISIBLE
                viewModel.login(email, password)
            }
        }

        binding.registerText.setOnClickListener {
            findNavController().navigate(
                LoginFragmentDirections.actionLoginFragmentToRegisterFragment()
            )
        }
    }

    private fun observeAuthenticationState() {
        try {
            viewModel.authState.observe(viewLifecycleOwner) { state ->
                when (state) {
                    is AuthViewModel.AuthState.Success -> {
                        binding.loadingProgress.visibility = View.GONE
                        Toast.makeText(
                            requireContext(),
                            "Connection successful",
                            Toast.LENGTH_SHORT
                        ).show()

                        if (isAdded && isResumed) {
                            findNavController().navigate(R.id.action_loginFragment_to_feedFragment)
                            Log.d(TAG, "Navigating to feed after successful login")
                        }
                    }
                    is AuthViewModel.AuthState.Error -> {
                        binding.loadingProgress.visibility = View.GONE
                        Toast.makeText(
                            requireContext(),
                            "Error: ${state.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e(TAG, "Authentication Error: ${state.message}")
                    }
                    is AuthViewModel.AuthState.Idle -> {
                        binding.loadingProgress.visibility = View.GONE
                        Log.d(TAG, "Auth state is idle")
                    }
                    else -> {
                        binding.loadingProgress.visibility = View.GONE
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Navigating to feed after successful login\n", e)
            Toast.makeText(
                requireContext(),
                "An unexpected error has occurred: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.emailLayout.error = "Email cannot be empty"
            return false
        }
        if (password.isEmpty()) {
            binding.passwordLayout.error = "Password cannot be empty"
            return false
        }
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}