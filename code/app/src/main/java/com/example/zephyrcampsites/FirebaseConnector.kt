package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import android.util.Log
import android.net.Uri

/**
 * Class for firebase connection.
 *
 * Starts a connection to target collection, and allows for modifying (add/update/remove) documents and initializing a snapshot listener.
 * Intention is for each model to make an instance of this for their target type, ex. in CampsiteModel.kt, val connector = FirebaseConnector("campsite")
 *
 * @param target Name of target Firebase collection, ex. campsite, accounts, etc.
 */
class FirebaseConnector(target: String) {
    val db = Firebase.firestore.collection(target)

    /**
     * Adds a document to the target collection.
     * Throws IllegalStateException if operation failed.
     *
     * @param value Object to add, can be anything that inherits from DataHolder (base class that data classes inherits from).
     */
    fun add(value: DataHolder, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        db.document(value.id).set(value)
            .addOnSuccessListener {
                Log.d("Firebase_test", "Saved campsite: ${value.id}")
                onSuccess()
            }
            .addOnFailureListener { exception ->
                Log.e("Firebase_test", "Save failed", exception)
                onFailure(exception.message ?: "Database operation failed.")
            }
    }

    /**
     * Updates document in target collection.
     * Throws IllegalStateException if operation failed.
     *
     * @param target Object to modify, type DataHolder (base class that data classes inherits from).
     */
    fun update(target: DataHolder, field: String, value: String) {
        db.document(target.id).update(field, value)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    throw IllegalStateException("Database operation failed.")
                }
            }
    }

    /**
     * Removes a document from the target collection.
     * Throws IllegalStateException if operation failed.
     *
     * @param target Object to add, can be anything that inherits from DataHolder (base class that data classes inherits from).
     */
    fun remove(target: DataHolder) {
        db.document(target.id).delete()
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    throw IllegalStateException("Database operation failed.")
                }
            }
    }

    /**
     * Initializes the snapshot listener required to update a local copy of the list/collection.
     *
     * @param localList List stored locally, ex. _campsites
     * @param type Type of item stored in localList, passed in as class ex. Campsite::class.java
     * @param onError Method to respond when listener fails
     * @param onSuccess Method to respond when listener succeeds
     */
    fun <T> initializeListener(
        localList: MutableList<T>,
        type: Class<T>,
        onError: (FirebaseFirestoreException?) -> Unit,
        onSuccess: () -> Unit
    ) : ListenerRegistration {
        return db.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            localList.clear()

            snapshot?.documents?.forEach { document ->
                val item = document.toObject(type)
                if (item != null) {
                    localList.add(item)
                }
            }
            onSuccess()
        }
    }

    /**
     * Resolves an HTTPS image URL or an existing Firebase Storage path.
     *
     * @param path HTTPS URL or Firebase Storage path of the image.
     * @param result Called when the image URL is available.
     * @param onError Called if the path is invalid or the URL cannot be retrieved.
     */
    fun resourceGetter(
        path: String,
        result: (String) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val cleanPath = path.trim()

        if (cleanPath.isBlank()) {
            onError(IllegalArgumentException("Image path is empty."))
            return
        }

        if (cleanPath.startsWith("https://")) {
            result(cleanPath)
            return
        }

        if (cleanPath.startsWith("http://")) {
            onError(IllegalArgumentException("Image URLs must use HTTPS."))
            return
        }

        val resource = try {
            if (cleanPath.startsWith("gs://")) {
                Firebase.storage.getReferenceFromUrl(cleanPath)
            } else {
                Firebase.storage.reference.child(cleanPath)
            }
        } catch (exception: IllegalArgumentException) {
            onError(exception)
            return
        }

        resource.downloadUrl
            .addOnSuccessListener { uri -> result(uri.toString()) }
            .addOnFailureListener { exception -> onError(exception) }
    }

    /**
     * The following function was generated by OpenAI, ChatGPT,
     * "Create a Firebase Storage function for uploading campsite images",
     * 2026-11-05.
     */
    fun uploadCampsiteImage(
        campsiteId: String,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val imageName = java.util.UUID.randomUUID().toString()

        val storageRef = Firebase.storage.reference
            .child("campsites/$campsiteId/$imageName")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                onSuccess(storageRef.path)
            }
            .addOnFailureListener { exception ->
                onFailure(exception.message ?: "Image upload failed.")
            }
    }
}
