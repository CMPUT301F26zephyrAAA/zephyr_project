package com.example.zephyrcampsites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun TripsScreen(
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    LotteryListScreen(title = "Trips", onBack = onBack, content = content)
}

@Composable
internal fun LotteryListScreen(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(8.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E7D32),
                contentColor = Color.White
            )
        ) {
            Text("Back")
        }

        Text(title, style = MaterialTheme.typography.headlineMedium)
        LazyColumn(modifier = Modifier.weight(1f), content = content)
    }
}
@Composable
internal fun LotteryListItem(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp)
    )
}

@Composable
internal fun ReservationsScreen(onBack: () -> Unit) {
    FeaturePage("View Reservations", onBack)
}

@Composable
internal fun NotificationLogsScreen(onBack: () -> Unit) {
    FeaturePage("Notification Logs", onBack)
}

@Composable
internal fun MessageLogsScreen(onBack: () -> Unit) {
    FeaturePage("Message Logs", onBack)
}

@Composable
internal fun MakeAdminScreen(onBack: () -> Unit) {
    FeaturePage("Make Admin", onBack)
}

@Composable
internal fun FeaturePage(title: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack) { Text("Back") }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}