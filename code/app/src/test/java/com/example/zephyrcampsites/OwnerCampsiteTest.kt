package com.example.zephyrcampsites

import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Tests owner-specific campsite filtering for US 02.03.01.
 */
class OwnerCampsiteTest {

    private fun createCampsite(
        id: String,
        ownerId: String
    ): Campsite {
        return Campsite(id).apply {
            ownerID = ownerId
            name = "Campsite $id"
        }
    }

    @Test
    fun ownerHasOneCampsite() {
        val campsite = createCampsite("1", "owner1")

        val result = filterOwnerCampsites(
            listOf(campsite),
            "owner1"
        )

        assertEquals(1, result.size)
        assertEquals(campsite, result.first())
    }

    @Test
    fun ownerHasMultipleCampsites() {
        val campsites = listOf(
            createCampsite("1", "owner1"),
            createCampsite("2", "owner1"),
            createCampsite("3", "owner1")
        )

        val result = filterOwnerCampsites(
            campsites,
            "owner1"
        )

        assertEquals(3, result.size)
    }

    @Test
    fun excludesOtherOwnersCampsites() {
        val campsites = listOf(
            createCampsite("1", "owner1"),
            createCampsite("2", "owner2"),
            createCampsite("3", "owner1")
        )

        val result = filterOwnerCampsites(
            campsites,
            "owner1"
        )

        assertEquals(2, result.size)
        assertTrue(result.all {
            it.ownerID == "owner1"
        })
        assertEquals(
            listOf("1", "3"),
            result.map { it.id }
        )
    }

    @Test
    fun ownerHasNoCampsites() {
        val campsites = listOf(
            createCampsite("1", "owner2"),
            createCampsite("2", "owner3")
        )

        val result = filterOwnerCampsites(
            campsites,
            "owner1"
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun blankOwnerIdReturnsEmptyList() {
        val campsites = listOf(
            createCampsite("1", "owner1"),
            createCampsite("2", "")
        )

        assertTrue(
            filterOwnerCampsites(campsites, "").isEmpty()
        )

        assertTrue(
            filterOwnerCampsites(campsites, " ").isEmpty()
        )
    }

    @Test
    fun emptyCampsiteListReturnsEmptyList() {
        val result = filterOwnerCampsites(
            emptyList(),
            "owner1"
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun newlyRegisteredCampsiteIsIncluded() {
        val campsites = mutableListOf(
            createCampsite("1", "owner1")
        )

        assertEquals(
            1,
            filterOwnerCampsites(campsites, "owner1").size
        )

        campsites.add(
            createCampsite("2", "owner1")
        )

        val result = filterOwnerCampsites(
            campsites,
            "owner1"
        )

        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "2" })
    }

    @Test
    fun expiredCampsiteRemainsVisible() {
        val campsite = createCampsite(
            "1",
            "owner1"
        ).apply {
            bookingPeriodStart = Date(0)
            bookingPeriodEnd = Date(1000)
        }

        val result = filterOwnerCampsites(
            listOf(campsite),
            "owner1"
        )

        assertEquals(1, result.size)
        assertEquals(campsite, result.first())
    }
}