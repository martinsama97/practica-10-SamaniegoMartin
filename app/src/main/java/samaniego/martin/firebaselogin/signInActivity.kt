package samaniego.martin.firebaselogin

import android.content.ContentValues.TAG
import android.content.Intent
import android.content.IntentSender
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import samaniego.martin.firebaselogin.databinding.ActivitySignInBinding

class signInActivity : AppCompatActivity() {
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var oneTapClient: SignInClient
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivitySignInBinding

    val RC_SIGN_IN = 123
    val COD_LOGOUT = 321
    lateinit var mGoogleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = Firebase.auth
        
        binding.btnLogin.setOnClickListener {
            val mEmail = binding.etEmail.text.toString()
            val mPassword = binding.etPassword.text.toString()

            when{
                mEmail.isEmpty() || mPassword.isEmpty()->{
                    Toast.makeText(baseContext, "Mail o contraseña incorrecta", Toast.LENGTH_SHORT).show()
                } else ->{
                    SignIn(mEmail,mPassword)
                }
            }
        }

        val sign_in_button: SignInButton = findViewById(R.id.sign_in_button)

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        sign_in_button.setOnClickListener {
            val signInIntent: Intent = mGoogleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        oneTapClient = Identity.getSignInClient(this);
        signInRequest = BeginSignInRequest.builder()
            .setPasswordRequestOptions(
                BeginSignInRequest.PasswordRequestOptions.builder()
                .setSupported(true)
                .build())
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                .setSupported(true)
                // Your server's client ID, not your Android client ID.
                .setServerClientId(getString(R.string.default_web_client_id))
                // Only show accounts previously used to sign in.
                .setFilterByAuthorizedAccounts(true)
                .build())
            // Automatically sign in when exactly one credential is retrieved.
            .setAutoSelectEnabled(true)
            .build();

        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                try {
                    startIntentSenderForResult(
                        result.pendingIntent.intentSender, REQ_ONE_TAP,
                        null, 0, 0, 0, null)
                } catch (e: IntentSender.SendIntentException) {
                    Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                }
            }
            .addOnFailureListener(this) { e ->
                // No saved credentials found. Launch the One Tap sign-up flow, or
                // do nothing and continue presenting the signed-out UI.
                Log.d(TAG, e.localizedMessage)
            }
    }

        private val REQ_ONE_TAP = 2  // Can be any integer unique to the Activity
        private var showOneTapUI = true
        // ...

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)

            // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
            if (requestCode == RC_SIGN_IN) {
                // The Task returned from this call is always completed, no need to attach
                // a listener.
                val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
                handleSignInResult(task)
            }

            if ( requestCode == COD_LOGOUT){
                signOut()
            }

            val googleCredential = oneTapClient.getSignInCredentialFromIntent(data)
            val idToken = googleCredential.googleIdToken
            when {
                idToken != null -> {
                    // Got an ID token from Google. Use it to authenticate
                    // with Firebase.
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                // Sign in success, update UI with the signed-in user's information
                                Log.d(TAG, "signInWithCredential:success")
                                val user = auth.currentUser
                                //updateUI(user)
                            } else {
                                // If sign in fails, display a message to the user.
                                Log.w(TAG, "signInWithCredential:failure", task.exception)
                                //updateUI(null)
                            }
                        }
                }
                else -> {
                    // Shouldn't happen.
                    Log.d(TAG, "No ID token!")
                }
            }

            when (requestCode) {
                REQ_ONE_TAP -> {
                    try {
                        val credential = oneTapClient.getSignInCredentialFromIntent(data)
                        val idToken = credential.googleIdToken
                        val username = credential.id
                        val password = credential.password
                        when {
                            idToken != null -> {
                                // Got an ID token from Google. Use it to authenticate
                                // with your backend.
                                Log.d(TAG, "Got ID token.")
                            }
                            password != null -> {
                                // Got a saved username and password. Use them to authenticate
                                // with your backend.
                                Log.d(TAG, "Got password.")
                            }
                            else -> {
                                // Shouldn't happen.
                                Log.d(TAG, "No ID token or password!")
                            }
                        }
                    } catch (e: ApiException) {
                        when (e.statusCode) {
                            CommonStatusCodes.CANCELED -> {
                                Log.d(TAG, "One-tap dialog was closed.")
                                // Don't re-prompt the user.
                                showOneTapUI = false
                            }
                            CommonStatusCodes.NETWORK_ERROR -> {
                                Log.d(TAG, "One-tap encountered a network error.")
                                // Try again or just ignore.
                            }
                            else -> {
                                Log.d(
                                    TAG, "Couldn't get credential from result." +
                                            " (${e.localizedMessage})"
                                )
                            }
                        }
                    }
                }
            }
        }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)

            // Signed in successfully, show authenticated UI.
            //updateUI(account)
        } catch (e: ApiException) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            //Log.w(TAG, "signInResult:failed code=" + e.getStatusCode())
            //updateUI(null)
        }
    }

    private fun signOut() {
        mGoogleSignInClient.signOut()
            .addOnCompleteListener(this,) {
                Toast.makeText(this, "Sesion terminada", Toast.LENGTH_SHORT).show()
            }
    }


    private fun SignIn(email:String, password:String){
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "createUserWithEmail:success")
                    reload()
                    //val user = auth.currentUser
                    //updateUI(user)
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT).show()
                    //updateUI(null)
                }
            }
    }

    private fun reload(){
        val intent = Intent(this, MainActivity::class.java)
        this.startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        //updateUI(currentUser)
    }
}