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
    private lateinit var binding: ActivityLockBinding; private var isSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); binding = ActivityLockBinding.inflate(layoutInflater); setContentView(binding.root)
        isSetup = !LuksEncryption.isVaultInitialized(this)
        if (!isSetup && LuksEncryption.isVaultUnlocked()) { openMain(); return }
        setupUI()
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
                try { LuksEncryption.setupVault(this@LockActivity, password); openMain() } catch (e: Exception) { binding.btnAction.isEnabled = true; binding.btnAction.text = "Create Vault"; Toast.makeText(this@LockActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        } else {
            if (password.isEmpty()) { binding.passwordInput.error = "Required"; return }
            lifecycleScope.launch { binding.btnAction.isEnabled = false; binding.btnAction.text = "Unlocking..."; delay(100)
                if (LuksEncryption.unlockVault(this@LockActivity, password)) openMain()
                else { binding.btnAction.isEnabled = true; binding.btnAction.text = "Unlock"; binding.passwordInput.error = "Wrong password"; binding.passwordInput.text?.clear(); binding.passwordInput.startAnimation(AnimationUtils.loadAnimation(this@LockActivity, R.anim.shake)) }
            }
        }
    }

    private fun openMain() { startActivity(Intent(this, MainActivity::class.java)); finish() }
    @Suppress("DEPRECATION") override fun onBackPressed() {}
}
