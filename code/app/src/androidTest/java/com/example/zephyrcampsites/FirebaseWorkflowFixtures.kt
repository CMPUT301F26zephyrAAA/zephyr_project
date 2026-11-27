// Shares two Firebase Auth sessions across local callable integration tests.
// AI Citation [AI-39] (Bhav Saluja). See the team AI Documentation Google Doc.
package com.example.zephyrcampsites

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

/** Keeps the local Firebase SDK instances alive for the instrumentation run. */
internal object FirebaseWorkflowFixtures {
    val primary by lazy { createSession("workflow-primary") }
    val secondary by lazy { createSession("workflow-secondary") }
    val firestore by lazy {
        val fixtureApp = createApp("workflow-firestore")
        FirebaseFirestore.getInstance(fixtureApp).apply { useEmulator("127.0.0.1", 8082) }
    }

    private fun createApp(name: String): FirebaseApp {
        val options = FirebaseOptions.Builder()
            .setProjectId("demo-zephyr")
            .setApplicationId("1:123456789:android:0123456789abcdef")
            .setApiKey("AIza" + "x".repeat(35))
            .build()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return FirebaseApp.initializeApp(context, options, name)
    }

    private fun createSession(name: String): FirebaseApp =
        createApp(name).apply {
            FirebaseAuth.getInstance(this).useEmulator("127.0.0.1", 9099)
            FirebaseFunctions.getInstance(this).useEmulator("127.0.0.1", 5001)
        }
}
