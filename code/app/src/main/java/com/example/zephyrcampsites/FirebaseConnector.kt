package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Interface to inherit from for connecting to firebase.
 * Should implement in each model? Or controller?
 */
class FirebaseConnector(target: DataHolder) {
    val db = Firebase.firestore.collection(target.name)
    fun add(value: Campsite) {
        db.document(value.name).set(value)
    }

    fun update(target: Campsite, field: String, value: String) {
        db.document(target.name).update(field, value)
    }

    fun remove(target: Campsite) {
        db.document(target.name).delete()
    }

    fun initializeListener(localList: MutableList<Campsite>) {
        db.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            localList.clear()

            snapshot?.documents?.forEach { document ->
                val campsite = document.toObject(Campsite::class.java)
                if (campsite != null) {
                    localList.add(campsite)
                }
            }
        }
    }
}