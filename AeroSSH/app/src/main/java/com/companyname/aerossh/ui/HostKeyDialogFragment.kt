package com.companyname.aerossh.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.companyname.aerossh.R

class HostKeyDialogFragment(
    private val host: String,
    private val fingerprint: String,
    private val keyType: String,
    private val onTrust: () -> Unit,
    private val onReject: () -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_host_key, null)

        view.findViewById<TextView>(R.id.hostText).text = host
        view.findViewById<TextView>(R.id.keyTypeText).text = keyType
        view.findViewById<TextView>(R.id.fingerprintText).text = fingerprint

        view.findViewById<Button>(R.id.btnTrust).setOnClickListener {
            onTrust()
            dismiss()
        }

        view.findViewById<Button>(R.id.btnReject).setOnClickListener {
            onReject()
            dismiss()
        }

        val dialog = Dialog(requireContext())
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    companion object {
        const val TAG = "HostKeyDialog"
    }
}
