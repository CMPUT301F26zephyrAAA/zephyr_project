package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import android.util.Log

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
     * (Incomplete, probably unusable) Gets resource from Firebase storage.
     *
     * @param path File path of resource to get.
     * @param result Method to respond when operation is a success, used to save the resource.
     */
    fun resourceGetter(
        path: String,
        result: (String) -> Unit
    ) {
        val storageRef = Firebase.storage.reference
        val resource =  storageRef.child(path)
        resource.downloadUrl.addOnSuccessListener {uri ->
            result(uri.toString())
        }.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                throw IllegalStateException("Database operation failed.")
            }
        }
    }
}