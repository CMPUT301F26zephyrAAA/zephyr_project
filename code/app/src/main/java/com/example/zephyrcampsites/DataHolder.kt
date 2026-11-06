package com.example.zephyrcampsites

/**
 * Open class for everything that would hold data, ex. Campsite.
 * Use for single object, not for models.
 * Created to generalize FirebaseConnector a connector does not need to be rewritten for every object type.
 * Name is a value only to make it so the class is not empty, as the compiler will not like it.
 */
open class DataHolder(open val id: String) {
    open val name : String = ""
}