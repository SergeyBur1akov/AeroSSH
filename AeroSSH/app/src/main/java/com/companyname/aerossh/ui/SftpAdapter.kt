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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SftpAdapter(
    private val onOpen: (SftpService.SftpEntry) -> Unit,
    private val onSelect: (SftpService.SftpEntry) -> Unit
) : ListAdapter<SftpService.SftpEntry, SftpAdapter.ViewHolder>(DiffCallback) {

    private var selectedPath: String? = null

    fun setSelected(path: String?) {
        val old = selectedPath
        selectedPath = path
        if (old != null) notifyItemChanged(currentList.indexOfFirst { it.path == old })
        if (path != null) notifyItemChanged(currentList.indexOfFirst { it.path == path })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sftp_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: TextView = view.findViewById(R.id.fileIcon)
        private val name: TextView = view.findViewById(R.id.fileName)
        private val info: TextView = view.findViewById(R.id.fileInfo)

        fun bind(entry: SftpService.SftpEntry) {
            icon.text = if (entry.isDirectory) "\uD83D\uDCC1" else getFileIcon(entry.name)
            name.text = entry.name

            if (entry.isDirectory) {
                info.text = "Folder"
            } else {
                info.text = formatSize(entry.size)
            }

            itemView.alpha = if (selectedPath == entry.path) 0.7f else 1.0f

            itemView.setOnClickListener {
                if (entry.isDirectory) {
                    onOpen(entry)
                } else {
                    onSelect(entry)
                }
            }

            itemView.setOnLongClickListener {
                onSelect(entry)
                true
            }
        }
    }

    private fun getFileIcon(name: String): String {
        return when {
            name.endsWith(".txt", true) || name.endsWith(".log", true) -> "\uD83D\uDCC4"
            name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".gif", true) -> "\uD83D\uDDBC\uFE0F"
            name.endsWith(".zip", true) || name.endsWith(".tar", true) || name.endsWith(".gz", true) -> "\uD83D\uDCE6"
            name.endsWith(".sh", true) || name.endsWith(".py", true) || name.endsWith(".kt", true) -> "\uD83D\uDCBB"
            name.endsWith(".pdf", true) -> "\uD83D\uDCC3"
            else -> "\uD83D\uDCC1"
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<SftpService.SftpEntry>() {
        override fun areItemsTheSame(old: SftpService.SftpEntry, new: SftpService.SftpEntry) = old.path == new.path
        override fun areContentsTheSame(old: SftpService.SftpEntry, new: SftpService.SftpEntry) = old == new
    }
}
