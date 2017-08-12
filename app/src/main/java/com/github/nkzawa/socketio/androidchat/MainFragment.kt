package com.github.nkzawa.socketio.androidchat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast

import org.json.JSONException
import org.json.JSONObject

import java.util.ArrayList

import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.android.synthetic.main.item_action.*
import org.jetbrains.anko.custom.onUiThread
import org.jetbrains.anko.runOnUiThread
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.sdk25.coroutines.onEditorAction
import org.jetbrains.anko.sdk25.coroutines.textChangedListener


/**
 * A chat fragment containing messages view and input form.
 */
class MainFragment : Fragment() {

    companion object {

        private val TAG = "MainFragment"

        private val REQUEST_LOGIN = 0

        private val TYPING_TIMER_LENGTH = 600
    }

    private val messageList = ArrayList<Message>()

    private var typing = false
    private val typingHandler = Handler()
    private var userName: String? = null

    private lateinit var socket: Socket

    private lateinit var adapter: RecyclerView.Adapter<*>

    private var isConnected: Boolean = true

    // This event fires 1st, before creation of fragment or any views
    // The onAttach method is called when the Fragment instance is associated with an Activity.
    // This does not mean the Activity is fully initialized.
    override fun onAttach(context: Context?) {
        super.onAttach(context)
        adapter = MessageAdapter(context!!, messageList)
        if (context is Activity) {
            //this.listener = (MainActivity) context;
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        val app = activity.application as ChatApplication
        socket = app.socket
        socket.on(Socket.EVENT_CONNECT, onConnect)
        socket.on(Socket.EVENT_DISCONNECT, onDisconnect)
        socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError)
        socket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError)
        socket.on("new message", onNewMessage)
        socket.on("user joined", onUserJoined)
        socket.on("user left", onUserLeft)
        socket.on("typing", onTyping)
        socket.on("stop typing", onStopTyping)
        socket.connect()

