package com.example.zephyrcampsites

class CampsiteController(val model: CampsiteModel) {
    fun onClick() {
        model.createCampsite("add test", "right here", "the thing", 20)
        model.campsites.forEach { campsite ->
            println(campsite.id)
        }
    }
}