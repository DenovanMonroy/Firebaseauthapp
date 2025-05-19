package com.example.firebaseauthapp.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.firebaseauthapp.model.NotificationType
import com.example.firebaseauthapp.model.User
import com.example.firebaseauthapp.viewmodel.NotificationViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminNotificationsScreen(
    notificationViewModel: NotificationViewModel,
    onBackClick: () -> Unit
) {
    // Log para depurar en tiempo de ejecución
    LaunchedEffect(Unit) {
        Log.d("AdminNotificationsScreen", "Pantalla inicializada")
    }

    val allUsers by notificationViewModel.allUsers.collectAsState()
    val notificationState by notificationViewModel.notificationState.collectAsState()

    LaunchedEffect(allUsers) {
        Log.d("AdminNotificationsScreen", "Usuarios cargados: ${allUsers.size}")
        allUsers.forEach {
            Log.d("AdminNotificationsScreen", "Usuario: ${it.uid} - ${it.name} - Admin: ${it.isAdmin}")
        }
    }

    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedRecipientId by remember { mutableStateOf<String?>(null) }
    var isGlobalNotification by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(NotificationType.REGULAR) }
    var showUserSelectionDialog by remember { mutableStateOf(false) }
    var showNotificationTypeDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val focusManager = LocalFocusManager.current

    LaunchedEffect(notificationState) {
        when (notificationState) {
            is NotificationViewModel.NotificationState.Success -> {
                val successMessage = (notificationState as NotificationViewModel.NotificationState.Success).message
                scope.launch {
                    snackbarHostState.showSnackbar(message = successMessage)
                }
                // Limpiar el formulario después de enviar con éxito
                title = ""
                message = ""
                selectedRecipientId = null
                isGlobalNotification = false
                selectedType = NotificationType.REGULAR
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

    val selectedUser = remember(selectedRecipientId, allUsers) {
        allUsers.find { it.uid == selectedRecipientId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enviar Notificaciones") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Selector de tipo de notificación (individual o global)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tipo de envío:", modifier = Modifier.weight(1f))

                FilterChip(
                    selected = !isGlobalNotification,
                    onClick = { isGlobalNotification = false },
                    label = { Text("Individual") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilterChip(
                    selected = isGlobalNotification,
                    onClick = { isGlobalNotification = true },
                    label = { Text("Global") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // Selector de usuario (si es notificación individual)
            if (!isGlobalNotification) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showUserSelectionDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedUser != null) {
                            Column {
                                Text(
                                    "Destinatario:",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    selectedUser.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            Text("Seleccionar destinatario")
                        }

                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Seleccionar usuario"
                        )
                    }
                }
            }

            // Selector de tipo de notificación
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showNotificationTypeDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Prioridad:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            when (selectedType) {
                                NotificationType.REGULAR -> "Normal"
                                NotificationType.ALERT -> "Alerta"
                                NotificationType.INFO -> "Informativa"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Icon(
                        when (selectedType) {
                            NotificationType.REGULAR -> Icons.Default.Notifications
                            NotificationType.ALERT -> Icons.Default.NotificationsActive
                            NotificationType.INFO -> Icons.Default.Info
                        },
                        contentDescription = "Tipo de notificación",
                        tint = when (selectedType) {
                            NotificationType.REGULAR -> MaterialTheme.colorScheme.primary
                            NotificationType.ALERT -> MaterialTheme.colorScheme.error
                            NotificationType.INFO -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
            }

            // Campos para el título y mensaje
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título de la notificación") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Mensaje") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                maxLines = 5
            )

            // Botón de envío
            Button(
                onClick = {
                    if (title.isBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("El título no puede estar vacío")
                        }
                        return@Button
                    }

                    if (message.isBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("El mensaje no puede estar vacío")
                        }
                        return@Button
                    }

                    if (!isGlobalNotification && selectedRecipientId == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Debes seleccionar un destinatario")
                        }
                        return@Button
                    }

                    // Enviar notificación
                    notificationViewModel.sendNotification(
                        title = title,
                        message = message,
                        recipientUid = if (isGlobalNotification) null else selectedRecipientId,
                        type = selectedType
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = notificationState !is NotificationViewModel.NotificationState.Loading
            ) {
                if (notificationState is NotificationViewModel.NotificationState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Enviar Notificación")
                }
            }
        }
    }

    // Dialog para seleccionar usuario
    if (showUserSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showUserSelectionDialog = false },
            title = { Text("Seleccionar destinatario") },
            text = {
                if (allUsers.filter { !it.isAdmin }.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No hay usuarios disponibles. Verifica los permisos de base de datos.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(allUsers.filter { !it.isAdmin }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedRecipientId == user.uid,
                                        onClick = {
                                            selectedRecipientId = user.uid
                                            showUserSelectionDialog = false
                                        }
                                    )
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedRecipientId == user.uid,
                                    onClick = {
                                        selectedRecipientId = user.uid
                                        showUserSelectionDialog = false
                                    }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp)
                                ) {
                                    Text(
                                        text = user.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = user.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserSelectionDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog para seleccionar tipo de notificación
    if (showNotificationTypeDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationTypeDialog = false },
            title = { Text("Seleccionar tipo de notificación") },
            text = {
                Column {
                    NotificationType.values().forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedType == type,
                                    onClick = {
                                        selectedType = type
                                        showNotificationTypeDialog = false
                                    }
                                )
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    showNotificationTypeDialog = false
                                }
                            )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    when (type) {
                                        NotificationType.REGULAR -> Icons.Default.Notifications
                                        NotificationType.ALERT -> Icons.Default.NotificationsActive
                                        NotificationType.INFO -> Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    tint = when (type) {
                                        NotificationType.REGULAR -> MaterialTheme.colorScheme.primary
                                        NotificationType.ALERT -> MaterialTheme.colorScheme.error
                                        NotificationType.INFO -> MaterialTheme.colorScheme.tertiary
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (type) {
                                        NotificationType.REGULAR -> "Normal"
                                        NotificationType.ALERT -> "Alerta"
                                        NotificationType.INFO -> "Informativa"
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationTypeDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}