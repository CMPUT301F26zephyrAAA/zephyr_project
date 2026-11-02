package com.example.zephyrcampsites

/**
 * Sealed class of list of screens related to campsites.
 * Used for navigation in MainActivity (instead of enum class in order to pass arguments).
 *
 * @param route The resulting route, in most cases it'll just be the name itself.
 */
sealed class CampsiteScreens(val route: String) {
    data object SummaryView : CampsiteScreens("CampsiteScreen/SummaryView/")
    data object DetailView : CampsiteScreens("CampsiteScreen/campsite/{id}") {
        /**
         * Creates an individual route for provided campsite ID
         *
         * @param id ID of campsite to generate route for.
         */
        fun createRoute(id: Int) : String {
            return "CampsiteScreen/campsite/$id"
        }
    }
    data object FilterPage : CampsiteScreens("CampsiteScreen/FilterPage")
}