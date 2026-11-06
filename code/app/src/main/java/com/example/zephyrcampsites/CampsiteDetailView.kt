package com.example.zephyrcampsites

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
class CampsiteDetailView {
    /**
     * Displaying the details of a certain campsite.
     *
     * @param campsite The campsite to display the details of
     * @param codeGenerator Function to generate the QR code to this campsite
     */
    @Composable
    fun DetailView(
        campsite: Campsite?,
        codeGenerator: (String, Int) -> ImageBitmap,
        onManageLottery: (String) -> Unit = {},
        lottery: Lottery? = null,
        accountId: String? = null,
        waitingListController: WaitingListController? = null
    )  {
        var codeShow by remember { mutableStateOf(false) }
        var joined by remember(lottery?.id, accountId) {
            mutableStateOf(false)
        }
        var checkingMembership by remember(lottery?.id, accountId) {
            mutableStateOf(true)
        }
        var joining by remember { mutableStateOf(false) }

        var showLeaveConfirmation by remember(lottery?.id, accountId){
            mutableStateOf(false)
        }

        var leaving by remember {
            mutableStateOf(false)
        }
        var joinError by remember {
            mutableStateOf("")
        }
        LaunchedEffect(lottery?.id, accountId) {
            joinError = ""
            if (lottery == null || accountId.isNullOrBlank() ||
                waitingListController == null
            ) {
                checkingMembership = false
            } else {
                checkingMembership = true
                waitingListController.isEntrantJoined(
                    lotteryId = lottery.id,
                    accountId = accountId,
                    onResult = { isJoined ->
                        joined = isJoined
                        checkingMembership = false
                    },
                    onError = { error ->
                        joinError = error
                        checkingMembership = true
                    }
                )
            }
        }
        val code = codeGenerator("campsite", campsite!!.id.toInt())
        val fileSaver = PermissionHandlers().imageSaveHandler(code)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Spacer(modifier = Modifier.size(30.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = campsite.name,
                    fontSize = 30.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = campsite.maxOccupancy.toString(),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                if (codeShow) {
                    Image(
                        bitmap = code,
                        contentDescription = "Generated QR code of user"
                    )
                }
            }
            Spacer(modifier = Modifier.size(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { codeShow = !codeShow }) {
                    Text(text = "Generate/Hide QR")
                }
                Spacer(modifier = Modifier.weight(1f))
                if (codeShow) {
                    Button(onClick = {
                        fileSaver.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/png"
                            putExtra(Intent.EXTRA_TITLE, "campsite-qr.png")
                        })
                    }) {
                        Text(text = "Download/Save QR")
                    }
                }
            }

            // Entrant lottery registration
            if (
                lottery != null &&
                !accountId.isNullOrBlank() &&
                waitingListController != null
            ) {
                Spacer(modifier = Modifier.size(16.dp))
                Button(
                    onClick = {
                        joining = true
                        joinError = ""
                        waitingListController.joinWaitingList(
                            lotteryId = lottery.id,
                            accountId = accountId,
                            onSuccess = {
                                joined = true
                                joining = false
                            },
                            onError = { error ->
                                joinError = error
                                joining = false
                            }
                        )
                    },
                    enabled = !joined &&
                            !checkingMembership &&
                            !joining &&
                            !leaving &&
                            System.currentTimeMillis() >= lottery.registrationStart &&
                            System.currentTimeMillis() < lottery.registrationEnd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            checkingMembership -> "Checking..."
                            joining -> "Joining..."
                            joined -> "Joined"
                            else -> "Join Lottery"
                        }
                    )
                }
                if (joinError.isNotEmpty()) {
                    Text(
                        text = joinError,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (joined){
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedButton(
                        onClick = {
                            showLeaveConfirmation = true
                        },
                        enabled = !leaving && !joining,
                        modifier = Modifier.fillMaxWidth()
                    ){
                        Text(
                            if (leaving) "Leaving..."
                            else "Leave Waiting List"
                        )
                    }
                }
                if (showLeaveConfirmation) {
                    Dialog( //https://developer.android.com/develop/ui/compose/components/dialog
                        onDismissRequest = {
                            if (!leaving) showLeaveConfirmation = false
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(20.dp)
                        ) {
                            Text("Are you sure you want to leave the waiting list?")
                            Button(
                                onClick = {
                                    leaving = true
                                    joinError = ""

                                    waitingListController.leaveWaitingList(
                                        lotteryId = lottery.id,
                                        accountId = accountId,
                                        onSuccess = {
                                            joined = false
                                            leaving = false
                                            showLeaveConfirmation = false
                                        },
                                        onError = { error ->
                                            leaving = false
                                            showLeaveConfirmation = false
                                            joinError = error
                                        }
                                    )
                                },
                                enabled = !leaving
                            ) {
                                Text(if (leaving) "Leaving..." else "Yes, Leave")
                            }

                            Button(
                                onClick = { showLeaveConfirmation = false },
                                enabled = !leaving
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }

            }

            // Manage campsite lottery
            Spacer(modifier = Modifier.size(16.dp))

            Button(
                onClick = { onManageLottery(campsite.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Lottery")
            }
        }
    }
}