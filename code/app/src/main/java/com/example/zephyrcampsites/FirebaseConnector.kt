package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlin.jvm.java

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
     *
     * @param value Object to add, can be anything that inherits from DataHolder (base class that data classes inherits from).
     */
    fun add(value: DataHolder) {
        db.document(value.id).set(value)
    }

    /**
     * Updates document in target collection.
     *
     * @param target Object to modify, type DataHolder (base class that data classes inherits from).
     */
    fun update(target: DataHolder, field: String, value: String) {
        db.document(target.id).update(field, value)
    }

    /**
     * Removes a document from the target collection.
     *
     * @param target Object to add, can be anything that inherits from DataHolder (base class that data classes inherits from).
     */
    fun remove(target: DataHolder) {
        db.document(target.id).delete()
    }

    /**
     * Initializes the snapshot listener required to update a local copy of the list/collection.
     *
     * @param localList List stored locally, ex. _campsites
     * @param type Type of item stored in localList, passed in as class ex. Campsite::class.java
     */
    fun <T> initializeListener(localList: MutableList<T>, type: Class<T>) {
        db.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            localList.clear()

            snapshot?.documents?.forEach { document ->
                val item = document.toObject(type)
                if (item != null) {
                    localList.add(item)
                }
            }
        }
    }

    fun resourceGetter(path: String) : String {
        val storageRef = Firebase.storage.reference
        val resource =  storageRef.child(path)
        var imageUrl = ""
        resource.downloadUrl.addOnSuccessListener {uri ->
            imageUrl = uri.toString()
        }
        return imageUrl
    }
}