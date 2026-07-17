package com.companyname.aerossh

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.companyname.aerossh.databinding.ActivitySftpBinding
import com.companyname.aerossh.sftp.SftpService
import com.companyname.aerossh.security.LuksEncryption
import com.companyname.aerossh.ui.SftpAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SftpActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySftpBinding; private lateinit var adapter: SftpAdapter
    private var sftp: SftpService? = null; private var currentPath = "/"; private val pathHistory = mutableListOf<String>(); private var selectedEntry: SftpService.SftpEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); com.companyname.aerossh.security.SecurityManager.preventScreenshots(this)
        binding = ActivitySftpBinding.inflate(layoutInflater); setContentView(binding.root); setupUI(); connectAndList()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { if (pathHistory.isNotEmpty()) navigateTo(pathHistory.removeLast()) else finish() }
        binding.btnNewFolder.setOnClickListener { val input = EditText(this).apply { hint = "Folder name"; setPadding(48, 32, 48, 32) }; AlertDialog.Builder(this).setTitle("New Folder").setView(input).setPositiveButton("Create") { _, _ -> val n = input.text.toString().trim(); if (n.isNotEmpty()) lifecycleScope.launch { withContext(Dispatchers.IO) { sftp?.mkdir("$currentPath/$n") }; listFiles() } }.setNegativeButton("Cancel", null).show() }
        binding.btnRefresh.setOnClickListener { listFiles() }
        adapter = SftpAdapter({ pathHistory.add(currentPath); navigateTo(it.path) }, { selectedEntry = it; adapter.setSelected(it.path); binding.actionBar.visibility = View.VISIBLE })
        binding.fileList.layoutManager = LinearLayoutManager(this); binding.fileList.adapter = adapter
        binding.btnDownload.setOnClickListener { downloadSelected() }; binding.btnDelete.setOnClickListener { deleteSelected() }; binding.btnRename.setOnClickListener { renameSelected() }
    }

    private fun connectAndList() {
        val host = intent.getStringExtra(EXTRA_HOST) ?: return; val port = intent.getIntExtra(EXTRA_PORT, 22); val user = intent.getStringExtra(EXTRA_USER) ?: return; val encPass = intent.getStringExtra(EXTRA_PASS) ?: ""
        val pass = try { LuksEncryption.decryptWithMaster(encPass).toCharArray() } catch (_: Exception) { CharArray(0) }
        if (pass.isEmpty()) { Toast.makeText(this, "Failed to decrypt", Toast.LENGTH_SHORT).show(); finish(); return }
        lifecycleScope.launch { try { withContext(Dispatchers.IO) { val ssh = SshService(host, port, user, pass); ssh.connect(this@SftpActivity); val svc = SftpService(ssh); svc.connect(); sftp = svc; currentPath = svc.pwd() }; listFiles() } catch (_: Exception) { Toast.makeText(this@SftpActivity, "SFTP failed", Toast.LENGTH_LONG).show(); finish() } finally { pass.fill('\u0000') } }
    }

    private fun navigateTo(path: String) { currentPath = path; selectedEntry = null; adapter.setSelected(null); binding.actionBar.visibility = View.GONE; listFiles() }
    private fun listFiles() { binding.pathText.text = currentPath; lifecycleScope.launch { try { val files = withContext(Dispatchers.IO) { sftp?.listFiles(currentPath) ?: emptyList() }; val sorted = files.sortedWith(compareByDescending<SftpService.SftpEntry> { it.isDirectory }.thenBy { it.name.lowercase() }); adapter.submitList(sorted); binding.emptyState.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE; binding.fileList.visibility = if (sorted.isEmpty()) View.GONE else View.VISIBLE } catch (_: Exception) { Toast.makeText(this@SftpActivity, "Error listing files", Toast.LENGTH_SHORT).show() } } }

    private fun downloadSelected() { val e = selectedEntry ?: return; if (e.isDirectory) { Toast.makeText(this, "Select a file", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch { try { binding.transferBar.visibility = View.VISIBLE; binding.transferText.text = "Downloading..."; withContext(Dispatchers.IO) { val f = File(cacheDir, "dl_${System.currentTimeMillis()}_${e.name}"); f.outputStream().use { sftp?.download(e.path, it) }; val uri = FileProvider.getUriForFile(this@SftpActivity, "${packageName}.fileprovider", f); startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, contentResolver.getType(uri)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Open with")) }; binding.transferBar.visibility = View.GONE } catch (_: Exception) { binding.transferBar.visibility = View.GONE; Toast.makeText(this@SftpActivity, "Download failed", Toast.LENGTH_SHORT).show() } } }

    private fun deleteSelected() { val e = selectedEntry ?: return; AlertDialog.Builder(this).setTitle("Delete").setMessage("Delete ${e.name}?").setPositiveButton("Delete") { _, _ -> lifecycleScope.launch { withContext(Dispatchers.IO) { if (e.isDirectory) sftp?.rmdir(e.path) else sftp?.rm(e.path) }; selectedEntry = null; binding.actionBar.visibility = View.GONE; listFiles() } }.setNegativeButton("Cancel", null).show() }

    private fun renameSelected() { val e = selectedEntry ?: return; val input = EditText(this).apply { setText(e.name); setPadding(48, 32, 48, 32) }; AlertDialog.Builder(this).setTitle("Rename").setView(input).setPositiveButton("Rename") { _, _ -> val n = input.text.toString().trim(); if (n.isNotEmpty() && n != e.name) lifecycleScope.launch { withContext(Dispatchers.IO) { sftp?.rename(e.path, "$currentPath/$n") }; selectedEntry = null; binding.actionBar.visibility = View.GONE; listFiles() } }.setNegativeButton("Cancel", null).show() }

    override fun onDestroy() { super.onDestroy(); sftp?.close() }
    companion object { const val EXTRA_HOST = "host"; const val EXTRA_PORT = "port"; const val EXTRA_USER = "user"; const val EXTRA_PASS = "pass"
        fun start(context: Context, host: String, port: Int, user: String, pass: String) { val ep = try { LuksEncryption.encryptWithMaster(pass) } catch (_: Exception) { return }; context.startActivity(Intent(context, SftpActivity::class.java).apply { putExtra(EXTRA_HOST, host); putExtra(EXTRA_PORT, port); putExtra(EXTRA_USER, user); putExtra(EXTRA_PASS, ep) }) }
    }
}
