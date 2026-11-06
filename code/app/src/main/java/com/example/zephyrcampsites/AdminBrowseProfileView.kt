package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Separate View class exclusively for Admin to browse and manage profiles:
 * 1. AdminProfileListView: Scroll through list of all profiles and select one.
 * 2. AdminProfileDetailView: View individual profile details, go back, or delete profile with confirmation.
 */
class AdminBrowseProfileView {

    /**
     * Displays all registered profiles. Updates automatically when firestore updates.
     *
     * @param onProfileSelected Callback triggered when a profile item is clicked
     * @param onBack Returns to the previous screen
     */
    @Composable
    fun AdminProfileListView(
        onProfileSelected: (Account) -> Unit,
        onBack: () -> Unit
    ) {
        val accountsModel = AccountsModel()
        var accounts by remember { mutableStateOf(emptyList<Account>()) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        DisposableEffect(accountsModel) {
            val listener = accountsModel.observeAccounts(
                onSuccess = {
                    accounts = it
                    isLoading = false
                },
                onError = {
                    errorMessage = it.message ?: "Failed to load profiles."
                    isLoading = false
                }
            )

            onDispose { listener.remove() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "BROWSE PROFILES",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isLoading) {
                Text("Loading...")
            } else if (errorMessage != null) {
                Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error)
            }

            if (accounts.isEmpty()) {
                Text("No profiles found.")
            }
            else {
                accounts.forEach { account ->
                    OutlinedButton(
                        onClick = { onProfileSelected(account) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ProfileItem_${account.id}")
                    ) {
                        Column(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if(account.name.isBlank()) {
                                    "Unnamed User"
                                } else {
                                    account.name
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Role: ${account.userType} | ID: ${account.id}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Email: ${if(account.email.isBlank()) "N/A" else account.email}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Phone Number: ${if (account.phoneNumber.isBlank()) "N/A" else account.phoneNumber}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BACK")
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
        targetAccount: Account,
        accountsController: AccountsController,
        onDeleteSuccess: () -> Unit,
        onBack: () -> Unit
    ) {
        var showDeleteConfirmation by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }
        var isDeleting by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "PROFILE DETAILS",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Name: ${if(targetAccount.name.isBlank()) "N/A" else targetAccount.name}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Email: ${if(targetAccount.email.isBlank()) "N/A" else targetAccount.email}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Phone Number: ${if(targetAccount.phoneNumber.isBlank()) "N/A" else targetAccount.phoneNumber}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "User Type: ${targetAccount.userType}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Device ID: ${targetAccount.id}",
                style = MaterialTheme.typography.bodySmall
            )

            if(errorMessage.isNotEmpty()) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Delete profile button
            Button(
                onClick = { showDeleteConfirmation = true },
                enabled = !isDeleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("AdminDeleteProfileButton"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Remove Profile")
            }

            OutlinedButton(
                onClick = onBack,
                enabled = !isDeleting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BACK")
            }
        }

        // Confirmation Dialog before deletion
        if(showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Confirm Profile Removal") },
                text = { Text("Are you sure you want to remove '${targetAccount.name.ifBlank { targetAccount.id }}'? This will remove them from all waiting lists, cancel active reservations, and delete the profile.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation = false
                            isDeleting = true
                            errorMessage = ""

                            accountsController.deleteProfile(
                                targetAccount = targetAccount,
                                onSuccess = {
                                    isDeleting = false
                                    onDeleteSuccess()
                                },
                                onError = { e ->
                                    isDeleting = false
                                    errorMessage = e.message ?: "Failed to delete profile."
                                }
                            )
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}