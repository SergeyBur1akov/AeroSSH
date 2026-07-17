package com.companyname.aerossh.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.companyname.aerossh.R
import com.companyname.aerossh.sftp.SftpService

class SftpAdapter(private val onOpen: (SftpService.SftpEntry) -> Unit, private val onSelect: (SftpService.SftpEntry) -> Unit) : ListAdapter<SftpService.SftpEntry, SftpAdapter.ViewHolder>(DiffCallback) {
    private var selectedPath: String? = null
    fun setSelected(path: String?) { val old = selectedPath; selectedPath = path; if (old != null) notifyItemChanged(currentList.indexOfFirst { it.path == old }); if (path != null) notifyItemChanged(currentList.indexOfFirst { it.path == path }) }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_sftp_file, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon = view.findViewById<TextView>(R.id.fileIcon); private val name = view.findViewById<TextView>(R.id.fileName); private val info = view.findViewById<TextView>(R.id.fileInfo)
        fun bind(entry: SftpService.SftpEntry) {
            icon.text = if (entry.isDirectory) "\uD83D\uDCC1" else getFileIcon(entry.name); name.text = entry.name
            info.text = if (entry.isDirectory) "Folder" else formatSize(entry.size)
            itemView.alpha = if (selectedPath == entry.path) 0.7f else 1.0f
            itemView.setOnClickListener { if (entry.isDirectory) onOpen(entry) else onSelect(entry) }; itemView.setOnLongClickListener { onSelect(entry); true }
        }
    }
    private fun getFileIcon(name: String): String = when { name.endsWith(".txt", true) -> "\uD83D\uDCC4"; name.endsWith(".jpg", true) || name.endsWith(".png", true) -> "\uD83D\uDDBC\uFE0F"; name.endsWith(".zip", true) || name.endsWith(".tar", true) -> "\uD83D\uDCE6"; name.endsWith(".sh", true) || name.endsWith(".py", true) -> "\uD83D\uDCBB"; name.endsWith(".pdf", true) -> "\uD83D\uDCC3"; else -> "\uD83D\uDCC1" }
    private fun formatSize(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1048576 -> "${bytes / 1024} KB"; bytes < 1073741824 -> "${bytes / 1048576} MB"; else -> "${bytes / 1073741824} GB" }
    object DiffCallback : DiffUtil.ItemCallback<SftpService.SftpEntry>() { override fun areItemsTheSame(a: SftpService.SftpEntry, b: SftpService.SftpEntry) = a.path == b.path; override fun areContentsTheSame(a: SftpService.SftpEntry, b: SftpService.SftpEntry) = a == b }
}
