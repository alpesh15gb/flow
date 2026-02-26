package `in`.cartunez.flow.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.databinding.ActivityLoginBinding
import `in`.cartunez.flow.network.LoginRequest
import `in`.cartunez.flow.network.RegisterRequest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as FlowApp

        // If already logged in, go straight to MainActivity
        lifecycleScope.launch {
            if (app.prefsStore.getToken() != null) {
                startMain()
                return@launch
            }
        }

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (username.length < 3) {
                showError("Username must be at least 3 characters")
                return@setOnClickListener
            }
            if (password.length < 4) {
                showError("Password must be at least 4 characters")
                return@setOnClickListener
            }

            binding.tvError.visibility = View.GONE
            binding.progress.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false

            lifecycleScope.launch {
                val deviceId = app.prefsStore.getDeviceId()

                try {
                    if (isRegisterMode) {
                        val name = binding.etName.text.toString().trim().ifBlank { null }
                        val resp = app.apiService.register(
                            RegisterRequest(username, password, name, deviceId)
                        )
                        if (resp.isSuccessful && resp.body() != null) {
                            val body = resp.body()!!
                            app.prefsStore.saveAuth(body.token, body.user_id, body.username)
                            startMain()
                        } else {
                            val msg = resp.errorBody()?.string()?.let {
                                try { org.json.JSONObject(it).optString("error", "Registration failed") }
                                catch (e: Exception) { "Registration failed" }
                            } ?: "Registration failed"
                            showError(msg)
                        }
                    } else {
                        val resp = app.apiService.login(
                            LoginRequest(username, password, deviceId)
                        )
                        if (resp.isSuccessful && resp.body() != null) {
                            val body = resp.body()!!
                            app.prefsStore.saveAuth(body.token, body.user_id, body.username)
                            startMain()
                        } else {
                            val msg = resp.errorBody()?.string()?.let {
                                try { org.json.JSONObject(it).optString("error", "Login failed") }
                                catch (e: Exception) { "Login failed" }
                            } ?: "Login failed"
                            showError(msg)
                        }
                    }
                } catch (e: Exception) {
                    showError("Connection failed — check your internet")
                } finally {
                    binding.progress.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                }
            }
        }

        binding.tvToggle.setOnClickListener {
            isRegisterMode = !isRegisterMode
            binding.layoutName.visibility = if (isRegisterMode) View.VISIBLE else View.GONE
            binding.btnLogin.text = if (isRegisterMode) "Register" else "Login"
            binding.tvToggle.text = if (isRegisterMode) "Already have an account? Login" else "Don't have an account? Register"
            binding.tvError.visibility = View.GONE
        }
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
