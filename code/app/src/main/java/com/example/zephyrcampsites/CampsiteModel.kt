package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

open class CampsiteConnector() : FirebaseConnector<Campsite> {
    val db = Firebase.firestore.collection("campsite")
    override fun add(value: Campsite) {
        db.document(value.name).set(value)
    }

    override fun update(target: Campsite, field: String, value: String) {
        db.document(target.name).update(field, value)
    }

    override fun remove(target: Campsite) {
        db.document(target.name).delete()
    }

    override fun initializeListener(localList: MutableList<Campsite>) {
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
class CampsiteModel : ObservableModel<CampsiteModel>() {
    val connector = CampsiteConnector()
    private val _campsites = mutableStateListOf(
        Campsite(1),
        Campsite(2)
    )

    fun createCampsite(
        name: String,
        location: String,
        description: String,
        maxOccupancy: Int,
    ) {
        val newCampsite = Campsite(_campsites.size + 1)
        newCampsite.name = name
        newCampsite.location = location
        newCampsite.description = description
        newCampsite.maxOccupancy = maxOccupancy
        connector.add(newCampsite)
    }

    fun updateCampsite(
        target: Campsite,
        field: String,
        value: String
    ) {
        connector.update(target, field, value)
    }

    fun deleteCampsite(
        target: Campsite
    ) {
        connector.remove(target)
    }

    init {
        connector.initializeListener(_campsites)
    }
}