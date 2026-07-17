package com.companyname.aerossh.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.companyname.aerossh.R
import com.companyname.aerossh.data.Server
import com.google.android.material.textfield.TextInputEditText

class ServerDialogFragment(
    private val server: Server? = null,
    private val onSave: (Server) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_server, null)

        val titleText = view.findViewById<TextView>(R.id.dialogTitle)
        val editName = view.findViewById<TextInputEditText>(R.id.editName)
        val editHost = view.findViewById<TextInputEditText>(R.id.editHost)
        val editPort = view.findViewById<TextInputEditText>(R.id.editPort)
        val editUsername = view.findViewById<TextInputEditText>(R.id.editUsername)
        val editPassword = view.findViewById<TextInputEditText>(R.id.editPassword)
        val editGroup = view.findViewById<TextInputEditText>(R.id.editGroup)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        server?.let { s ->
            titleText.text = "Edit Server"
            editName.setText(s.name)
            editHost.setText(s.host)
            editPort.setText(s.port.toString())
            editUsername.setText(s.username)
            editPassword.setText(s.password)
            editGroup.setText(s.groupTag)
        }

        btnCancel.setOnClickListener { dismiss() }
        btnSave.setOnClickListener {
            val name = editName.text?.toString()?.trim().orEmpty()
            val host = editHost.text?.toString()?.trim().orEmpty()
            val port = editPort.text?.toString()?.toIntOrNull() ?: 22
            val username = editUsername.text?.toString()?.trim().orEmpty()
            val password = editPassword.text?.toString().orEmpty()
            val group = editGroup.text?.toString()?.trim().orEmpty()

            if (name.isEmpty() || host.isEmpty() || username.isEmpty()) {
                if (name.isEmpty()) editName.error = "Required"
                if (host.isEmpty()) editHost.error = "Required"
                if (username.isEmpty()) editUsername.error = "Required"
                return@setOnClickListener
            }

            val saved = (server ?: Server(name = "", host = "", username = "")).copy(
                name = name,
                host = host,
                port = port,
                username = username,
                password = password,
                groupTag = group
            )
            onSave(saved)
            dismiss()
        }

        val dialog = Dialog(requireContext())
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    companion object {
        const val TAG = "ServerDialog"
    }
}
