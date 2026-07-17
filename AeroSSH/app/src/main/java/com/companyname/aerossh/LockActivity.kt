package com.companyname.aerossh

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.companyname.aerossh.databinding.ActivityLockBinding
import com.companyname.aerossh.security.LuksEncryption
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private var isSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isSetup = !LuksEncryption.isVaultInitialized(this)

        if (!isSetup && LuksEncryption.isVaultUnlocked()) {
            openMain()
            return
        }

        setupUI()
    }

    private fun setupUI() {
        val minLen = LuksEncryption.isMinPasswordLength()

        if (isSetup) {
            binding.titleText.text = "Create Password"
            binding.subtitleText.text = "Min $minLen characters with uppercase, lowercase, digit"
            binding.confirmRow.visibility = android.view.View.VISIBLE
            binding.btnAction.text = "Create Vault"
        } else {
            binding.titleText.text = "AeroSSH"
            binding.subtitleText.text = "Enter password to unlock"
            binding.confirmRow.visibility = android.view.View.GONE
            binding.btnAction.text = "Unlock"
        }

        binding.btnAction.setOnClickListener { handleAction() }

        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleAction()
                true
            } else false
        }

        binding.passwordInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN) {
                handleAction()
                true
            } else false
        }
    }

    private fun validatePasswordStrength(password: String): String? {
        if (password.length < 8) return "Minimum 8 characters"
        if (!password.any { it.isUpperCase() }) return "Need at least one uppercase letter"
        if (!password.any { it.isLowerCase() }) return "Need at least one lowercase letter"
        if (!password.any { it.isDigit() }) return "Need at least one digit"
        return null
    }

    private fun handleAction() {
        val password = binding.passwordInput.text?.toString() ?: ""

        if (isSetup) {
            val strengthError = validatePasswordStrength(password)
            if (strengthError != null) {
                Toast.makeText(this, strengthError, Toast.LENGTH_SHORT).show()
                return
            }

            val confirm = binding.confirmInput.text?.toString() ?: ""
            if (password != confirm) {
                Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show()
                binding.confirmInput.error = "Mismatch"
                return
            }

            lifecycleScope.launch {
                binding.btnAction.isEnabled = false
                binding.btnAction.text = "Encrypting..."

                delay(100)

                try {
                    LuksEncryption.setupVault(this@LockActivity, password)
                    Toast.makeText(this@LockActivity, "Vault created", Toast.LENGTH_SHORT).show()
                    openMain()
                } catch (e: Exception) {
                    binding.btnAction.isEnabled = true
                    binding.btnAction.text = "Create Vault"
                    Toast.makeText(this@LockActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            if (password.isEmpty()) {
                binding.passwordInput.error = "Required"
                return
            }

            lifecycleScope.launch {
                binding.btnAction.isEnabled = false
                binding.btnAction.text = "Unlocking..."

                delay(100)

                val success = LuksEncryption.unlockVault(this@LockActivity, password)

                if (success) {
                    openMain()
                } else {
                    binding.btnAction.isEnabled = true
                    binding.btnAction.text = "Unlock"
                    binding.passwordInput.error = "Wrong password or vault wiped"
                    binding.passwordInput.text?.clear()

                    val shake = AnimationUtils.loadAnimation(this@LockActivity, R.anim.shake)
                    binding.passwordInput.startAnimation(shake)
                }
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Don't allow back from lock screen
    }
}
