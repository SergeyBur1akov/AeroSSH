package com.companyname.aerossh

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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); binding = ActivitySettingsBinding.inflate(layoutInflater); setContentView(binding.root); supportActionBar?.setDisplayHomeAsUpEnabled(true); supportActionBar?.title = "Settings"; loadSettings(); setupListeners() }

    private fun loadSettings() {
        binding.themeSpinner.setSelection(themeModeToIndex(Prefs.getTheme(this)))
        binding.fontSizeSeekBar.progress = ((Prefs.getFontSize(this) - 10f) / 22f * 100).toInt(); binding.fontSizeValue.text = "${Prefs.getFontSize(this).toInt()}sp"
        binding.lineSpacingSeekBar.progress = ((Prefs.getLineSpacing(this) - 1.0f) / 1.5f * 100).toInt(); binding.lineSpacingValue.text = "%.1fx".format(Prefs.getLineSpacing(this))
        binding.scrollbackSpinner.setSelection(scrollbackToIndex(Prefs.getScrollback(this))); binding.cursorSpinner.setSelection(cursorToIndex(Prefs.getCursor(this)))
        binding.timeoutSpinner.setSelection(timeoutToIndex(Prefs.getTimeout(this))); binding.encodingSpinner.setSelection(encodingToIndex(Prefs.getEncoding(this)))
        binding.keepaliveSwitch.isChecked = Prefs.getKeepalive(this) > 0; binding.autoReconnectSwitch.isChecked = Prefs.getAutoReconnect(this); binding.logSwitch.isChecked = Prefs.getLogSessions(this)
    }

    private fun setupListeners() {
        binding.themeSpinner.onItemSelectedListener = simpleSpinner { Prefs.setTheme(this, indexToThemeMode(it)) }
        binding.fontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { val s = 10f + (p / 100f * 22f); Prefs.setFontSize(this@SettingsActivity, s); binding.fontSizeValue.text = "${s.toInt()}sp" }; override fun onStartTrackingTouch(sb: SeekBar?) {}; override fun onStopTrackingTouch(sb: SeekBar?) {} })
        binding.lineSpacingSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { val s = 1.0f + (p / 100f * 1.5f); Prefs.setLineSpacing(this@SettingsActivity, s); binding.lineSpacingValue.text = "%.1fx".format(s) }; override fun onStartTrackingTouch(sb: SeekBar?) {}; override fun onStopTrackingTouch(sb: SeekBar?) {} })
        binding.scrollbackSpinner.onItemSelectedListener = simpleSpinner { Prefs.setScrollback(this, indexToScrollback(it)) }
        binding.cursorSpinner.onItemSelectedListener = simpleSpinner { Prefs.setCursor(this, indexToCursor(it)) }
        binding.timeoutSpinner.onItemSelectedListener = simpleSpinner { Prefs.setTimeout(this, indexToTimeout(it)) }
        binding.encodingSpinner.onItemSelectedListener = simpleSpinner { Prefs.setEncoding(this, indexToEncoding(it)) }
        binding.keepaliveSwitch.setOnCheckedChangeListener { _, c -> Prefs.setKeepalive(this, if (c) 30 else 0) }
        binding.autoReconnectSwitch.setOnCheckedChangeListener { _, c -> Prefs.setAutoReconnect(this, c) }
        binding.logSwitch.setOnCheckedChangeListener { _, c -> Prefs.setLogSessions(this, c) }
        binding.exportBtn.setOnClickListener { exportLogs() }
        binding.btnChangePassword.setOnClickListener { showChangePasswordDialog() }
        binding.btnWipeData.setOnClickListener { android.app.AlertDialog.Builder(this).setTitle("Wipe All Data").setMessage("Permanently delete ALL data. Cannot be undone.").setPositiveButton("Wipe") { _, _ -> com.companyname.aerossh.security.LuksEncryption.wipeVault(this); filesDir.deleteRecursively(); startActivity(android.content.Intent(this, LockActivity::class.java).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK }) }.setNegativeButton("Cancel", null).show() }
    }

    private fun showChangePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null); val dialog = android.app.AlertDialog.Builder(this).setView(view).create()
        val op = view.findViewById<android.widget.EditText>(R.id.oldPasswordInput); val np = view.findViewById<android.widget.EditText>(R.id.newPasswordInput); val cp = view.findViewById<android.widget.EditText>(R.id.confirmPasswordInput)
        view.findViewById<android.widget.Button>(R.id.btnChange).setOnClickListener {
            val o = op.text.toString(); val n = np.text.toString(); val c = cp.text.toString()
            if (n.length < 8) { np.error = "Min 8"; return@setOnClickListener }; if (!n.any { it.isUpperCase() }) { np.error = "Need uppercase"; return@setOnClickListener }; if (!n.any { it.isLowerCase() }) { np.error = "Need lowercase"; return@setOnClickListener }; if (!n.any { it.isDigit() }) { np.error = "Need digit"; return@setOnClickListener }; if (n != c) { cp.error = "Mismatch"; return@setOnClickListener }
            lifecycleScope.launch { if (com.companyname.aerossh.security.LuksEncryption.changePassword(this@SettingsActivity, o, n)) { Toast.makeText(this@SettingsActivity, "Changed", Toast.LENGTH_SHORT).show(); dialog.dismiss() } else op.error = "Wrong password" }
        }
        view.findViewById<android.widget.Button>(R.id.btnCancelDialog).setOnClickListener { dialog.dismiss() }; dialog.show()
    }

    private fun exportLogs() { lifecycleScope.launch { try { val ld = File(filesDir, "logs"); if (!ld.exists()) { Toast.makeText(this@SettingsActivity, "No logs", Toast.LENGTH_SHORT).show(); return@launch }; val files = ld.listFiles() ?: emptyArray(); if (files.isEmpty()) { Toast.makeText(this@SettingsActivity, "No logs", Toast.LENGTH_SHORT).show(); return@launch }; val ef = File(cacheDir, "logs_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt")
        withContext(Dispatchers.IO) { ef.bufferedWriter().use { w -> files.sortedByDescending { it.name }.forEach { w.write("=== ${it.name} ===\n"); w.write(it.readText()); w.write("\n\n") } } }
        startActivity(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_STREAM, FileProvider.getUriForFile(this@SettingsActivity, "${packageName}.fileprovider", ef)); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }.let { android.content.Intent.createChooser(it, "Export logs") }) } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "Export failed", Toast.LENGTH_SHORT).show() } } }

    override fun onOptionsItemSelected(item: MenuItem) = if (item.itemId == android.R.id.home) { finish(); true } else super.onOptionsItemSelected(item)

    private fun themeModeToIndex(m: String) = when (m) { "dark" -> 0; "oled" -> 1; "light" -> 2; else -> 0 }
    private fun indexToThemeMode(i: Int) = when (i) { 0 -> "dark"; 1 -> "oled"; 2 -> "light"; else -> "dark" }
    private fun scrollbackToIndex(s: Int) = when (s) { 1000 -> 0; 5000 -> 1; 10000 -> 2; else -> 1 }; private fun indexToScrollback(i: Int) = when (i) { 0 -> 1000; 1 -> 5000; 2 -> 10000; else -> 5000 }
    private fun cursorToIndex(c: String) = when (c) { "block" -> 0; "bar" -> 1; "underline" -> 2; else -> 0 }; private fun indexToCursor(i: Int) = when (i) { 0 -> "block"; 1 -> "bar"; 2 -> "underline"; else -> "block" }
    private fun timeoutToIndex(t: Int) = when (t) { 5 -> 0; 10 -> 1; 30 -> 2; 60 -> 3; else -> 2 }; private fun indexToTimeout(i: Int) = when (i) { 0 -> 5; 1 -> 10; 2 -> 30; 3 -> 60; else -> 30 }
    private fun encodingToIndex(e: String) = when (e) { "UTF-8" -> 0; "KOI8-R" -> 1; "CP1251" -> 2; else -> 0 }; private fun indexToEncoding(i: Int) = when (i) { 0 -> "UTF-8"; 1 -> "KOI8-R"; 2 -> "CP1251"; else -> "UTF-8" }
    private fun simpleSpinner(cb: (Int) -> Unit) = object : android.widget.AdapterView.OnItemSelectedListener { override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { cb(pos) }; override fun onNothingSelected(p: android.widget.AdapterView<*>?) {} }
}
