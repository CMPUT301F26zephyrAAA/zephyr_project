package com.example.zephyrcampsites

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController

@Composable
internal fun AccountProfilePage(
    role: String,
    profileAccount: Account,
    activityContext: Context,
    accountsController: AccountsController,
    accountsModel: AccountsModel,
    currentAccount: MutableState<Account?>,
    profileDeletionInProgress: MutableState<Boolean>,
    navHostController: NavHostController,
    onBack: () -> Unit,
    onFinishActivity: () -> Unit
) {
    var showDeleteDialog by rememberSaveable(profileAccount.id) {
        mutableStateOf(false)
    }
    var deleteError by remember(profileAccount.id) {
        mutableStateOf(false)
    }
    val isDeleting = profileDeletionInProgress.value
    var isImageUpdating by remember(profileAccount.id) {
        mutableStateOf(false)
    }

    var imageError by remember(profileAccount.id) {
        mutableStateOf("")
    }

    val displayedAccount = currentAccount.value
        ?.takeIf { it.id == profileAccount.id }
        ?: profileAccount

    val canManageImage =
        role == "entrant" &&
                "entrant" in displayedAccount.userType &&
                currentAccount.value?.id == profileAccount.id

    AccountsView().ProfileOverviewView(
        account = displayedAccount,
        onEdit = {
            navHostController.navigate(
                roleRoute(role, GeneralScreen.PersonalInformation.route)
            )
        },
        onPolicy = {
            navHostController.navigate(
                roleRoute(role, GeneralScreen.Policy.route)
            )
        },
        onSettings = {
            navHostController.navigate(
                roleRoute(role, GeneralScreen.Settings.route)
            )
        },
        onBack = onBack,
        onDelete = {
            if (!isImageUpdating) {
                deleteError = false
                showDeleteDialog = true
            }
        },
        onImageSelected = { imageUri ->
            if (canManageImage && !isImageUpdating && !isDeleting) {

                isImageUpdating = true
                imageError = ""

                accountsController.updateOwnProfileImage(
                    context = activityContext,
                    account = displayedAccount,
                    imageUri = imageUri,
                    onSuccess = { updatedAccount ->
                        val latestAccount = currentAccount.value

                        if (latestAccount != null &&
                            latestAccount.id == updatedAccount.id
                        ) {
                            currentAccount.value = latestAccount.copy(
                                compressedImage = updatedAccount.compressedImage,
                                imagePath = updatedAccount.imagePath
                            )
                        }

                        isImageUpdating = false
                    },
                    onError = { message ->
                        imageError = message
                        isImageUpdating = false
                    }
                )
            }
        },
        onRemoveImage = {
            if (canManageImage && !isImageUpdating && !isDeleting) {

                isImageUpdating = true
                imageError = ""

                accountsController.removeOwnProfileImage(
                    context = activityContext,
                    account = displayedAccount,
                    onSuccess = { updatedAccount ->
                        val latestAccount = currentAccount.value

                        if (latestAccount != null &&
                            latestAccount.id == updatedAccount.id
                        ) {
                            currentAccount.value = latestAccount.copy(
                                compressedImage = updatedAccount.compressedImage,
                                imagePath = updatedAccount.imagePath
                            )
                        }

                        isImageUpdating = false
                    },
                    onError = { message ->
                        imageError = message
                        isImageUpdating = false
                    }
                )
            }
        },
        isImageUpdating = isImageUpdating,
        imageError = imageError,
        canManageImage = canManageImage && !isDeleting && !showDeleteDialog
    )

    if (showDeleteDialog) {
        var hasActiveReservations by remember(profileAccount.id) {
            mutableStateOf(false)
        }
        var isCheckingReservations by remember(profileAccount.id) {
            mutableStateOf(true)
        }
        var reservationCheckError by remember(profileAccount.id) {
            mutableStateOf<String?>(null)
        }
        var deletionError by remember(profileAccount.id) {
            mutableStateOf<String?>(null)
        }
        LaunchedEffect(profileAccount.id) {
            accountsModel.hasActiveReservations(
                accountId = profileAccount.id,
                onResult = { hasActive ->
                    hasActiveReservations = hasActive
                    isCheckingReservations = false
                },
                onError = { exception ->
                    reservationCheckError = exception.message
                        ?: "Unable to check reservations."
                    isCheckingReservations = false
                }
            )
        }

        AccountsView().DeleteProfileDialog(
            hasActiveReservations = hasActiveReservations,
            isCheckingReservations = isCheckingReservations,
            isDeleting = profileDeletionInProgress.value,
            reservationCheckError = reservationCheckError,
            deletionError = deletionError,
            onConfirm = {
                if (
                    !profileDeletionInProgress.value &&
                    !isCheckingReservations &&
                    reservationCheckError == null &&
                    currentAccount.value?.id == profileAccount.id
                ) {
                    profileDeletionInProgress.value = true
                    deletionError = null

                    accountsController.deleteOwnProfile(
                        context = activityContext,
                        account = profileAccount,
                        onSuccess = {
                            showDeleteDialog = false
                            currentAccount.value = null
                            profileDeletionInProgress.value = false
                            onFinishActivity()
                        },
                        onError = { exception ->
                            profileDeletionInProgress.value = false
                            deletionError = exception.message
                                ?: "Unable to delete your profile."
                        }
                    )
                }
            },
            onCancel = {
                if (!profileDeletionInProgress.value) {
                    showDeleteDialog = false
                }
            }
        )
    }
}