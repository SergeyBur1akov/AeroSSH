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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private var ctrlMode = false

    // Multi-session — password stored as CharArray, not String
    data class Session(
        val id: Int,
        val host: String,
        val port: Int,
        val user: String,
        val pass: CharArray,
        var ssh: SshService? = null,
        var label: String = "",
        var isActive: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = id
    }

    private val sessions = mutableListOf<Session>()
    private var activeSessionId = 0
    private var nextSessionId = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        com.companyname.aerossh.security.SecurityManager.preventScreenshots(this)

        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar()
        setupInput()
        setupSpecialKeys()
        setupSymbolKeys()
        setupTerminal()

        // Decrypt password from intent
        val host = intent.getStringExtra(EXTRA_HOST) ?: return
        val port = intent.getIntExtra(EXTRA_PORT, 22)
        val user = intent.getStringExtra(EXTRA_USER) ?: return
        val encPass = intent.getStringExtra(EXTRA_PASS) ?: ""

        // Decrypt password using master vault key
        val pass = try {
            LuksEncryption.decryptWithMaster(encPass).toCharArray()
        } catch (_: Exception) {
            CharArray(0)
        }

        if (pass.isEmpty()) {
            Toast.makeText(this, "Failed to decrypt credentials", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        addSession(host, port, user, pass)
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnDisconnect.setOnClickListener { disconnectActive() }
        binding.btnNewSession.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        binding.btnSftp.setOnClickListener {
            val session = sessions.find { it.id == activeSessionId } ?: return@setOnClickListener
            val encPass = try {
                LuksEncryption.encryptWithMaster(String(session.pass))
            } catch (_: Exception) { "" }
            SftpActivity.start(
                this,
                host = session.host,
                port = session.port,
                user = session.user,
                pass = encPass
            )
        }
    }

    private fun addSession(host: String, port: Int, user: String, pass: CharArray) {
        val id = nextSessionId++
        val session = Session(
            id = id,
            host = host,
            port = port,
            user = user,
            pass = pass,
            label = "$user@${host.take(12)}"
        )
        sessions.add(session)
        renderTabs()
        switchToSession(id)
        connectSession(session)
    }

    private fun renderTabs() {
        binding.tabsContainer.removeAllViews()
        for (session in sessions) {
            val tabView = LayoutInflater.from(this).inflate(R.layout.item_tab, binding.tabsContainer, false)
            val title = tabView.findViewById<android.widget.TextView>(R.id.tabTitle)
            val indicator = tabView.findViewById<View>(R.id.tabIndicator)
            val closeBtn = tabView.findViewById<View>(R.id.tabClose)

            title.text = session.label
            indicator.visibility = if (session.isActive) View.VISIBLE else View.GONE
            closeBtn.visibility = if (sessions.size > 1) View.VISIBLE else View.GONE
            tabView.isSelected = session.id == activeSessionId
            tabView.setOnClickListener { switchToSession(session.id) }
            closeBtn.setOnClickListener { closeSession(session.id) }
            binding.tabsContainer.addView(tabView)
        }
    }

    private fun switchToSession(id: Int) {
        sessions.find { it.id == activeSessionId }?.isActive = false
        val session = sessions.find { it.id == id } ?: return
        session.isActive = true
        activeSessionId = id
        binding.titleText.text = session.label
        binding.subtitleText.text = "${session.host}:${session.port}"
        binding.terminalView.invalidate()
        renderTabs()
    }

    private fun closeSession(id: Int) {
        val session = sessions.find { it.id == id } ?: return
        session.ssh?.close()
        session.pass.fill('\u0000')  // Clear password from memory
        sessions.remove(session)
        if (sessions.isEmpty()) { finish(); return }
        if (activeSessionId == id) switchToSession(sessions.first().id)
        renderTabs()
    }

    private fun connectSession(session: Session) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val service = SshService(session.host, session.port, session.user, session.pass.copyOf())
                    service.connect(this@TerminalActivity)
                    session.ssh = service

                    service.openShell(
                        onOutput = { data ->
                            binding.terminalView.feedData(data)
                        },
                        onError = { err ->
                            runOnUiThread {
                                binding.subtitleText.text = "ERR: connection error"
                            }
                        }
                    )
                }
            } catch (_: Exception) {
                runOnUiThread {
                    binding.subtitleText.text = "FAILED: connection failed"
                    Toast.makeText(this@TerminalActivity, "Connection failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun disconnectActive() {
        val session = sessions.find { it.id == activeSessionId } ?: return
        session.ssh?.close()
        session.ssh = null
        closeSession(activeSessionId)
    }

    // --- Input handling ---
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1

    private fun setupInput() {
        binding.inputField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendInput(); true } else false
        }
        binding.btnSend.setOnClickListener { sendInput() }

        // Long press = paste
        binding.inputField.setOnLongClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.primaryClip?.let { clip ->
                if (clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString() ?: ""
                    binding.inputField.append(text)
                }
            }
            true
        }

        // Swipe up = history
        binding.inputField.setOnTouchListener(object : View.OnTouchListener {
            private var startY = 0f
            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> startY = event.y
                    android.view.MotionEvent.ACTION_UP -> {
                        if (startY - event.y > 100) { showHistory(); return true }
                    }
                }
                return false
            }
        })
    }

    private fun showHistory() {
        if (commandHistory.isEmpty()) {
            Toast.makeText(this, "No command history", Toast.LENGTH_SHORT).show()
            return
        }
        historyIndex = (historyIndex + 1) % commandHistory.size
        binding.inputField.setText(commandHistory[historyIndex])
        binding.inputField.setSelection(binding.inputField.text?.length ?: 0)
    }

    private fun sendInput() {
        val text = binding.inputField.text?.toString() ?: return
        if (text.isNotBlank()) {
            // Don't store potential passwords in history
            val lower = text.lowercase()
            val looksLikePassword = lower.contains("password") || lower.contains("passwd") ||
                    lower.contains("secret") || lower.contains("token")
            if (!looksLikePassword) {
                commandHistory.add(text)
            }
            historyIndex = commandHistory.size
        }
        val session = sessions.find { it.id == activeSessionId }
        session?.ssh?.sendText(text + "\n")
        binding.inputField.text?.clear()
        hapticClick()
    }

    private fun setupSpecialKeys() {
        binding.keyUp.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_UP) }
        binding.keyDown.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_DOWN) }
        binding.keyLeft.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_LEFT) }
        binding.keyRight.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_RIGHT) }
        binding.keyHome.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_HOME) }
        binding.keyEnd.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_END) }
        binding.keyPgUp.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_PGUP) }
        binding.keyPgDn.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_PGDN) }
        binding.keyTab.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_TAB) }
        binding.keyEsc.setOnClickListener { activeSsh()?.sendKey(SshService.KEY_ESC) }

        binding.keyCtrl.setOnClickListener {
            ctrlMode = !ctrlMode
            binding.keyCtrl.alpha = if (ctrlMode) 1.0f else 0.6f
        }

        binding.inputField.setOnKeyListener { _, keyCode, event ->
            if (ctrlMode && event.action == android.view.KeyEvent.ACTION_DOWN) {
                val ch = event.UnicodeChar
                if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                    activeSsh()?.sendCtrlChar(ch.toChar())
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    private fun setupSymbolKeys() {
        val symbolMap = mapOf(
            R.id.symPipe to "|", R.id.symSlash to "/", R.id.symBackslash to "\\",
            R.id.symDash to "-", R.id.symUnderscore to "_", R.id.symTilde to "~",
            R.id.symAt to "@", R.id.symDot to ".", R.id.symColon to ":"
        )
        for ((id, char) in symbolMap) {
            findViewById<View>(id)?.setOnClickListener { binding.inputField.append(char) }
        }
        binding.symCtrlC.setOnClickListener { activeSsh()?.sendCtrlChar('c') }
        binding.symCtrlD.setOnClickListener { activeSsh()?.sendCtrlChar('d') }
        binding.symCtrlZ.setOnClickListener { activeSsh()?.sendCtrlChar('z') }
        binding.symCtrlL.setOnClickListener { activeSsh()?.sendCtrlChar('l') }
    }

    private fun activeSsh(): SshService? = sessions.find { it.id == activeSessionId }?.ssh

    private fun hapticClick() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun setupTerminal() {
        binding.terminalView.onTextSelected = { text ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        binding.terminalView.onTerminalResize = { cols, rows -> activeSsh()?.resize(cols, rows) }

        // Swipe down = Ctrl+C
        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (velocityY > 2000 && e1 != null && e2.y - e1.y > 200) {
                    activeSsh()?.sendCtrlChar('c')
                    hapticClick()
                    Toast.makeText(this@TerminalActivity, "Ctrl+C", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }
        })
        binding.terminalView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); false }

        val hapticListener = View.OnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) hapticClick()
            false
        }
        binding.keyTab.setOnTouchListener(hapticListener)
        binding.keyEsc.setOnTouchListener(hapticListener)
        binding.keyCtrl.setOnTouchListener(hapticListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        sessions.forEach { session ->
            session.ssh?.close()
            session.pass.fill('\u0000')
        }
        sessions.clear()
    }

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_USER = "user"
        const val EXTRA_PASS = "pass"  // Now holds encrypted password

        fun start(context: Context, host: String, port: Int, user: String, pass: String) {
            // Encrypt password before passing via Intent
            val encPass = try {
                LuksEncryption.encryptWithMaster(pass)
            } catch (_: Exception) {
                return
            }
            val intent = Intent(context, TerminalActivity::class.java).apply {
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_USER, user)
                putExtra(EXTRA_PASS, encPass)
            }
            context.startActivity(intent)
        }
    }
}
