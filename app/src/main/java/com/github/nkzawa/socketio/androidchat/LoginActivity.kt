package com.github.nkzawa.socketio.androidchat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.android.synthetic.main.activity_login.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.sdk25.coroutines.onEditorAction
import org.json.JSONException
import org.json.JSONObject


/**
 * A login screen that offers login via username.
 */
class LoginActivity : Activity() {

    private var userName: String? = null

    private lateinit var socket: Socket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val app = application as ChatApplication
        socket = app.socket

        // Set up the login form.
        username_input.onEditorAction(returnValue = true) { v, actionId, event ->
            if (actionId == R.id.login || actionId == EditorInfo.IME_NULL) {
                attemptLogin()
            }
        }

        sign_in_button.onClick { attemptLogin() }

        socket.on("login", onLogin)
    }

    override fun onDestroy() {
        super.onDestroy()

        socket.off("login", onLogin)
    }

    /**
     * Attempts to sign in the account specified by the login form.
     * If there are form errors (invalid username, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private fun attemptLogin() {
        // Reset errors.
        username_input.error = null

        // Store values at the time of the login attempt.
        val username = username_input.text.toString().trim()

        // Check for a valid username.
        if (username.isNullOrBlank()) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            username_input.error = getString(R.string.error_field_required)
            username_input.requestFocus()
            return
        }

        userName = username

        // perform the user login attempt.
        socket.emit("add user", username)
    }

    private val onLogin = Emitter.Listener { args ->
        val data = args[0] as JSONObject

        val numUsers: Int
        try {
            numUsers = data.getInt("numUsers")
        } catch (e: JSONException) {
            return@Listener
        }

        val intent = Intent()
        intent.putExtra("username", userName)
        intent.putExtra("numUsers", numUsers)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}



