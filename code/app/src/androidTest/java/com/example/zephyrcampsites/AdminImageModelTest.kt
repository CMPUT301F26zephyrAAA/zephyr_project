package com.example.zephyrcampsites

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ExecutionException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks image deletion against local Firestore and Storage emulators, including denied deletions.
 */
@RunWith(AndroidJUnit4::class)
class AdminImageModelTest : AdminEmulatorTest() {
    private lateinit var storage: FirebaseStorage
    private var model: CampsiteModel? = null
    private val paths = mutableListOf<String>()

    @Before
    fun connectStorage() {
        storage =
            FirebaseStorage.getInstance(
                FirebaseApp.getInstance("admin-$suffix"),
                "gs://demo-zephyr-admin.appspot.com",
            )
        storage.useEmulator("127.0.0.1", 9199)
        storage.maxOperationRetryTimeMillis = 2000
        storage.maxUploadRetryTimeMillis = 2000
    }

    @After
    fun removeStorageFixtures() {
        model?.stopListening()
        for (path in paths) {
            val encoded = URLEncoder.encode(path, "UTF-8")
            val connection =
                URL("http://127.0.0.1:9199/v0/b/demo-zephyr-admin.appspot.com/o/$encoded")
                    .openConnection() as HttpURLConnection
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            try {
                assertTrue(connection.responseCode in listOf(200, 204, 404))
            } finally {
                connection.disconnect()
            }
        }
    }

    @Test
    fun profileImageIsDeletedWithoutDeletingTheProfile() {
        val path = upload("test-images/profile-$suffix.png")
        val id = "profile-$suffix"
        put(
            "accounts/$id",
            mapOf("name" to "Taylor Morgan", "imagePath" to path, "notificationsEnabled" to false),
        )
        removeProfile(id, path)
        val document = await(firestore.document("accounts/$id").get(Source.SERVER))
        assertTrue(document.exists())
        assertEquals("", document.getString("imagePath"))
        assertEquals("Taylor Morgan", document.getString("name"))
        assertFalse(document.getBoolean("notificationsEnabled")!!)
        assertMissing(path)
    }

    @Test
    fun deletingAnAliasedCampsiteImageClearsBothReferencesAndKeepsOtherImages() {
        val path = upload("test-images/site-$suffix.png")
        val other = upload("test-images/other-$suffix.png")
        val id = "site-$suffix"
        put(
            "campsite/$id",
            mapOf(
                "id" to id,
                "name" to "Pine Lake",
                "imagePath" to path,
                "promoImagePaths" to listOf(path, other),
            ),
        )
        model = CampsiteModel(firestore, storage)
        val result = TaskCompletionSource<Boolean>()
        model!!.removeImage(
            Campsite(id),
            path,
            { result.setResult(true) },
            { result.setException(it) },
        )
        assertTrue(await(result.task))
        val document = await(firestore.document("campsite/$id").get(Source.SERVER))
        assertTrue(document.exists())
        assertEquals("", document.getString("imagePath"))
        assertEquals(listOf(other), document.get("promoImagePaths"))
        assertMissing(path)
        assertNotNull(await(storage.reference.child(other).metadata))
    }

    @Test
    fun missingStorageFileCanStillHaveItsStaleReferenceCleared() {
        val path = "test-images/missing-$suffix.png"
        val id = "profile-$suffix"
        put("accounts/$id", mapOf("imagePath" to path))
        removeProfile(id, path)
        assertEquals(
            "",
            await(firestore.document("accounts/$id").get(Source.SERVER)).getString("imagePath"),
        )
    }

    @Test
    fun deniedStorageDeletionReturnsFailureAndKeepsTheReference() {
        val path = upload("blocked/profile-$suffix.png")
        val id = "profile-$suffix"
        put("accounts/$id", mapOf("imagePath" to path))
        val result = TaskCompletionSource<Boolean>()
        AccountsModel(firestore, storage)
            .removeProfileImage(
                Account(id),
                path,
                { result.setResult(true) },
                { result.setResult(false) },
            )
        assertFalse(await(result.task))
        assertEquals(
            path,
            await(firestore.document("accounts/$id").get(Source.SERVER)).getString("imagePath"),
        )
        assertNotNull(await(storage.reference.child(path).metadata))
    }

    @Test
    fun staleSelectionCannotRemoveTheCurrentProfileImage() {
        val old = upload("test-images/old-$suffix.png")
        val current = upload("test-images/current-$suffix.png")
        val id = "profile-$suffix"
        put("accounts/$id", mapOf("imagePath" to current))
        val result = TaskCompletionSource<Boolean>()
        AccountsModel(firestore, storage)
            .removeProfileImage(
                Account(id),
                old,
                { result.setResult(true) },
                { result.setResult(false) },
            )
        assertFalse(await(result.task))
        assertEquals(
            current,
            await(firestore.document("accounts/$id").get(Source.SERVER)).getString("imagePath"),
        )
        assertNotNull(await(storage.reference.child(old).metadata))
    }

    private fun upload(path: String): String {
        paths.add(path)
        val bytes =
            Base64.decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=",
                Base64.DEFAULT,
            )
        await(
            storage.reference
                .child(path)
                .putBytes(bytes, StorageMetadata.Builder().setContentType("image/png").build())
        )
        return path
    }

    private fun removeProfile(id: String, path: String) {
        val result = TaskCompletionSource<Boolean>()
        AccountsModel(firestore, storage)
            .removeProfileImage(
                Account(id),
                path,
                { result.setResult(true) },
                { result.setException(it) },
            )
        assertTrue(await(result.task))
    }

    private fun assertMissing(path: String) {
        try {
            await(storage.reference.child(path).metadata)
            fail("Storage file still exists")
        } catch (error: ExecutionException) {
            assertEquals(
                StorageException.ERROR_OBJECT_NOT_FOUND,
                (error.cause as StorageException).errorCode,
            )
        }
    }
}
