package com.example.zephyrcampsites

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before

/** Connects admin model tests only to the local Firestore emulator on port 8082. */
abstract class AdminEmulatorTest {
    protected lateinit var firestore: FirebaseFirestore
    private lateinit var app: FirebaseApp
    protected val suffix = UUID.randomUUID().toString()
    private val createdDocuments = mutableListOf<DocumentReference>()

    @Before
    fun connectToEmulator() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("adminFirestoreEmulator") == "true"
        )
        val options =
            FirebaseOptions.Builder()
                .setProjectId("demo-zephyr-admin")
                .setApplicationId("1:123456789:android:admin-test")
                .setApiKey("local-emulator-only")
                .build()
        app =
            FirebaseApp.initializeApp(
                InstrumentationRegistry.getInstrumentation().targetContext,
                options,
                "admin-$suffix",
            )
        firestore = FirebaseFirestore.getInstance(app)
        firestore.useEmulator("127.0.0.1", 8082)
    }

    protected fun put(path: String, data: Map<String, Any>) {
        val reference = firestore.document(path)
        createdDocuments.add(reference)
        await(reference.set(data))
    }

    protected fun <T> await(task: Task<T>): T = Tasks.await(task, 20, TimeUnit.SECONDS)

    @After
    fun removeLocalData() {
        if (this::firestore.isInitialized) {
            await(firestore.enableNetwork())
            createdDocuments.reversed().forEach { await(it.delete()) }
            await(firestore.terminate())
        }
        if (this::app.isInitialized) {
            app.delete()
        }
    }
}
