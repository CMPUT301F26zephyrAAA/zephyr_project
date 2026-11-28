package com.example.zephyrcampsites

// Exercises the owner form and checks the published pin and compressed photos in local Firestore.
// AI Citation [AI-51]
// OpenAI, Codex, November 27, 2026. Generated the registration regression test.
// See Bhav Saluja's entry in the team's AI Documentation Google Doc.

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses firebase.notifications.test.json with the Auth and Firestore emulators.
 * Requires the campsitePublishEmulator runner argument and live OpenFreeMap tiles.
 */
@RunWith(AndroidJUnit4::class)
class CampsitePublishTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var app: FirebaseApp
    private lateinit var firestore: FirebaseFirestore
    private lateinit var model: CampsiteModel
    private val photos = mutableListOf<File>()

    @After
    fun releaseFixtures() {
        if (::model.isInitialized) {
            compose.runOnIdle { model.stopListening() }
        }
        if (::firestore.isInitialized) {
            await(firestore.terminate())
        }
        if (::app.isInitialized) {
            app.delete()
        }
        photos.forEach { it.delete() }
    }

    @Test
    fun ownerCanBackUpAndPublishTheConfirmedPinAndSelectedPhotos() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("campsitePublishEmulator") == "true"
        )
        val ownerId = createOwner()
        val photoPicker = TestPhotoPicker(listOf(createPhoto(Color.GREEN), createPhoto(Color.BLUE)))
        val published = AtomicBoolean()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides photoPicker) {
                ZephyrCampsitesTheme {
                    CampsiteFormView(
                        controller = CampsiteController(model),
                        ownerId = ownerId,
                        onDone = { published.set(true) },
                        onBack = { error("Back from the location step should show campsite details.") }
                    )
                }
            }
        }
        compose.waitUntil(10_000) { !model.isLoading }

        enter("Campsite Name:", "Pine Lake")
        enter("Location:", "Alberta")
        enter("Max Occupancy:", "4")
        enter("Price / Night:", "25")
        enter("Tags:", "Lakeside, Shaded")
        click("NEXT")

        val publish = compose.onNodeWithText("PUBLISH")
        publish.performScrollTo().assertIsNotEnabled()
        click("Choose on map")
        val ready = hasText("Use this location") and hasClickAction() and isEnabled()
        compose.waitUntil(25_000) { compose.onAllNodes(ready).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(ready).performClick()

        click("SET DATES")
        compose.onNodeWithText("Select Start Date").assertExists()
        clickCalendarDay("15")
        compose.onNodeWithText("NEXT").performClick()
        compose.onNodeWithText("Select End Date").assertExists()
        clickCalendarDay("17")
        compose.onNodeWithText("SAVE").performClick()

        click("ADD PHOTOS")
        compose.onNodeWithText("2 photos selected").assertExists()
        click("ADD PHOTOS") // The second picker result is a cancellation.
        compose.onNodeWithText("2 photos selected").assertExists()

        Espresso.pressBack()
        compose.onNodeWithText("Campsite Name:").assertExists()
        click("NEXT")
        compose.onNodeWithText("2 photos selected").assertExists()
        publish.performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(15_000) { published.get() }

        val document = await(
            firestore.collection("campsite").whereEqualTo("ownerID", ownerId).get(Source.SERVER)
        ).documents.single()
        assertEquals(document.id, document.getString("id"))
        assertEquals("Pine Lake", document.getString("name"))
        assertEquals("Alberta", document.getString("location"))
        assertEquals(4L, document.getLong("maxOccupancy"))
        assertEquals(listOf("Lakeside", "Shaded"), document.get("tags"))
        val pin = requireNotNull(document.getGeoPoint("geolocation"))
        assertEquals(53.55, pin.latitude, 0.001)
        assertEquals(-116.0, pin.longitude, 0.001)
        val start = requireNotNull(document.getDate("bookingPeriodStart"))
        val end = requireNotNull(document.getDate("bookingPeriodEnd"))
        assertEquals(TimeUnit.DAYS.toMillis(2), end.time - start.time)

        val images = document.get("compressedImages") as List<*>
        assertEquals(2, images.size)
        for (image in images) {
            val jpeg = Base64.decode(image as String, Base64.NO_WRAP)
            val metadata = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, metadata)
            assertEquals("image/jpeg", metadata.outMimeType)
            assertTrue(metadata.outWidth in 1..1024 && metadata.outHeight in 1..1024)
            assertTrue(jpeg.size <= 150 * 1024)
        }
    }

    private fun createOwner(): String {
        val options = FirebaseOptions.Builder()
            .setProjectId("demo-zephyr")
            .setApplicationId("1:123456789:android:0123456789abcdef")
            .setApiKey("AIza" + "x".repeat(35))
            .build()
        app = FirebaseApp.initializeApp(context, options, "campsite-publish-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", 9099) }
        firestore = FirebaseFirestore.getInstance(app).apply { useEmulator("127.0.0.1", 8082) }
        val ownerId = "auth_${requireNotNull(await(auth.signInAnonymously()).user).uid}"
        await(
            firestore.collection("accounts").document(ownerId).set(
                mapOf(
                    "id" to ownerId,
                    "userType" to listOf("entrant", "owner"),
                    "notificationsEnabled" to true
                )
            )
        )
        compose.runOnIdle { model = CampsiteModel(firestore) }
        return ownerId
    }

    private fun createPhoto(color: Int): Uri {
        val file = File.createTempFile("campsite-photo-", ".png", context.cacheDir)
        photos.add(file)
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    private fun enter(label: String, value: String) {
        compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextInput(value)
    }

    private fun click(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
    }

    private fun clickCalendarDay(day: String) {
        val dayPattern = Regex("\\b$day\\b")
        val date = SemanticsMatcher("calendar day $day") { node ->
            node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].any { dayPattern.containsMatchIn(it.text) }
        }
        compose.onNode(date and hasClickAction()).performClick()
    }

    private fun <T> await(task: Task<T>): T = Tasks.await(task, 20, TimeUnit.SECONDS)

    /** Delivers one photo selection and then an empty result through the Activity Result Registry. */
    private class TestPhotoPicker(private var selection: List<Uri>) : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                dispatchResult(requestCode, selection)
                selection = emptyList()
            }
        }
    }
}
