package com.companyname.aerossh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.companyname.aerossh.databinding.ActivityTerminalBinding
import com.companyname.aerossh.security.LuksEncryption
import com.companyname.aerossh.security.SecurityManager
import com.companyname.aerossh.security.VaultLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTerminalBinding; private var ctrlMode = false
    data class Session(val id: Int, val host: String, val port: Int, val user: String, val pass: CharArray, var ssh: SshService? = null, var label: String = "", var isActive: Boolean = false) { override fun equals(other: Any?) = this === other; override fun hashCode() = id }
    private val sessions = mutableListOf<Session>(); private var activeSessionId = 0; private var nextSessionId = 1
    private val commandHistory = mutableListOf<String>(); private var historyIndex = -1
    private val onVaultLock: () -> Unit = { sessions.forEach { it.ssh?.close(); it.pass.fill('\u0000') }; sessions.clear(); runOnUiThread { finish() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        com.companyname.aerossh.security.SecurityManager.preventScreenshots(this)
        binding = ActivityTerminalBinding.inflate(layoutInflater); setContentView(binding.root)
        SecurityManager.setupClipboardAutoClear(this, 30_000)
        VaultLockManager.addLockListener(onVaultLock)
        setupTopBar(); setupInput(); setupSpecialKeys(); setupSymbolKeys(); setupTerminal()
        val host = intent.getStringExtra(EXTRA_HOST) ?: run { finish(); return }; val port = intent.getIntExtra(EXTRA_PORT, 22); val user = intent.getStringExtra(EXTRA_USER) ?: run { finish(); return }
        val encPass = intent.getStringExtra(EXTRA_PASS) ?: ""; val pass = try { LuksEncryption.decryptWithMaster(encPass).toCharArray() } catch (_: Exception) { CharArray(0) }
        if (pass.isEmpty()) { Toast.makeText(this, "Failed to decrypt credentials", Toast.LENGTH_SHORT).show(); finish(); return }
        addSession(host, port, user, pass)
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener { finish() }; binding.btnDisconnect.setOnClickListener { disconnectActive() }
        binding.btnNewSession.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        binding.btnSftp.setOnClickListener { val s = sessions.find { it.id == activeSessionId } ?: return@setOnClickListener; val ep = try { LuksEncryption.encryptWithMaster(String(s.pass)) } catch (_: Exception) { "" }; SftpActivity.start(this, s.host, s.port, s.user, ep) }
    }

    private fun addSession(host: String, port: Int, user: String, pass: CharArray) { val id = nextSessionId++; val s = Session(id, host, port, user, pass, label = "$user@${host.take(12)}"); sessions.add(s); renderTabs(); switchToSession(id); connectSession(s) }
    private fun renderTabs() { binding.tabsContainer.removeAllViews(); for (s in sessions) { val v = LayoutInflater.from(this).inflate(R.layout.item_tab, binding.tabsContainer, false); v.findViewById<android.widget.TextView>(R.id.tabTitle).text = s.label; v.findViewById<View>(R.id.tabIndicator).visibility = if (s.isActive) View.VISIBLE else View.GONE; v.findViewById<View>(R.id.tabClose).visibility = if (sessions.size > 1) View.VISIBLE else View.GONE; v.isSelected = s.id == activeSessionId; v.setOnClickListener { switchToSession(s.id) }; v.findViewById<View>(R.id.tabClose).setOnClickListener { closeSession(s.id) }; binding.tabsContainer.addView(v) } }
    private fun switchToSession(id: Int) { sessions.find { it.id == activeSessionId }?.isActive = false; val s = sessions.find { it.id == id } ?: return; s.isActive = true; activeSessionId = id; binding.titleText.text = s.label; binding.subtitleText.text = "${s.host}:${s.port}"; binding.terminalView.invalidate(); renderTabs() }
    private fun closeSession(id: Int) { val s = sessions.find { it.id == id } ?: return; s.ssh?.close(); s.pass.fill('\u0000'); sessions.remove(s); if (sessions.isEmpty()) { finish(); return }; if (activeSessionId == id) switchToSession(sessions.first().id); renderTabs() }

    private fun connectSession(session: Session) {
        lifecycleScope.launch { try { withContext(Dispatchers.IO) { val svc = SshService(session.host, session.port, session.user, session.pass.copyOf()); svc.connect(this@TerminalActivity); session.ssh = svc; svc.openShell({ binding.terminalView.feedData(it) }, { runOnUiThread { binding.subtitleText.text = "ERR: connection error" } }) }
        } catch (_: Exception) { runOnUiThread { binding.subtitleText.text = "FAILED"; Toast.makeText(this@TerminalActivity, "Connection failed", Toast.LENGTH_LONG).show() } } }
    }

    private fun disconnectActive() { val s = sessions.find { it.id == activeSessionId } ?: return; s.ssh?.close(); s.ssh = null; closeSession(activeSessionId) }

    private fun setupInput() {
        binding.inputField.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { sendInput(); true } else false }
        binding.btnSend.setOnClickListener { sendInput() }
        binding.inputField.setOnLongClickListener { val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.primaryClip?.let { if (it.itemCount > 0) binding.inputField.append(it.getItemAt(0).text?.toString() ?: "") }; true }
        binding.inputField.setOnTouchListener(object : View.OnTouchListener { private var sy = 0f; override fun onTouch(v: View, e: android.view.MotionEvent): Boolean { when (e.action) { android.view.MotionEvent.ACTION_DOWN -> sy = e.y; android.view.MotionEvent.ACTION_UP -> { if (sy - e.y > 100) { showHistory(); return true } } }; return false } })
    }

    private fun showHistory() { if (commandHistory.isEmpty()) { Toast.makeText(this, "No history", Toast.LENGTH_SHORT).show(); return }; historyIndex = (historyIndex + 1) % commandHistory.size; binding.inputField.setText(commandHistory[historyIndex]); binding.inputField.setSelection(binding.inputField.text?.length ?: 0) }

    private fun sendInput() { val t = binding.inputField.text?.toString() ?: return; if (t.isNotBlank()) { val l = t.lowercase(); if (!l.contains("password") && !l.contains("passwd") && !l.contains("secret")) { commandHistory.add(t); historyIndex = commandHistory.size } }; sessions.find { it.id == activeSessionId }?.ssh?.sendText(t + "\n"); binding.inputField.text?.clear(); hapticClick() }

    private fun setupSpecialKeys() {
        binding.keyUp.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_UP) }; binding.keyDown.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_DOWN) }
        binding.keyLeft.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_LEFT) }; binding.keyRight.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_RIGHT) }
        binding.keyHome.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_HOME) }; binding.keyEnd.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_END) }
        binding.keyPgUp.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_PGUP) }; binding.keyPgDn.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_PGDN) }
        binding.keyTab.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_TAB) }; binding.keyEsc.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_ESC) }
        binding.keyCtrl.setOnClickListener { ctrlMode = !ctrlMode; binding.keyCtrl.alpha = if (ctrlMode) 1.0f else 0.6f }
        binding.inputField.setOnKeyListener { _, kc, ev -> if (ctrlMode && ev.action == android.view.KeyEvent.ACTION_DOWN) { val ch = ev.unicodeChar; if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) { activeSsh()?.sendCtrlChar(ch.toChar()); return@setOnKeyListener true } }; false }
    }

    private fun setupSymbolKeys() { val sm = mapOf(R.id.symPipe to "|", R.id.symSlash to "/", R.id.symBackslash to "\\", R.id.symDash to "-", R.id.symUnderscore to "_", R.id.symTilde to "~", R.id.symAt to "@", R.id.symDot to ".", R.id.symColon to ":"); for ((id, c) in sm) findViewById<View>(id)?.setOnClickListener { binding.inputField.append(c) }; binding.symCtrlC.setOnClickListener { activeSsh()?.sendCtrlChar('c') }; binding.symCtrlD.setOnClickListener { activeSsh()?.sendCtrlChar('d') }; binding.symCtrlZ.setOnClickListener { activeSsh()?.sendCtrlChar('z') }; binding.symCtrlL.setOnClickListener { activeSsh()?.sendCtrlChar('l') } }

    private fun activeSsh(): SshService? = sessions.find { it.id == activeSessionId }?.ssh
    private fun hapticClick() { (getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE)) }

    private fun setupTerminal() {
        binding.terminalView.onTextSelected = { val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("terminal", it)); Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show() }
        binding.terminalView.onTerminalResize = { c, r -> activeSsh()?.resize(c, r) }
        val gd = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean { if (vy > 2000 && e1 != null && e2.y - e1.y > 200) { activeSsh()?.sendCtrlChar('c'); hapticClick(); Toast.makeText(this@TerminalActivity, "Ctrl+C", Toast.LENGTH_SHORT).show(); return true }; return false }
        })
        binding.terminalView.setOnTouchListener { _, e -> gd.onTouchEvent(e); false }
        val hl = View.OnTouchListener { _, e -> if (e.action == android.view.MotionEvent.ACTION_DOWN) hapticClick(); false }
        binding.keyTab.setOnTouchListener(hl); binding.keyEsc.setOnTouchListener(hl); binding.keyCtrl.setOnTouchListener(hl)
    }

    override fun onResume() { super.onResume(); VaultLockManager.onActivityResumed(this) }
    override fun onStop() { super.onStop(); VaultLockManager.onActivityStopped(this) }
    override fun onDestroy() { super.onDestroy(); VaultLockManager.removeLockListener(onVaultLock); sessions.forEach { it.ssh?.close(); it.pass.fill('\u0000') }; sessions.clear() }

    companion object { const val EXTRA_HOST = "host"; const val EXTRA_PORT = "port"; const val EXTRA_USER = "user"; const val EXTRA_PASS = "pass"
        fun start(context: Context, host: String, port: Int, user: String, pass: String) { val ep = try { LuksEncryption.encryptWithMaster(pass) } catch (_: Exception) { return }; context.startActivity(Intent(context, TerminalActivity::class.java).apply { putExtra(EXTRA_HOST, host); putExtra(EXTRA_PORT, port); putExtra(EXTRA_USER, user); putExtra(EXTRA_PASS, ep) }) }
    }
}
