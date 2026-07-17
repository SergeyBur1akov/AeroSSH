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

    private lateinit var binding: ActivitySftpBinding
    private lateinit var adapter: SftpAdapter
    private var sftp: SftpService? = null
    private var currentPath = "/"
    private val pathHistory = mutableListOf<String>()
    private var selectedEntry: SftpService.SftpEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.companyname.aerossh.security.SecurityManager.preventScreenshots(this)
        binding = ActivitySftpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUI()
        connectAndList()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            if (pathHistory.isNotEmpty()) navigateTo(pathHistory.removeLast())
            else finish()
        }
        binding.btnNewFolder.setOnClickListener { showNewFolderDialog() }
        binding.btnRefresh.setOnClickListener { listFiles() }

        adapter = SftpAdapter(
            onOpen = { entry ->
                pathHistory.add(currentPath)
                navigateTo(entry.path)
            },
            onSelect = { entry ->
                selectedEntry = entry
                adapter.setSelected(entry.path)
                binding.actionBar.visibility = View.VISIBLE
            }
        )
        binding.fileList.layoutManager = LinearLayoutManager(this)
        binding.fileList.adapter = adapter
        binding.btnDownload.setOnClickListener { downloadSelected() }
        binding.btnDelete.setOnClickListener { deleteSelected() }
        binding.btnRename.setOnClickListener { renameSelected() }
    }

    private fun connectAndList() {
        val host = intent.getStringExtra(EXTRA_HOST) ?: return
        val port = intent.getIntExtra(EXTRA_PORT, 22)
        val user = intent.getStringExtra(EXTRA_USER) ?: return
        val encPass = intent.getStringExtra(EXTRA_PASS) ?: ""

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

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val ssh = SshService(host, port, user, pass)
                    ssh.connect(this@SftpActivity)
                    val service = SftpService(ssh)
                    service.connect()
                    sftp = service
                    currentPath = service.pwd()
                }
                listFiles()
            } catch (_: Exception) {
                Toast.makeText(this@SftpActivity, "SFTP connection failed", Toast.LENGTH_LONG).show()
                finish()
            } finally {
                pass.fill('\u0000')
            }
        }
    }

    private fun navigateTo(path: String) {
        currentPath = path
        selectedEntry = null
        adapter.setSelected(null)
        binding.actionBar.visibility = View.GONE
        listFiles()
    }

    private fun listFiles() {
        binding.pathText.text = currentPath
        lifecycleScope.launch {
            try {
                val files = withContext(Dispatchers.IO) { sftp?.listFiles(currentPath) ?: emptyList() }
                val sorted = files.sortedWith(
                    compareByDescending<SftpService.SftpEntry> { it.isDirectory }.thenBy { it.name.lowercase() }
                )
                adapter.submitList(sorted)
                binding.emptyState.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
                binding.fileList.visibility = if (sorted.isEmpty()) View.GONE else View.VISIBLE
            } catch (_: Exception) {
                Toast.makeText(this@SftpActivity, "Failed to list files", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showNewFolderDialog() {
        val input = EditText(this).apply { hint = "Folder name"; setPadding(48, 32, 48, 32) }
        AlertDialog.Builder(this).setTitle("New Folder").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { sftp?.mkdir("$currentPath/$name") }
                        listFiles()
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun downloadSelected() {
        val entry = selectedEntry ?: return
        if (entry.isDirectory) {
            Toast.makeText(this, "Select a file, not a folder", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                binding.transferBar.visibility = View.VISIBLE
                binding.transferText.text = "Downloading..."
                withContext(Dispatchers.IO) {
                    val file = File(cacheDir, "dl_${System.currentTimeMillis()}_${entry.name}")
                    file.outputStream().use { out -> sftp?.download(entry.path, out) }
                    val uri = FileProvider.getUriForFile(this@SftpActivity, "${packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, contentResolver.getType(uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Open with"))
                }
                binding.transferBar.visibility = View.GONE
            } catch (_: Exception) {
                binding.transferBar.visibility = View.GONE
                Toast.makeText(this@SftpActivity, "Download failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteSelected() {
        val entry = selectedEntry ?: return
        AlertDialog.Builder(this).setTitle("Delete").setMessage("Delete ${entry.name}?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        if (entry.isDirectory) sftp?.rmdir(entry.path) else sftp?.rm(entry.path)
                    }
                    selectedEntry = null
                    binding.actionBar.visibility = View.GONE
                    listFiles()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun renameSelected() {
        val entry = selectedEntry ?: return
        val input = EditText(this).apply { setText(entry.name); setPadding(48, 32, 48, 32) }
        AlertDialog.Builder(this).setTitle("Rename").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != entry.name) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { sftp?.rename(entry.path, "$currentPath/$newName") }
                        selectedEntry = null
                        binding.actionBar.visibility = View.GONE
                        listFiles()
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        sftp?.close()
    }

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_USER = "user"
        const val EXTRA_PASS = "pass"

        fun start(context: Context, host: String, port: Int, user: String, pass: String) {
            val encPass = try { LuksEncryption.encryptWithMaster(pass) } catch (_: Exception) { return }
            val intent = Intent(context, SftpActivity::class.java).apply {
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_USER, user)
                putExtra(EXTRA_PASS, encPass)
            }
            context.startActivity(intent)
        }
    }
}
