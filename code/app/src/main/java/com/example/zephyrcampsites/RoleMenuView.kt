package com.example.zephyrcampsites

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


internal data class RoleAction(
    val label: String,
    val onClick: () -> Unit
)

@Composable
internal fun RolePage(
    actions: List<RoleAction>,
    onBack: (() -> Unit)? = null,
    actionsEnabled: Boolean = true,
    statusMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val isLandingPage = onBack == null
    val menuScrollState = rememberScrollState()
    val shape = RoundedCornerShape(10.dp)
    val green = Color(0xFF2E7D32)
    val menuButtonColors = ButtonDefaults.buttonColors(
        containerColor = if (isLandingPage) Color.White else green,
        contentColor = if (isLandingPage) Color.Black else Color.White
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isLandingPage) {
            Image(
                painter = painterResource(id = R.drawable.bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isLandingPage) 0.dp else 72.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .size(width = 600.dp, height = 600.dp)
                    .verticalScroll(menuScrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement =
                    if (isLandingPage) Arrangement.Top else Arrangement.Center
            ) {
                if (isLandingPage) {
                    Text(
                        text = "CAMPS\nALBERTA",
                        modifier = Modifier.padding(vertical = 25.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 55.sp,
                        lineHeight = 60.sp,
                        color = Color.White
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(25.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (action in actions) {
                        Button(
                            onClick = action.onClick,
                            enabled = actionsEnabled,
                            modifier = Modifier.size(
                                width = if (isLandingPage) 200.dp else 240.dp,
                                height = 60.dp
                            ),
                            shape = shape,
                            colors = menuButtonColors
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontSize = if (isLandingPage) 25.sp else 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (statusMessage != null) {
                        Text(
                            text = statusMessage,
                            color = if (isLandingPage) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textAlign = TextAlign.Center
                        )
                    }

                    if (onRetry != null) {
                        Button(onClick = onRetry, shape = shape, colors = menuButtonColors) {
                            Text("Retry")
                        }
                    }
                }
            }
            if (!isLandingPage && menuScrollState.canScrollForward) {
                Text(
                    text = "More options ↓",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (onBack != null) {
            Button(onClick = onBack,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = green,
                    contentColor = Color.White
                )
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
internal fun RoleMenuWithSwitcher(role: String,
                                  actions: List<RoleAction>,
                                  isAdmin: Boolean,
                                  onBack: () -> Unit,
                                  openRoleGraph: (String) -> Unit) {
    var showRoleSwitcher by rememberSaveable(role) { mutableStateOf(false) }

    RolePage(
        actions = actions + RoleAction("Switch Role") {
            showRoleSwitcher = true
        },
        onBack = onBack
    )

    if (showRoleSwitcher) {
        val availableRoles = buildList {
            add(ENTRANT to "Entrant")
            add(OWNER to "Owner")
            if (isAdmin) add(ADMIN to "Admin")
        }

        AlertDialog(
            onDismissRequest = { showRoleSwitcher = false },
            title = { Text("Switch role") },
            text = {
                Column {
                    for ((targetRole, label) in availableRoles) {
                        if (targetRole != role) {
                            TextButton(
                                onClick = {
                                    showRoleSwitcher = false
                                    openRoleGraph(targetRole)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black,
                                    contentColor = Color.White)
                            ) {
                                Text(label,
                                    style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRoleSwitcher = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White)) {
                    Text("Cancel",
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        )
    }
}