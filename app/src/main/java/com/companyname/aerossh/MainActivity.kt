package com.companyname.aerossh

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.companyname.aerossh.data.AppDatabase
import com.companyname.aerossh.data.Server
import com.companyname.aerossh.data.ServerRepository
import com.companyname.aerossh.databinding.ActivityMainBinding
import com.companyname.aerossh.ui.ServerAdapter
import com.companyname.aerossh.crypto.BiometricHelper
import com.companyname.aerossh.security.SecurityManager
import com.companyname.aerossh.security.VaultLockManager
import com.companyname.aerossh.ui.ServerDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding; private lateinit var adapter: ServerAdapter
    private val db by lazy { AppDatabase.get(this) }; private val repo by lazy { ServerRepository(db.serverDao()) }; private var biometricDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecurityManager.preventScreenshots(this)
        if (SecurityManager.isDeviceRooted()) Toast.makeText(this, "Root detected. Security may be compromised.", Toast.LENGTH_LONG).show()
        SecurityManager.setupClipboardAutoClear(this, 30_000)
        binding = ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
        if (!biometricDone && BiometricHelper.isAvailable(this)) BiometricHelper.authenticate(this, "AeroSSH", "Verify to access servers", { biometricDone = true }, {}, {})
        setupList(); setupSearch(); setupFab(); setupSettings(); setupImport(); setupQuickConnect(); observeServers()
    }

    private fun setupFab() { binding.fabAdd.setOnClickListener { showAddDialog() } }
    private fun setupSettings() { binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) } }
    private fun setupImport() { binding.btnImport.setOnClickListener { sshConfigLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }) } }

    private val sshConfigLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { uri ->
            lifecycleScope.launch { try { val content = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@launch; val servers = com.companyname.aerossh.sshconfig.SshConfigParser.parseFromFile(content); servers.forEach { repo.insert(it) }; Toast.makeText(this@MainActivity, "Imported ${servers.size} servers", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(this@MainActivity, "Import failed", Toast.LENGTH_SHORT).show() } }
        }
    }

    private fun setupList() {
        adapter = ServerAdapter({ connectToServer(it) }, { showEditDialog(it) }, { deleteServer(it) })
        binding.serverList.layoutManager = LinearLayoutManager(this); binding.serverList.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) { val pos = vh.bindingAdapterPosition; if (pos == RecyclerView.NO_POSITION) return; val s = adapter.currentList[pos]; if (dir == ItemTouchHelper.LEFT) deleteServer(s) else connectToServer(s) }
        }).attachToRecyclerView(binding.serverList)
        binding.serverList.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in))
    }

    private fun setupSearch() { binding.searchEdit.addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { val q = s?.toString()?.trim().orEmpty(); if (q.isEmpty()) observeServers() else lifecycleScope.launch { repo.search(q).collectLatest { adapter.submitList(it); updateEmptyState(it.isEmpty()) } } } override fun afterTextChanged(s: Editable?) {} }) }

    private fun setupQuickConnect() { binding.quickConnectBtn.setOnClickListener { val h = binding.quickHostEdit.text?.toString()?.trim().orEmpty(); val u = binding.quickUserEdit.text?.toString()?.trim().orEmpty(); if (h.isEmpty() || u.isEmpty()) { Toast.makeText(this, "Enter host and username", Toast.LENGTH_SHORT).show(); return@setOnClickListener }; connectToServer(Server(name = h, host = h, port = 22, username = u, password = "")) } }

    private fun observeServers() { lifecycleScope.launch { repo.getAll().collectLatest { adapter.submitList(it); updateEmptyState(it.isEmpty()); binding.quickConnectSection.visibility = if (it.size > 3) View.VISIBLE else View.GONE } } }
    private fun updateEmptyState(empty: Boolean) { binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE; binding.serverList.visibility = if (empty) View.GONE else View.VISIBLE }
    private fun showAddDialog() { ServerDialogFragment { lifecycleScope.launch { repo.insert(it); Toast.makeText(this@MainActivity, "Server saved", Toast.LENGTH_SHORT).show() } }.show(supportFragmentManager, ServerDialogFragment.TAG) }
    private fun showEditDialog(server: Server) { ServerDialogFragment(server) { lifecycleScope.launch { repo.update(it); Toast.makeText(this@MainActivity, "Server updated", Toast.LENGTH_SHORT).show() } }.show(supportFragmentManager, ServerDialogFragment.TAG) }
    private fun deleteServer(server: Server) { lifecycleScope.launch { repo.delete(server); Toast.makeText(this@MainActivity, "Server deleted", Toast.LENGTH_SHORT).show() } }
    private fun connectToServer(server: Server) { lifecycleScope.launch { repo.touchLastConnected(server.id) }; TerminalActivity.start(this, server.host, server.port, server.username, server.password) }

    override fun onResume() { super.onResume(); VaultLockManager.onActivityResumed(this) }
    override fun onStop() { super.onStop(); VaultLockManager.onActivityStopped(this) }
}
