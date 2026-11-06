package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zephyrcampsites.ui.theme.ProfileMenuGreen

/**
 * Displays account notification settings and saves preference changes.
 * Outstanding issue: notification senders must honor the saved preference before sending.
 */
class SettingsView {
    /**
     * Connects notification settings to the account controller.
     *
     * @param account Account whose settings are displayed.
     * @param accountsController Controller used to save changes.
     * @param onSaved Receives the account after a successful save.
     * @param onBack Returns to the previous screen.
     */
    @Composable
    fun SettingsScreen(
        account: Account,
        accountsController: AccountsController,
        onSaved: (Account) -> Unit,
        onBack: () -> Unit
    ) {
        var saving by remember(account.id) { mutableStateOf(false) }
        var errorMessage by remember(account.id) { mutableStateOf<String?>(null) }

        SettingsView(
            notificationsEnabled = account.notificationsEnabled,
            saving = saving,
            errorMessage = errorMessage,
            onNotificationsChanged = { enabled ->
                saving = true
                errorMessage = null
                accountsController.updateNotificationPreference(
                    account = account,
                    enabled = enabled,
                    onSuccess = {
                        onSaved(it)
                        saving = false
                    },
                    onError = {
                        errorMessage = it
                        saving = false
                    }
                )
            },
            onBack = onBack
        )
    }

    /**
     * Displays the saved notification preference and save feedback.
     *
     * @param notificationsEnabled Current saved preference.
     * @param saving Whether a preference update is pending.
     * @param errorMessage Save failure message, or null if there is no failure.
     * @param onNotificationsChanged Requests a new notification preference.
     * @param onBack Returns to the previous screen.
     */
    @Composable
    fun SettingsView(
        notificationsEnabled: Boolean,
        saving: Boolean = false,
        errorMessage: String? = null,
        onNotificationsChanged: (Boolean) -> Unit,
        onBack: () -> Unit
    ) {
        val notificationsTitle = stringResource(R.string.settings_notifications_title)
        Scaffold(
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.width(104.dp).testTag("SettingsBack"),
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.settings_back))
                    }
                }
            }
        ) { contentPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("AccountSettingsScreen"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.settings_title), fontSize = 12.sp, lineHeight = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(notificationsTitle, fontSize = 15.sp, lineHeight = 22.sp)
                        Text(stringResource(R.string.settings_notifications_description), fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onNotificationsChanged,
                        enabled = !saving,
                        modifier = Modifier.testTag("NotificationsToggle").semantics {
                            contentDescription = notificationsTitle
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = ProfileMenuGreen,
                            checkedThumbColor = Color.White
                        )
                    )
                }
                if (saving) {
                    Text(stringResource(R.string.settings_saving), modifier = Modifier.testTag("SettingsSaving"))
                }
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("SettingsError"))
                }
            }
        }
    }
}
