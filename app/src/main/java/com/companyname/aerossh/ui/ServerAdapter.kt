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

class ServerAdapter(private val onConnect: (Server) -> Unit, private val onEdit: (Server) -> Unit, private val onDelete: (Server) -> Unit) : ListAdapter<Server, ServerAdapter.ViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_server, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameText = view.findViewById<TextView>(R.id.serverName)
        private val hostText = view.findViewById<TextView>(R.id.serverHost)
        private val tagText = view.findViewById<TextView>(R.id.serverTag)
        private val indicator = view.findViewById<View>(R.id.connectionIndicator)

        fun bind(server: Server) {
            nameText.text = server.name; hostText.text = "${server.username}@${server.host}:${server.port}"
            if (server.groupTag.isNotEmpty()) { tagText.text = server.groupTag; tagText.visibility = View.VISIBLE } else tagText.visibility = View.GONE
            indicator.setBackgroundColor(if (System.currentTimeMillis() - server.lastConnected < 86_400_000) Color.parseColor("#3FB950") else Color.parseColor("#30363D"))
            itemView.setOnClickListener { onConnect(server) }; itemView.setOnLongClickListener { onEdit(server); true }
        }
    }
    object DiffCallback : DiffUtil.ItemCallback<Server>() { override fun areItemsTheSame(a: Server, b: Server) = a.id == b.id; override fun areContentsTheSame(a: Server, b: Server) = a == b }
}