        startSignIn()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_main, container, false)
    }

    override fun onDestroy() {
        super.onDestroy()

        socket.disconnect()

        socket.off(Socket.EVENT_CONNECT, onConnect)
        socket.off(Socket.EVENT_DISCONNECT, onDisconnect)
        socket.off(Socket.EVENT_CONNECT_ERROR, onConnectError)
        socket.off(Socket.EVENT_CONNECT_TIMEOUT, onConnectError)
        socket.off("new message", onNewMessage)
        socket.off("user joined", onUserJoined)
        socket.off("user left", onUserLeft)
        socket.off("typing", onTyping)
        socket.off("stop typing", onStopTyping)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        messages?.run {
            layoutManager = LinearLayoutManager(activity)
            adapter = this@MainFragment.adapter
        }

        message_input.onEditorAction { v, actionId, event ->
            if (id == R.id.send || id == EditorInfo.IME_NULL) {
                attemptSend()
            }
        }

        message_input.textChangedListener {
            onTextChanged { text, _, _, _ ->
                if (userName == null) return@onTextChanged
                if (!socket.connected()) return@onTextChanged

                if (!typing) {
                    typing = true
                    socket.emit("typing")
                }

                typingHandler.removeCallbacks(onTypingTimeout)
                typingHandler.postDelayed(onTypingTimeout, TYPING_TIMER_LENGTH.toLong())
            }
        }

        send_button.onClick { attemptSend() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Activity.RESULT_OK != resultCode) {
            activity.finish()
            return
        }

        userName = data!!.getStringExtra("username")
        val numUsers = data.getIntExtra("numUsers", 1)

        addLog(resources.getString(R.string.message_welcome))
        addParticipantsLog(numUsers)
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater!!.inflate(R.menu.menu_main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item!!.itemId


        if (id == R.id.action_leave) {
            leave()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private fun addLog(message: String) {
        messageList.add(Message(Message.TYPE_LOG, message))
        adapter.notifyItemInserted(messageList.size - 1)
        scrollToBottom()
    }

    private fun addParticipantsLog(numUsers: Int) {
        addLog(resources.getQuantityString(R.plurals.message_participants, numUsers, numUsers))
    }

    private fun addMessage(username: String, message: String) {
        messageList.add(Message(Message.TYPE_MESSAGE, message, username))
        adapter.notifyItemInserted(messageList.size - 1)
        scrollToBottom()
    }

    private fun addTyping(username: String) {
        messageList.add(Message(Message.TYPE_ACTION, username = username))
        adapter.notifyItemInserted(messageList.size - 1)
        scrollToBottom()
    }

    private fun removeTyping(username: String) {
        for (i in messageList.indices.reversed()) {
            val message = messageList[i]
            if (message.type == Message.TYPE_ACTION && message.username == username) {
                messageList.removeAt(i)
                adapter.notifyItemRemoved(i)
            }
        }
    }

    private fun attemptSend() {
        if (userName == null) return
        if (!socket.connected()) return

        typing = false

        val message = message_input.text.toString().trim()
        if (message.isNullOrBlank()) {
            message_input.requestFocus()
            return
        }

        message_input.setText("")
        addMessage(userName!!, message)

        // perform the sending message attempt.
        socket.emit("new message", message)
    }

    private fun startSignIn() {
        userName = null
        val intent = Intent(activity, LoginActivity::class.java)
        startActivityForResult(intent, REQUEST_LOGIN)
    }

    private fun leave() {
        userName = null
        socket.disconnect()
        socket.connect()
        startSignIn()
    }

    private fun scrollToBottom() {
        messages.scrollToPosition(adapter.itemCount - 1)
    }

    private val onConnect = Emitter.Listener {
        activity.runOnUiThread {
            if ((!isConnected)) {
                if (null != userName)
                    socket.emit("add user", userName)
                Toast.makeText(activity.applicationContext,
                        R.string.connect, Toast.LENGTH_LONG).show()
                isConnected = true
            }
        }
    }

    private val onDisconnect = Emitter.Listener {
        activity.runOnUiThread {
            Log.i(TAG, "diconnected")
            isConnected = false
            Toast.makeText(activity.applicationContext,
                    R.string.disconnect, Toast.LENGTH_LONG).show()
        }
    }

    private val onConnectError = Emitter.Listener {
        activity.runOnUiThread {
            Log.e(TAG, "Error connecting")
            Toast.makeText(activity.applicationContext,
                    R.string.error_connect, Toast.LENGTH_LONG).show()
        }
    }

    private val onNewMessage = Emitter.Listener { args ->
        activity.runOnUiThread(Runnable {
            val data = args[0] as JSONObject
            val username: String
            val message: String
            try {
                username = data.getString("username")
                message = data.getString("message")
            } catch (e: JSONException) {
                Log.e(TAG, e.message)
                return@Runnable
            }

            removeTyping(username)
            addMessage(username, message)
        })
    }

    private val onUserJoined = Emitter.Listener { args ->
        activity.runOnUiThread(Runnable {
            val data = args[0] as JSONObject
            val username: String
            val numUsers: Int
            try {
                username = data.getString("username")
                numUsers = data.getInt("numUsers")
            } catch (e: JSONException) {
                Log.e(TAG, e.message)
                return@Runnable
            }

            addLog(resources.getString(R.string.message_user_joined, username))
            addParticipantsLog(numUsers)
        })
    }

    private val onUserLeft = Emitter.Listener { args ->
        activity.runOnUiThread(Runnable {
            val data = args[0] as JSONObject
            val username: String
            val numUsers: Int
            try {
                username = data.getString("username")
                numUsers = data.getInt("numUsers")
            } catch (e: JSONException) {
                Log.e(TAG, e.message)
                return@Runnable
            }

            addLog(resources.getString(R.string.message_user_left, username))
            addParticipantsLog(numUsers)
            removeTyping(username)
        })
    }

    private val onTyping = Emitter.Listener { args ->
        activity.runOnUiThread(Runnable {
            val data = args[0] as JSONObject
            val username: String
            try {
                username = data.getString("username")
            } catch (e: JSONException) {
                Log.e(TAG, e.message)
                return@Runnable
            }

            addTyping(username)
        })
    }

    private val onStopTyping = Emitter.Listener { args ->
        activity.runOnUiThread(Runnable {
            val data = args[0] as JSONObject
            val username: String
            try {
                username = data.getString("username")
            } catch (e: JSONException) {
                Log.e(TAG, e.message)
                return@Runnable
            }

            removeTyping(username)
        })
    }

    private val onTypingTimeout = Runnable {
        if (!typing) return@Runnable

        typing = false
        socket.emit("stop typing")
    }

}

