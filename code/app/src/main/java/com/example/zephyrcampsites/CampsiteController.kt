package com.example.zephyrcampsites

/**
 * Controller class for anything campsite related.
 * Will handle any user interactions made on a view.
 * (Methods should be called from view)
 *
 * @param model Model to operate on.
 */
class CampsiteController(val model: CampsiteModel) {
    fun onClick() {
        model.createCampsite("add test", "right here", "the thing", 20)
        model.campsites.forEach { campsite ->
            println(campsite.id)
        }
    }

    /**
     * Retrieve campsite entry based on ID.
     *
     * @param id ID of campsite to retrieve
     * @return Full campsite object
     */
    fun retrieveId(id: Int) : Campsite? {
        return model.campsites.find { it.id.toInt() == id }
    }
}