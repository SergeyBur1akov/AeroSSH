package com.companyname.aerossh

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.companyname.aerossh.databinding.ActivitySettingsBinding
import com.companyname.aerossh.ui.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        // Theme
        val theme = themeModeToIndex(themeMode)
        binding.themeSpinner.setSelection(theme)

        // Font size
        binding.fontSizeSeekBar.progress = ((fontSize - 10f) / 22f * 100).toInt()
        binding.fontSizeValue.text = "${fontSize.toInt()}sp"

        // Line spacing
        binding.lineSpacingSeekBar.progress = ((lineSpacing - 1.0f) / 1.5f * 100).toInt()
        binding.lineSpacingValue.text = "%.1fx".format(lineSpacing)

        // Scrollback
        val scrollbackIndex = scrollbackToIndex(scrollbackLines)
        binding.scrollbackSpinner.setSelection(scrollbackIndex)

        // Cursor
        val cursorIndex = cursorToIndex(cursorStyle)
        binding.cursorSpinner.setSelection(cursorIndex)

        // Connection
        val timeoutIndex = timeoutToIndex(connectionTimeout)
        binding.timeoutSpinner.setSelection(timeoutIndex)

        binding.keepaliveSwitch.isChecked = keepaliveInterval > 0
        binding.autoReconnectSwitch.isChecked = autoReconnect

        // Encoding
        val encIndex = encodingToIndex(encoding)
        binding.encodingSpinner.setSelection(encIndex)

        // Log
        binding.logSwitch.isChecked = logSessions
    }

    private fun setupListeners() {
        binding.themeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                themeMode = indexToThemeMode(pos)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.fontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = 10f + (progress / 100f * 22f)
                fontSize = size
                binding.fontSizeValue.text = "${size.toInt()}sp"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.lineSpacingSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val spacing = 1.0f + (progress / 100f * 1.5f)
                lineSpacing = spacing
                binding.lineSpacingValue.text = "%.1fx".format(spacing)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.scrollbackSpinner.onItemSelectedListener = simpleSpinner { scrollbackLines = indexToScrollback(it) }
        binding.cursorSpinner.onItemSelectedListener = simpleSpinner { cursorStyle = indexToCursor(it) }
        binding.timeoutSpinner.onItemSelectedListener = simpleSpinner { connectionTimeout = indexToTimeout(it) }
        binding.encodingSpinner.onItemSelectedListener = simpleSpinner { encoding = indexToEncoding(it) }

        binding.keepaliveSwitch.setOnCheckedChangeListener { _, checked ->
            keepaliveInterval = if (checked) 30 else 0
        }
        binding.autoReconnectSwitch.setOnCheckedChangeListener { _, checked ->
            autoReconnect = checked
        }
        binding.logSwitch.setOnCheckedChangeListener { _, checked ->
            logSessions = checked
        }

        binding.exportBtn.setOnClickListener { exportLogs() }
        binding.btnChangePassword.setOnClickListener { showChangePasswordDialog() }
        binding.btnWipeData.setOnClickListener { showWipeDataDialog() }
    }

    private fun showChangePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(view)
            .create()

        val oldPass = view.findViewById<android.widget.EditText>(R.id.oldPasswordInput)
        val newPass = view.findViewById<android.widget.EditText>(R.id.newPasswordInput)
        val confirmPass = view.findViewById<android.widget.EditText>(R.id.confirmPasswordInput)
        val btnChange = view.findViewById<android.widget.Button>(R.id.btnChange)
        val btnCancel = view.findViewById<android.widget.Button>(R.id.btnCancelDialog)

        btnChange.setOnClickListener {
            val old = oldPass.text.toString()
            val new = newPass.text.toString()
            val confirm = confirmPass.text.toString()

            if (new.length < 8) {
                newPass.error = "Min 8 characters"
                return@setOnClickListener
            }
            if (!new.any { it.isUpperCase() }) {
                newPass.error = "Need uppercase letter"
                return@setOnClickListener
            }
            if (!new.any { it.isLowerCase() }) {
                newPass.error = "Need lowercase letter"
                return@setOnClickListener
            }
            if (!new.any { it.isDigit() }) {
                newPass.error = "Need digit"
                return@setOnClickListener
            }
            if (new != confirm) {
                confirmPass.error = "Mismatch"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val success = com.companyname.aerossh.security.LuksEncryption.changePassword(this@SettingsActivity, old, new)
                if (success) {
                    Toast.makeText(this@SettingsActivity, "Password changed", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    oldPass.error = "Wrong password"
                }
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showWipeDataDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("⚠ Wipe All Data")
            .setMessage("This will permanently delete ALL servers, keys, and settings. This cannot be undone.")
            .setPositiveButton("Wipe") { _, _ ->
                com.companyname.aerossh.security.LuksEncryption.wipeVault(this)
                filesDir.deleteRecursively()
                val intent = android.content.Intent(this, com.companyname.aerossh.LockActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportLogs() {
        lifecycleScope.launch {
            try {
                val logDir = File(filesDir, "logs")
                if (!logDir.exists()) {
                    Toast.makeText(this@SettingsActivity, "No logs to export", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val files = logDir.listFiles() ?: emptyArray()
                if (files.isEmpty()) {
                    Toast.makeText(this@SettingsActivity, "No logs to export", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val exportFile = File(cacheDir, "aerossh_logs_$timestamp.txt")

                withContext(Dispatchers.IO) {
                    exportFile.bufferedWriter().use { writer ->
                        for (file in files.sortedByDescending { it.name }) {
                            writer.write("=== ${file.name} ===\n")
                            writer.write(file.readText())
                            writer.write("\n\n")
                        }
                    }
                }

                val uri = FileProvider.getUriForFile(
                    this@SettingsActivity,
                    "${packageName}.fileprovider",
                    exportFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Export logs"))
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // --- Pref accessors (via Prefs helper) ---
    private var themeMode: String
        get() = com.companyname.aerossh.ui.Prefs.getTheme(this)
        set(value) { com.companyname.aerossh.ui.Prefs.setTheme(this, value) }
    private var fontSize: Float
        get() = com.companyname.aerossh.ui.Prefs.getFontSize(this)
        set(value) { com.companyname.aerossh.ui.Prefs.setFontSize(this, value) }
    private var lineSpacing: Float
        get() = com.companyname.aerossh.ui.Prefs.getLineSpacing(this)
        set(value) { com.companyname.aerossh.ui.Prefs.setLineSpacing(this, value) }
    private var scrollbackLines: Int
        get() = com.companyname.aerossh.ui.Prefs.getScrollback(this)
        set(value) { com.companyname.aerossh.ui.Prefs.setScrollback(this, value) }
    private var cursorStyle: String
        get() = com.companyname.aerossh.ui.Prefs.getCursor(this)
        set(value) { com.companyname.aerossh.ui.Prefs.setCursor(this, value) }
    private var connectionTimeout: Int
        get() = com.companyname.aerossh.ui.Prefs.getTimeout(this)
        set(value) { com.companyname.aerossh.ui.Prefs.setTimeout(this, value) }
    private var keepaliveInterval: Int
        get() = com.companyname.aerossh.ui.Prefs.getKeepalive(this)
        set(value) { com.companyname.aerossh.ui.Prefs.setKeepalive(this, value) }
    private var autoReconnect: Boolean
        get() = com.companyname.aerossh.ui.Prefs.getAutoReconnect(this)
        set(value) { com.companyname.aerossh.ui.Prefs.setAutoReconnect(this, value) }
    private var encoding: String
        get() = com.companyname.aerossh.ui.Prefs.getEncoding(this)
        set(value) { com.companyname.aerossh.ui.Prefs.setEncoding(this, value) }
    private var logSessions: Boolean
        get() = com.companyname.aerossh.ui.Prefs.getLogSessions(this)
        set(value) { com.companyname.aerossh.ui.Prefs.setLogSessions(this, value) }

    // --- Mappers ---
    private fun themeModeToIndex(m: String) = when (m) { "dark" -> 0; "oled" -> 1; "light" -> 2; else -> 0 }
    private fun indexToThemeMode(i: Int) = when (i) { 0 -> "dark"; 1 -> "oled"; 2 -> "light"; else -> "dark" }
    private fun scrollbackToIndex(s: Int) = when (s) { 1000 -> 0; 5000 -> 1; 10000 -> 2; else -> 1 }
    private fun indexToScrollback(i: Int) = when (i) { 0 -> 1000; 1 -> 5000; 2 -> 10000; else -> 5000 }
    private fun cursorToIndex(c: String) = when (c) { "block" -> 0; "bar" -> 1; "underline" -> 2; else -> 0 }
    private fun indexToCursor(i: Int) = when (i) { 0 -> "block"; 1 -> "bar"; 2 -> "underline"; else -> "block" }
    private fun timeoutToIndex(t: Int) = when (t) { 5 -> 0; 10 -> 1; 30 -> 2; 60 -> 3; else -> 2 }
    private fun indexToTimeout(i: Int) = when (i) { 0 -> 5; 1 -> 10; 2 -> 30; 3 -> 60; else -> 30 }
    private fun encodingToIndex(e: String) = when (e) { "UTF-8" -> 0; "KOI8-R" -> 1; "CP1251" -> 2; else -> 0 }
    private fun indexToEncoding(i: Int) = when (i) { 0 -> "UTF-8"; 1 -> "KOI8-R"; 2 -> "CP1251"; else -> "UTF-8" }

    private fun simpleSpinner(callback: (Int) -> Unit) = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
            callback(pos)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
}
