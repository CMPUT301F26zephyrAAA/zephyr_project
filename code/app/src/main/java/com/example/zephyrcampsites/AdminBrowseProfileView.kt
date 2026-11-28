// Hiral - [AI-10] OpenAI ChatGPT Add admin removal flow
// Renders administrator profile browsing and moderation.
package com.example.zephyrcampsites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon

/**
 * Separate View class exclusively for Admin to browse and manage profiles:
 * 1. AdminProfileListView: Scroll through list of all profiles and select one.
 * 2. AdminProfileDetailView: View individual profile details, go back, or delete profile with
 *    confirmation.
 */
class AdminBrowseProfileView {

    /**
     * Displays all registered profiles. Updates automatically when firestore updates.
     *
     * @param onProfileSelected Receives the selected profile and whether it was opened from Owners.
     * @param onBack Returns to the previous screen
     * @param registeredOwnerIds Accounts linked to at least one saved campsite.
     */
    @Composable
    fun AdminProfileListView(
        account: Account?,
        accountsModel: AccountsModel,
        onProfileSelected: (Account, Boolean) -> Unit,
        onBack: () -> Unit,
        loadImage: (String, (String?) -> Unit) -> Unit = { _, result -> result(null) },
        registeredOwnerIds: Set<String> = emptySet(),
        loadingCampsites: Boolean = false,
        campsiteLoadError: String? = null,
    ) {
        if (account == null || "admin" !in account.userType || account.id.isBlank()) {
            Text("Administrator access required.")
            return
        }
        var accounts by remember { mutableStateOf(emptyList<Account>()) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        DisposableEffect(accountsModel) {
            val listener =
                accountsModel.observeAccounts(
                    onSuccess = {
                        accounts = it
                        errorMessage = null
                        isLoading = false
                    },
                    onError = {
                        errorMessage = it.message ?: "Failed to load profiles."
                        isLoading = false
                    },
                )

            onDispose { listener.remove() }
        }

        var selectedTab by rememberSaveable { mutableStateOf("All") }
        val visibleAccounts = when (selectedTab) {
            "Entrants" -> accounts.filter { "entrant" in it.userType }
            "Owners" -> accounts.filter { it.id in registeredOwnerIds }
            else -> accounts
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("BROWSE PROFILES", style = MaterialTheme.typography.headlineMedium)
            ProfileTabs(selectedTab) { selectedTab = it }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    isLoading -> item { Text("Loading...") }
                    errorMessage != null -> item {
                        Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error)
                    }
                    selectedTab == "Owners" && loadingCampsites -> item { Text("Loading...") }
                    selectedTab == "Owners" && campsiteLoadError != null -> item {
                        Text(campsiteLoadError, color = MaterialTheme.colorScheme.error)
                    }
                    visibleAccounts.isEmpty() -> item { Text("No profiles found.") }
                    else -> items(visibleAccounts, key = { it.id }) { profile ->
                        OutlinedCard(
                            onClick = { onProfileSelected(profile, selectedTab == "Owners") },
                            modifier = Modifier.fillMaxWidth().testTag("ProfileItem_${profile.id}"),
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(end = 28.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(Modifier.width(56.dp)) {
                                        AdminProfileImage(
                                            profile.imagePath, loadImage, profile.compressedImage,
                                            compact = true,
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            profile.name.ifBlank { "Unnamed User" },
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text("Roles: ${profile.userType.joinToString()} | ID: ${profile.id}",
                                            style = MaterialTheme.typography.bodySmall)
                                        Text("Email: ${profile.email.ifBlank { "N/A" }}",
                                            style = MaterialTheme.typography.bodySmall)
                                        Text("Phone Number: ${profile.phoneNumber.ifBlank { "N/A" }}",
                                            style = MaterialTheme.typography.bodySmall)
                                        if (profile.ownerPrivilegesRevoked) {
                                            Text("Owner privileges removed",
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                )
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("BACK")
            }
        }
    }

    /** Displays the segmented role filters from the supplied mockup. */
    @Composable
    private fun ProfileTabs(selected: String, onSelected: (String) -> Unit) {
        val shape = RoundedCornerShape(10.dp)
        val outline = Color(0xFF454545)
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(shape)
                .border(1.dp, outline, shape).selectableGroup(),
        ) {
            listOf("All", "Entrants", "Owners").forEachIndexed { index, label ->
                if (index > 0) {
                    Spacer(Modifier.fillMaxHeight().width(1.dp).background(outline))
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .background(if (label == selected) Color.White else Color(0xFFDEDEDE))
                        .selectable(label == selected, role = Role.Tab) { onSelected(label) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = Color.Black)
                }
            }
        }
    }

    /**
     * Displays details of a selected profile with options to delete or go back to browse.
     *
     * @param targetAccount The selected account to inspect/delete
     * @param accountsController Controller handling deletion logic
     * @param onDeleteSuccess Callback triggered after profile is successfully deleted
     * @param onBack Returns to the profile browse list
     */
    @Composable
    fun AdminProfileDetailView(
        account: Account?,
        targetAccount: Account,
        accountsController: AccountsController,
        onDeleteSuccess: () -> Unit,
        onBack: () -> Unit,
        loadImage: (String, (String?) -> Unit) -> Unit = { _, result -> result(null) },
        showOwnerRemoval: Boolean = false,
    ) {
        if (account == null || "admin" !in account.userType || account.id.isBlank()) {
            Text("Administrator access required.")
            return
        }
        var showDeleteConfirmation by remember(targetAccount.id) { mutableStateOf(false) }
        var errorMessage by remember(targetAccount.id) { mutableStateOf("") }
        var isDeleting by remember(targetAccount.id) { mutableStateOf(false) }
        var showOwnerConfirmation by remember(targetAccount.id) { mutableStateOf(false) }
        var reason by rememberSaveable(targetAccount.id) { mutableStateOf("") }
        var ownerRemoved by rememberSaveable(targetAccount.id) {
            mutableStateOf(targetAccount.ownerPrivilegesRevoked)
        }
        val canRemoveOwner = showOwnerRemoval && !ownerRemoved &&
            "owner" in targetAccount.userType && account.id != targetAccount.id
        BackHandler(enabled = isDeleting) {}

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "PROFILE DETAILS",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            AdminProfileImage(targetAccount.imagePath, loadImage, targetAccount.compressedImage)
            Text(
                text =
                    "Name: ${if(targetAccount.name.isBlank()) { "N/A" } else { targetAccount.name }}",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text =
                    "Email: ${if(targetAccount.email.isBlank()) { "N/A" } else { targetAccount.email }}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    "Phone Number: ${if(targetAccount.phoneNumber.isBlank()) { "N/A" } else { targetAccount.phoneNumber }}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Roles: ${targetAccount.userType.joinToString()}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Device ID: ${targetAccount.id}",
                style = MaterialTheme.typography.bodySmall,
            )

            if (errorMessage.isNotEmpty()) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.weight(1f))

            if (ownerRemoved) {
                Text("Owner privileges removed.")
            } else if (canRemoveOwner) {
                Button(
                    onClick = { showOwnerConfirmation = true },
                    enabled = !isDeleting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Remove Owner Privileges")
                }
            }

            // Delete profile button
            Button(
                onClick = { showDeleteConfirmation = true },
                enabled = !isDeleting,
                modifier = Modifier.fillMaxWidth().testTag("AdminDeleteProfileButton"),
                colors =
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Remove Profile")
            }

            OutlinedButton(
                onClick = onBack,
                enabled = !isDeleting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("BACK")
            }
        }

        if (showOwnerConfirmation && canRemoveOwner) {
            AlertDialog(
                onDismissRequest = { showOwnerConfirmation = false },
                title = { Text("Remove owner privileges?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("This hides the owner's campsites and blocks new registrations and bookings. Existing records are kept.")
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Reason for removal") },
                            supportingText = { Text("${reason.length}/500") },
                            isError = reason.length > 500,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = reason.isNotBlank() && reason.length <= 500,
                        onClick = {
                            showOwnerConfirmation = false
                            isDeleting = true
                            errorMessage = ""
                            accountsController.removeOwnerPrivileges(
                                account, targetAccount, reason,
                                onSuccess = {
                                    isDeleting = false
                                    ownerRemoved = true
                                },
                                onError = {
                                    isDeleting = false
                                    errorMessage = it.message ?: "Failed to remove owner privileges."
                                },
                            )
                        },
                    ) { Text("Remove Privileges") }
                },
                dismissButton = {
                    TextButton(onClick = { showOwnerConfirmation = false }) { Text("Cancel") }
                },
            )
        }

        // Confirmation Dialog before deletion
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Confirm Profile Removal") },
                text = {
                    Text(
                        "Are you sure you want to remove '${targetAccount.name.ifBlank { targetAccount.id }}'? This will remove them from all waiting lists, cancel active reservations, and delete the profile."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation = false
                            isDeleting = true
                            errorMessage = ""

                            accountsController.deleteProfile(
                                account = account,
                                targetAccount = targetAccount,
                                onSuccess = {
                                    isDeleting = false
                                    onDeleteSuccess()
                                },
                                onError = { e ->
                                    isDeleting = false
                                    errorMessage = e.message ?: "Failed to delete profile."
                                },
                            )
                        },
                        modifier = Modifier.testTag("ConfirmRemoveProfile"),
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }

    /** Loads a selected profile by ID before displaying its authorized admin controls. */
    @Composable
    fun AdminProfileDetailScreen(
        account: Account?,
        profileId: String,
        controller: AccountsController,
        onRemoved: () -> Unit,
        onBack: () -> Unit,
        loadImage: (String, (String?) -> Unit) -> Unit = { _, result -> result(null) },
        showOwnerRemoval: Boolean = false,
    ) {
        if (account == null || "admin" !in account.userType || account.id.isBlank()) {
            Text("Administrator access required.")
            return
        }
        var profile by remember(profileId) { mutableStateOf<Account?>(null) }
        var loading by remember(profileId) { mutableStateOf(true) }
        var error by remember(profileId) { mutableStateOf<String?>(null) }
        DisposableEffect(profileId) {
            var active = true
            controller.findAccount(
                profileId,
                onResult = {
                    if (active) {
                        profile = it
                        loading = false
                    }
                },
                onError = {
                    if (active) {
                        error = it.message ?: "Unable to load profile."
                        loading = false
                    }
                },
            )
            onDispose { active = false }
        }
        val selected = profile
        when {
            loading -> Text("Loading profile...")
            selected != null ->
                AdminProfileDetailView(
                    account, selected, controller, onRemoved, onBack, loadImage, showOwnerRemoval,
                )
            else ->
                Column {
                    Text(error ?: "Profile no longer exists.")
                    OutlinedButton(onClick = onBack) { Text("BACK") }
                }
        }
    }
}

/** Resolves an optional profile image and displays a fallback when it is absent or fails. */
@Composable
fun AdminProfileImage(
    path: String,
    loadImage: (String, (String?) -> Unit) -> Unit,
    compressedImage: String = "",
    compact: Boolean = false,
) {
    if (compressedImage.isNotBlank()) {
        AdminCompressedImage(
            encodedImage = compressedImage,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (compact) 56.dp else 150.dp)
        )
        return
    }
    var url by remember(path) { mutableStateOf<String?>(null) }
    DisposableEffect(path) {
        var active = true
        if (path.isNotBlank()) {
            loadImage(path) {
                if (active) {
                    url = it
                }
            }
        }
        onDispose { active = false }
    }
    if (url == null) {
        Column {
            Icon(
                Icons.Default.Person,
                contentDescription = "Default profile image",
                modifier = Modifier.height(48.dp),
            )
            Text("No profile image", style = MaterialTheme.typography.bodySmall)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = "Profile image",
            onError = { url = null },
            modifier = Modifier.fillMaxWidth().heightIn(max = if (compact) 56.dp else 150.dp),
        )
    }
}
