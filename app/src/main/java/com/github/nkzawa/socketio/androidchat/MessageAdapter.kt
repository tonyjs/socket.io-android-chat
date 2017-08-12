package com.github.nkzawa.socketio.androidchat

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class MessageAdapter(context: Context, private val messageList: List<Message>)
    : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    private val usernameColors: IntArray = context.resources.getIntArray(R.array.username_colors)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            Message.TYPE_MESSAGE -> R.layout.item_message
            Message.TYPE_LOG -> R.layout.item_log
            Message.TYPE_ACTION -> R.layout.item_action
            else -> -1
        }
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val message = messageList[position]
        viewHolder.setMessage(message.message ?: "")
        viewHolder.setUsername(message.username ?: "")
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    override fun getItemViewType(position: Int): Int {
        return messageList[position].type
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val usernameView: TextView?
        private val messageView: TextView?

        init {
            usernameView = itemView.findViewById(R.id.username) as TextView
            messageView = itemView.findViewById(R.id.message) as TextView
        }

        fun setUsername(username: String) {
            usernameView?.run {
                text = username
                setTextColor(getUsernameColor(username))
            }
        }

        fun setMessage(message: String) {
            messageView?.text = message
        }

        private fun getUsernameColor(username: String): Int {
            var hash = 7
            for (i in 0..username.length - 1) {
                hash = username.codePointAt(i) + (hash shl 5) - hash
            }
            val index = Math.abs(hash % usernameColors.size)
            return usernameColors[index]
        }
    }
}
