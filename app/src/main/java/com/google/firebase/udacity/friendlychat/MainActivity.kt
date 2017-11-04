/**
 * Copyright Google Inc. All Rights Reserved.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.AuthUI.IdpConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.ArrayList
import java.util.Arrays

class MainActivity : AppCompatActivity() {

  private lateinit var mMessageListView: ListView
  private lateinit var mMessageAdapter: MessageAdapter
  private lateinit var mProgressBar: ProgressBar
  private lateinit var mPhotoPickerButton: ImageButton
  private lateinit var mMessageEditText: EditText
  private lateinit var mSendButton: Button

  private var mUsername: String = ""
  private lateinit var firebaseDatabase: FirebaseDatabase
  private lateinit var messageDatabaseReference: DatabaseReference
  private var childEventListener: ChildEventListener? = null
  private lateinit var firebaseAuth: FirebaseAuth
  private var authStateListener: FirebaseAuth.AuthStateListener? = null


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    mUsername = ANONYMOUS
    firebaseDatabase = FirebaseDatabase.getInstance()
    firebaseAuth = FirebaseAuth.getInstance()

    messageDatabaseReference = firebaseDatabase.reference.child("messages")

    // Initialize references to views
    mProgressBar = findViewById<View>(R.id.progressBar) as ProgressBar
    mMessageListView = findViewById<View>(R.id.messageListView) as ListView
    mPhotoPickerButton = findViewById<View>(R.id.photoPickerButton) as ImageButton
    mMessageEditText = findViewById<View>(R.id.messageEditText) as EditText
    mSendButton = findViewById<View>(R.id.sendButton) as Button

    // Initialize message ListView and its adapter
    val friendlyMessages = ArrayList<FriendlyMessage>()
    mMessageAdapter = MessageAdapter(this, R.layout.item_message, friendlyMessages)
    mMessageListView.adapter = mMessageAdapter

    // Initialize progress bar
    mProgressBar.visibility = ProgressBar.INVISIBLE

    // ImagePickerButton shows an image picker to upload a image for a message
    mPhotoPickerButton.setOnClickListener {
      // TODO: Fire an intent to show an image picker
    }

    // Enable Send button when there's text to send
    mMessageEditText.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

      override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
        mSendButton.isEnabled = charSequence.isNotEmpty()
      }

      override fun afterTextChanged(editable: Editable) {}
    })
    mMessageEditText.filters = arrayOf<InputFilter>(
        InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT))

    // Send button sends a message and clears the EditText
    mSendButton.setOnClickListener {
      // TODO: Send messages on click
      val friendlyMessage = FriendlyMessage(mMessageEditText.text.toString(), mUsername, null)
      messageDatabaseReference.push().setValue(friendlyMessage)

      // Clear input box
      mMessageEditText.setText("")
    }

    authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
      val firebaseUser = firebaseAuth.currentUser
      if (firebaseUser != null) {
        Toast.makeText(this@MainActivity, "Welcome to FriendlyChat", Toast.LENGTH_SHORT).show()
        //User signed in
        onSignedInInitialize(firebaseUser.displayName)
      } else {
        onSignedOutCleanUp()
        //User Signed out
        startActivityForResult(AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setIsSmartLockEnabled(false)
            .setAvailableProviders(
                Arrays.asList<IdpConfig>(AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                    AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()))
            .build(), RC_SIGN_IN)
      }
    }
  }

  private fun onSignedInInitialize(username: String?) {
    if (username != null)
      mUsername = username
    //You must be authenticated to be able to read.
    // Was set at server "auth!=null"
    attachDatabaseReadListener()
  }

  private fun onSignedOutCleanUp() {
    mUsername = ANONYMOUS
    mMessageAdapter.clear()
    detachDatabaseReadListener()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    val inflater = menuInflater
    inflater.inflate(R.menu.main_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.sign_out_menu -> {
        AuthUI.getInstance().signOut(this)
        true
      }

      else -> super.onOptionsItemSelected(item)
    }
  }

  override fun onResume() {
    super.onResume()
    val authStateListener = authStateListener
    // if you resume the code and the user is logged in, a database listener is attached, so it must be detached onPause
    // if the user is logged in then we will trigger sign initialize , add a read listener, must detach onPause and clear adapter
    if (authStateListener != null)
      firebaseAuth.addAuthStateListener(authStateListener)
  }

  override fun onPause() {
    super.onPause()
    val authStateListener = authStateListener
    if (authStateListener != null) firebaseAuth.removeAuthStateListener(authStateListener)
    detachDatabaseReadListener()
    mMessageAdapter.clear()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == RC_SIGN_IN) {
      if (resultCode == Activity.RESULT_OK) {
        Toast.makeText(this@MainActivity, "Signed In", Toast.LENGTH_LONG).show()
      } else if (resultCode == Activity.RESULT_CANCELED) {
        Toast.makeText(this@MainActivity, "Sign In Canceled ", Toast.LENGTH_LONG).show()
        finish()
      }
    }
  }

  private fun attachDatabaseReadListener() {
    if (childEventListener == null) {
      childEventListener = object : ChildEventListener {
        // New message inserted into messages list.
        // Existing children when the listener first attached, and active for later children.
        override fun onChildAdded(dataSnapshot: DataSnapshot, s: String?) {
          // Deserialize the message into the pojo message object.
          val friendlyMessage = dataSnapshot.getValue(FriendlyMessage::class.java)
          mMessageAdapter.add(friendlyMessage)
        }

        override fun onChildChanged(dataSnapshot: DataSnapshot, s: String?) {

        }

        override fun onChildRemoved(dataSnapshot: DataSnapshot) {

        }

        override fun onChildMoved(dataSnapshot: DataSnapshot, s: String?) {

        }

        // Error occurred when trying to make changes
        override fun onCancelled(databaseError: DatabaseError) {

        }
      }
      messageDatabaseReference.addChildEventListener(childEventListener)
    }
  }

  private fun detachDatabaseReadListener() {

    if (childEventListener != null) {
      messageDatabaseReference.removeEventListener(childEventListener)
      childEventListener = null
    }
  }

  companion object {

    private val TAG = "MainActivity"

    val ANONYMOUS = "anonymous"
    val DEFAULT_MSG_LENGTH_LIMIT = 1000
    private val RC_SIGN_IN = 1
  }
}
