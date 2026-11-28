// Shows an owner's lottery listings by registration status.
package com.example.zephyrcampsites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun OwnerLotteriesView(
    accountId: String,
    lotteries: List<Lottery>,
    canCreateLottery: Boolean,
    onBack: () -> Unit,
    onCreateLottery: () -> Unit,
    onOpenLottery: (String) -> Unit
) {
    var showPast by rememberSaveable(accountId) { mutableStateOf(false) }
    val registrationTimes = lotteries.flatMap { listOf(it.registrationStart, it.registrationEnd) }
    val now by produceState(System.currentTimeMillis(), registrationTimes) {
        value = System.currentTimeMillis()
        for (time in registrationTimes.distinct().sorted()) {
            if (time > value) {
                delay((time - System.currentTimeMillis()).coerceAtLeast(0))
                value = System.currentTimeMillis()
            }
        }
    }
    val visibleLotteries = lotteries.filter { (it.registrationEnd <= now) == showPast }

    LotteryListScreen(
        title = "My Lotteries",
        onBack = onBack,
        onCreateLottery = onCreateLottery,
        canCreateLottery = canCreateLottery,
        header = {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                listOf("Active", "Past").forEachIndexed { index, label ->
                    val pastTab = index == 1
                    SegmentedButton(
                        selected = showPast == pastTab,
                        onClick = { showPast = pastTab },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = 2,
                            baseShape = RoundedCornerShape(8.dp)
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Color.White,
                            activeContentColor = Color.Black,
                            inactiveContainerColor = Color(0xFFDEDEDE),
                            inactiveContentColor = Color.Black
                        ),
                        icon = {}
                    ) {
                        Text(label)
                    }
                }
            }
        }
    ) {
        if (visibleLotteries.isEmpty()) {
            item {
                Text(
                    text = if (showPast) "No past lotteries." else "No active lotteries.",
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        items(visibleLotteries, key = { it.id }) { lottery ->
            val status = when {
                lottery.drawCompleted -> "Winners drawn"
                now >= lottery.registrationEnd -> "Closed"
                now < lottery.registrationStart -> "Registration upcoming"
                else -> "Registration open"
            }
            OwnerLotteryCard(lottery.lotteryName, status) { onOpenLottery(lottery.id) }
        }
    }
}

@Composable
private fun OwnerLotteryCard(name: String, status: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(
                start = 14.dp,
                top = 9.dp,
                end = 10.dp,
                bottom = 6.dp
            )
        ) {
            Column(modifier = Modifier.padding(end = 32.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(status, style = MaterialTheme.typography.labelSmall)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}
