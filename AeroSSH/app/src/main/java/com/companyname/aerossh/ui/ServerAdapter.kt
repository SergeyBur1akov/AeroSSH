package com.companyname.aerossh.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.companyname.aerossh.R
import com.companyname.aerossh.data.Server

class ServerAdapter(
    private val onConnect: (Server) -> Unit,
    private val onEdit: (Server) -> Unit,
    private val onDelete: (Server) -> Unit
) : ListAdapter<Server, ServerAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameText: TextView = view.findViewById(R.id.serverName)
        private val hostText: TextView = view.findViewById(R.id.serverHost)
        private val tagText: TextView = view.findViewById(R.id.serverTag)
        private val indicator: View = view.findViewById(R.id.connectionIndicator)

        fun bind(server: Server) {
            nameText.text = server.name
            hostText.text = "${server.username}@${server.host}:${server.port}"

            if (server.groupTag.isNotEmpty()) {
                tagText.text = server.groupTag
                tagText.visibility = View.VISIBLE
            } else {
                tagText.visibility = View.GONE
            }

            // Show green dot if recently connected (within 24h)
            val recentlyConnected = System.currentTimeMillis() - server.lastConnected < 86_400_000
            indicator.setBackgroundColor(
                if (recentlyConnected) Color.parseColor("#3FB950")
                else Color.parseColor("#30363D")
            )

            itemView.setOnClickListener { onConnect(server) }
            itemView.setOnLongClickListener {
                onEdit(server)
                true
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Server>() {
        override fun areItemsTheSame(old: Server, new: Server) = old.id == new.id
        override fun areContentsTheSame(old: Server, new: Server) = old == new
    }
}
