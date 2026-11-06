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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Displays the app policy and guidelines from local string resources.
 * Shared bottom navigation will be integrated separately.
 */
class PolicyView {
    /**
     * Displays scrollable guidelines with a fixed Back button.
     *
     * @param onBack Returns to the previous screen.
     */
    @Composable
    fun PolicyAndGuidelinesView(onBack: () -> Unit) {
        val sections = listOf(
            R.string.policy_account_heading to R.string.policy_account_body,
            R.string.policy_lottery_heading to R.string.policy_lottery_body,
            R.string.policy_reservations_heading to R.string.policy_reservations_body,
            R.string.policy_rules_heading to R.string.policy_rules_body,
            R.string.policy_notifications_heading to R.string.policy_notifications_body,
            R.string.policy_ratings_heading to R.string.policy_ratings_body
        )

        Scaffold(
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.width(104.dp).testTag("PolicyBack"),
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.policy_back))
                    }
                }
            }
        ) { contentPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)
                    .testTag("PolicyContent"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.policy_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                for ((heading, body) in sections) {
                    Text(stringResource(heading), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
