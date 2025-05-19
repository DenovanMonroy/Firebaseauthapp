package com.example.firebaseauthapp.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.firebaseauthapp.model.Notification
import com.example.firebaseauthapp.model.NotificationType
import com.example.firebaseauthapp.viewmodel.NotificationViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notificationViewModel: NotificationViewModel,
    onBackClick: () -> Unit
) {
    val notifications by notificationViewModel.userNotifications.collectAsState()
    val notificationState by notificationViewModel.notificationState.collectAsState()
    val unreadCount by notificationViewModel.unreadCount.collectAsState()

    // Log para depurar en tiempo de ejecución
    LaunchedEffect(Unit) {
        Log.d("NotificationsScreen", "Pantalla inicializada")
        notificationViewModel.loadUserNotifications()
    }

    LaunchedEffect(notifications) {
        Log.d("NotificationsScreen", "Notificaciones actualizadas: ${notifications.size}")
        notifications.forEach {
            Log.d("NotificationsScreen", "Notificación: ${it.id} - ${it.title}")
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(notificationState) {
        when (notificationState) {
            is NotificationViewModel.NotificationState.Success -> {
                val successMessage = (notificationState as NotificationViewModel.NotificationState.Success).message
                scope.launch {
                    snackbarHostState.showSnackbar(message = successMessage)
                }
            }
            is NotificationViewModel.NotificationState.Error -> {
                val errorMessage = (notificationState as NotificationViewModel.NotificationState.Error).message
                scope.launch {
                    snackbarHostState.showSnackbar(message = errorMessage)
                }
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Notificaciones")
                        if (unreadCount > 0) {
                            Badge(
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(unreadCount.toString())
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Notifications, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { notificationViewModel.markAllNotificationsAsRead() },
                        enabled = unreadCount > 0
                    ) {
                        Icon(
                            Icons.Default.MarkChatRead,
                            contentDescription = "Marcar todas como leídas"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (notifications.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No tienes notificaciones",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = notifications,
                        key = { it.id }
                    ) { notification ->
                        NotificationItem(
                            notification = notification,
                            onReadClick = { notificationViewModel.markNotificationAsRead(notification) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: Notification,
    onReadClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val timestamp = when (val time = notification.timestamp) {
        is Long -> dateFormat.format(Date(time))
        else -> "Ahora"
    }

    val backgroundColor = when {
        !notification.read -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val notificationType = try {
        NotificationType.valueOf(notification.notificationType)
    } catch (e: Exception) {
        NotificationType.REGULAR
    }

    val cardColor = when (notificationType) {
        NotificationType.ALERT -> MaterialTheme.colorScheme.errorContainer
        NotificationType.INFO -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Icono según tipo de notificación
                    Icon(
                        imageVector = when (notificationType) {
                            NotificationType.ALERT -> Icons.Default.NotificationsActive
                            NotificationType.INFO -> Icons.Default.Notifications
                            else -> if (notification.recipientUid == null)
                                Icons.Default.Public else Icons.Default.Person
                        },
                        contentDescription = null,
                        tint = when (notificationType) {
                            NotificationType.ALERT -> MaterialTheme.colorScheme.error
                            NotificationType.INFO -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (!notification.read) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (!notification.read) {
                    IconButton(
                        onClick = onReadClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Marcar como leída",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "De: ${notification.senderName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}