package com.companyname.aerossh.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.companyname.aerossh.R
import com.companyname.aerossh.data.Server
import com.google.android.material.textfield.TextInputEditText

class ServerDialogFragment(private val server: Server? = null, private val onSave: (Server) -> Unit) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_server, null)
        val titleText = view.findViewById<TextView>(R.id.dialogTitle); val editName = view.findViewById<TextInputEditText>(R.id.editName); val editHost = view.findViewById<TextInputEditText>(R.id.editHost); val editPort = view.findViewById<TextInputEditText>(R.id.editPort); val editUsername = view.findViewById<TextInputEditText>(R.id.editUsername); val editPassword = view.findViewById<TextInputEditText>(R.id.editPassword); val editGroup = view.findViewById<TextInputEditText>(R.id.editGroup)
        server?.let { titleText.text = "Edit Server"; editName.setText(it.name); editHost.setText(it.host); editPort.setText(it.port.toString()); editUsername.setText(it.username); editPassword.setText(it.password); editGroup.setText(it.groupTag) }
        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dismiss() }
        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val n = editName.text?.toString()?.trim().orEmpty(); val h = editHost.text?.toString()?.trim().orEmpty(); val p = editPort.text?.toString()?.toIntOrNull() ?: 22; val u = editUsername.text?.toString()?.trim().orEmpty(); val pw = editPassword.text?.toString().orEmpty(); val g = editGroup.text?.toString()?.trim().orEmpty()
            if (n.isEmpty() || h.isEmpty() || u.isEmpty()) { if (n.isEmpty()) editName.error = "Required"; if (h.isEmpty()) editHost.error = "Required"; if (u.isEmpty()) editUsername.error = "Required"; return@setOnClickListener }
            onSave((server ?: Server(name = "", host = "", username = "")).copy(name = n, host = h, port = p, username = u, password = pw, groupTag = g)); dismiss()
        }
        val dialog = Dialog(requireContext()); dialog.setContentView(view); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); return dialog
    }
    companion object { const val TAG = "ServerDialog" }
}
