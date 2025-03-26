package com.example.travelog.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.travelog.R
import com.example.travelog.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AuthViewModel
    private val TAG = "LoginFragment"

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

        setupClickListeners()
        observeAuthenticationState()
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
                            "Login successful!",
                            Toast.LENGTH_SHORT
                        ).show()


                    }
                    is AuthViewModel.AuthState.Error -> {
                        binding.loadingProgress.visibility = View.GONE
                        Toast.makeText(
                            requireContext(),
                            "Erreur: ${state.message}",
                            Toast.LENGTH_LONG
                        ).show()

                    }
                    else -> {
                        binding.loadingProgress.visibility = View.GONE
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "error: ${e.message}",
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