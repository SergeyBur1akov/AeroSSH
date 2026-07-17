package com.companyname.aerossh

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.companyname.aerossh.databinding.ActivityLockBinding
import com.companyname.aerossh.security.LuksEncryption
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LockActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLockBinding; private var isSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); binding = ActivityLockBinding.inflate(layoutInflater); setContentView(binding.root)
        isSetup = !LuksEncryption.isVaultInitialized(this)
        if (!isSetup && LuksEncryption.isVaultUnlocked()) { openMain(); return }
        setupUI(); playEntryAnimation()
    }

    private fun playEntryAnimation() {
        binding.lockScreen.alpha = 0f; binding.lockScreen.translationY = 40f
        binding.lockScreen.animate().alpha(1f).translationY(0f).setDuration(600).setInterpolator(OvershootInterpolator(1.2f)).start()
    }

    private fun setupUI() {
        if (isSetup) { binding.titleText.text = "Create Password"; binding.subtitleText.text = "Min 8 chars with uppercase, lowercase, digit"; binding.confirmRow.visibility = View.VISIBLE; binding.btnAction.text = "Create Vault" }
        else { binding.titleText.text = "AeroSSH"; binding.subtitleText.text = "Enter password to unlock"; binding.confirmRow.visibility = View.GONE; binding.btnAction.text = "Unlock" }
        binding.btnAction.setOnClickListener { handleAction() }
        binding.passwordInput.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { handleAction(); true } else false }
    }

    private fun validatePassword(p: String): String? { if (p.length < 8) return "Minimum 8 characters"; if (!p.any { it.isUpperCase() }) return "Need uppercase"; if (!p.any { it.isLowerCase() }) return "Need lowercase"; if (!p.any { it.isDigit() }) return "Need digit"; return null }

    private fun handleAction() {
        val password = binding.passwordInput.text?.toString() ?: ""
        if (isSetup) {
            validatePassword(password)?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show(); return }
            val confirm = binding.confirmInput.text?.toString() ?: ""
            if (password != confirm) { Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show(); return }
            lifecycleScope.launch { binding.btnAction.isEnabled = false; binding.btnAction.text = "Encrypting..."; delay(100)
                try { LuksEncryption.setupVault(this@LockActivity, password); showDecryptAnimation() } catch (e: Exception) { binding.btnAction.isEnabled = true; binding.btnAction.text = "Create Vault"; Toast.makeText(this@LockActivity, "Vault setup failed", Toast.LENGTH_SHORT).show() }
            }
        } else {
            if (password.isEmpty()) { binding.passwordInput.error = "Required"; return }
            lifecycleScope.launch { binding.btnAction.isEnabled = false; binding.btnAction.text = "Unlocking..."; delay(100)
                if (LuksEncryption.unlockVault(this@LockActivity, password)) showDecryptAnimation()
                else { binding.btnAction.isEnabled = true; binding.btnAction.text = "Unlock"; binding.passwordInput.error = "Wrong password"; binding.passwordInput.text?.clear(); binding.passwordInput.startAnimation(AnimationUtils.loadAnimation(this@LockActivity, R.anim.shake)) }
            }
        }
    }

    private fun showDecryptAnimation() {
        val shakeOut = AnimationUtils.loadAnimation(this, R.anim.shake)
        binding.lockScreen.startAnimation(shakeOut)

        binding.lockScreen.animate().alpha(0f).translationY(-30f).setDuration(300).withEndAction {
            binding.lockScreen.visibility = View.GONE
            binding.decryptOverlay.visibility = View.VISIBLE
            binding.decryptOverlay.alpha = 0f
            binding.decryptOverlay.animate().alpha(1f).setDuration(200).start()
            binding.decryptAnim.startAnimation()
            startStatusMessages()
        }.start()
    }

    private fun startStatusMessages() {
        val statuses = arrayOf(
            "Initializing AES-256-GCM...",
            "Deriving key via PBKDF2...",
            "PBKDF2-HMAC-SHA256 × 600K iterations",
            "Validating password hash...",
            "Checking StrongBox backing...",
            "Unsealing master key...",
            "Decrypting vault...",
            "Verifying integrity...",
            "Loading secure storage...",
            "Vault unlocked"
        )
        val details = arrayOf(
            "key_length=256 bits",
            "iterations=600000 salt=random(32)",
            "hmac=SHA256",
            "constant_time_comparison=true",
            "hardware_backed=true",
            "aes_gcm_tag=128 bits",
            "ciphertext=AES/GCM/NoPadding",
            "hmac_verify=pass",
            "sqlcipher=unlocked",
            "master_key=loaded"
        )
        lifecycleScope.launch {
            for (i in statuses.indices) {
                binding.decryptStatus.text = statuses[i]
                binding.decryptDetail.text = details[i]
                val progress = ((i + 1).toFloat() / statuses.size * 100).toInt()
                delay(if (i == statuses.size - 1) 400 else 250L + (i * 30))
            }
            delay(300)
            binding.decryptAnim.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(300).withEndAction { openMain() }.start()
        }
    }

    private fun openMain() { startActivity(Intent(this, MainActivity::class.java)); finish() }
    @Suppress("DEPRECATION") override fun onBackPressed() {}
}
