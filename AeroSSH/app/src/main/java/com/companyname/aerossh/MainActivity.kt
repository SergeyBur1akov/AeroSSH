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
import com.companyname.aerossh.ui.ServerDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ServerAdapter
    private val db by lazy { AppDatabase.get(this) }
    private val repo by lazy { ServerRepository(db.serverDao()) }
    private var biometricDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Security: prevent screenshots
        SecurityManager.preventScreenshots(this)

        // Security: warn if rooted
        if (SecurityManager.isDeviceRooted()) {
            Toast.makeText(this, "⚠️ Root detected. Security may be compromised.", Toast.LENGTH_LONG).show()
        }

        // Security: auto-clear clipboard
        SecurityManager.setupClipboardAutoClear(this, 30_000)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!biometricDone && BiometricHelper.isAvailable(this)) {
            BiometricHelper.authenticate(
                activity = this,
                title = "AeroSSH",
                subtitle = "Verify to access your servers",
                onSuccess = {
                    biometricDone = true
                },
                onError = { err ->
                    Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                    biometricDone = true
                }
            )
        }

        setupList()
        setupSearch()
        setupFab()
        setupSettings()
        setupImport()
        setupQuickConnect()
        observeServers()
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddDialog()
        }
    }

    private fun setupImport() {
        binding.btnImport.setOnClickListener { importSshConfig() }
    }

    private fun importSshConfig() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        sshConfigLauncher.launch(intent)
    }

    private val sshConfigLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                lifecycleScope.launch {
                    try {
                        val content = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@launch
                        val servers = com.companyname.aerossh.sshconfig.SshConfigParser.parseFromFile(content)
                        var imported = 0
                        for (server in servers) {
                            repo.insert(server)
                            imported++
                        }
                        Toast.makeText(this@MainActivity, "Imported $imported servers", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupSettings() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupList() {
        adapter = ServerAdapter(
            onConnect = { server -> connectToServer(server) },
            onEdit = { server -> showEditDialog(server) },
            onDelete = { server -> deleteServer(server) }
        )

        binding.serverList.layoutManager = LinearLayoutManager(this)
        binding.serverList.adapter = adapter

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val server = adapter.currentList[position]

                if (direction == ItemTouchHelper.LEFT) {
                    deleteServer(server)
                } else {
                    connectToServer(server)
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.serverList)

        val anim = AnimationUtils.loadAnimation(this, R.anim.slide_in)
        binding.serverList.startAnimation(anim)
    }

    private fun setupSearch() {
        binding.searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                if (query.isEmpty()) {
                    observeServers()
                } else {
                    lifecycleScope.launch {
                        repo.search(query).collectLatest { servers ->
                            adapter.submitList(servers)
                            updateEmptyState(servers.isEmpty())
                        }
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupQuickConnect() {
        binding.quickConnectBtn.setOnClickListener {
            val host = binding.quickHostEdit.text?.toString()?.trim().orEmpty()
            val user = binding.quickUserEdit.text?.toString()?.trim().orEmpty()

            if (host.isEmpty() || user.isEmpty()) {
                Toast.makeText(this, "Enter host and username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val server = Server(
                name = host,
                host = host,
                port = 22,
                username = user,
                password = ""
            )
            connectToServer(server)
        }
    }

    private fun observeServers() {
        lifecycleScope.launch {
            repo.getAll().collectLatest { servers ->
                adapter.submitList(servers)
                updateEmptyState(servers.isEmpty())
                binding.quickConnectSection.visibility =
                    if (servers.size > 3) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateEmptyState(empty: Boolean) {
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.serverList.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun showAddDialog() {
        ServerDialogFragment { server ->
            lifecycleScope.launch {
                repo.insert(server)
                Toast.makeText(this@MainActivity, "Server saved", Toast.LENGTH_SHORT).show()
            }
        }.show(supportFragmentManager, ServerDialogFragment.TAG)
    }

    private fun showEditDialog(server: Server) {
        ServerDialogFragment(server) { updated ->
            lifecycleScope.launch {
                repo.update(updated)
                Toast.makeText(this@MainActivity, "Server updated", Toast.LENGTH_SHORT).show()
            }
        }.show(supportFragmentManager, ServerDialogFragment.TAG)
    }

    private fun deleteServer(server: Server) {
        lifecycleScope.launch {
            repo.delete(server)
            Toast.makeText(this@MainActivity, "Server deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToServer(server: Server) {
        lifecycleScope.launch {
            repo.touchLastConnected(server.id)
        }

        TerminalActivity.start(
            this,
            host = server.host,
            port = server.port,
            user = server.username,
            pass = server.password
        )
    }
}
