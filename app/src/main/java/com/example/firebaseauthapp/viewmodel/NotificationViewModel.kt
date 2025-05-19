package com.example.firebaseauthapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.volley.DefaultRetryPolicy
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.firebaseauthapp.model.Notification
import com.example.firebaseauthapp.model.NotificationType
import com.example.firebaseauthapp.model.User
import com.example.firebaseauthapp.util.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.*

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "NotificationViewModel"
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val _notificationState = MutableStateFlow<NotificationState>(NotificationState.Initial)
    val notificationState: StateFlow<NotificationState> = _notificationState

    private val _userNotifications = MutableStateFlow<List<Notification>>(emptyList())
    val userNotifications: StateFlow<List<Notification>> = _userNotifications

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    private var notificationsListener: ValueEventListener? = null

    init {
        // Si es administrador, cargar la lista de usuarios
        auth.currentUser?.let { user ->
            viewModelScope.launch {
                try {
                    val userRef = database.child(Constants.USERS_REF).child(user.uid).get().await()

                    Log.d(TAG, "Verificando rol de administrador: ${userRef.value}")

                    // Verificar si el campo "admin" existe y obtener su valor
                    val isAdmin = userRef.child("admin").getValue(Boolean::class.java) ?: false
                    Log.d(TAG, "¿Es admin (directo)? $isAdmin")

                    val currentUser = userRef.getValue(User::class.java)
                    Log.d(TAG, "Usuario obtenido: $currentUser, isAdmin: ${currentUser?.isAdmin}")

                    if (isAdmin) {
                        Log.d(TAG, "Cargando lista de usuarios para administrador")
                        loadAllUsers()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al verificar rol", e)
                    _notificationState.value = NotificationState.Error(e.message ?: "Error al verificar rol")
                }
            }
        }

        // Cargar notificaciones del usuario actual
        loadUserNotifications()
    }

    private fun loadAllUsers() {
        database.child(Constants.USERS_REF).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = mutableListOf<User>()
                for (userSnapshot in snapshot.children) {
                    try {
                        // Intentar obtener usando getData
                        val user = userSnapshot.getValue(User::class.java)

                        if (user != null) {
                            users.add(user)
                        } else {
                            // Crear manualmente si falla
                            val uid = userSnapshot.child("uid").getValue(String::class.java) ?: userSnapshot.key ?: ""
                            val email = userSnapshot.child("email").getValue(String::class.java) ?: ""
                            val name = userSnapshot.child("name").getValue(String::class.java) ?: ""
                            val isAdmin = userSnapshot.child("admin").getValue(Boolean::class.java) ?: false
                            val fcmToken = userSnapshot.child("fcmToken").getValue(String::class.java) ?: ""

                            users.add(User(uid, email, name, isAdmin, fcmToken))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al convertir usuario: ${e.message}")
                    }
                }

                Log.d(TAG, "Usuarios cargados: ${users.size}")
                _allUsers.value = users
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error al cargar usuarios: ${error.message}")
                _notificationState.value = NotificationState.Error("No se pudieron cargar los usuarios. Verifica los permisos.")

                // Usar la solución alternativa si no podemos cargar los usuarios
                handleLoadUsersFallback()
            }
        })
    }

    private fun handleLoadUsersFallback() {
        // Si no podemos cargar usuarios desde Firebase, crearemos una lista simulada con el usuario actual
        val currentUserList = mutableListOf<User>()

        // Obtener el usuario actual
        auth.currentUser?.let { currentFirebaseUser ->
            val uid = currentFirebaseUser.uid
            val email = currentFirebaseUser.email ?: ""

            // Crear un usuario básico (no administrador) para mostrar
            val dummyUser = User(
                uid = uid,
                email = email,
                name = "Usuario Actual",
                isAdmin = false
            )

            currentUserList.add(dummyUser)
        }

        // Establecer la lista de usuarios
        _allUsers.value = currentUserList
    }

    fun loadUserNotifications() {
        val currentUser = auth.currentUser ?: return

        Log.d(TAG, "Intentando cargar notificaciones para usuario: ${currentUser.uid}")

        // Eliminar listener anterior si existe
        notificationsListener?.let { listener ->
            database.child(Constants.USER_NOTIFICATIONS_REF)
                .child(currentUser.uid)
                .removeEventListener(listener)
        }

        // Crear nuevo listener
        notificationsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Datos de notificaciones recibidos: ${snapshot.childrenCount} elementos")

                viewModelScope.launch {
                    try {
                        val notifications = mutableListOf<Notification>()
                        var unreadCount = 0

                        // Primero las notificaciones del usuario
                        for (notifSnapshot in snapshot.children) {
                            if (notifSnapshot.key != "read_globals") {
                                notifSnapshot.getValue(Notification::class.java)?.let { notification ->
                                    Log.d(TAG, "Notificación personal cargada: ${notification.id} - ${notification.title}")
                                    notifications.add(notification)
                                    if (!notification.read) {
                                        unreadCount++
                                    }
                                }
                            }
                        }

                        // Obtener IDs de notificaciones globales ya leídas
                        val readGlobalsSnapshot = snapshot.child("read_globals")
                        val readGlobalIds = mutableSetOf<String>()

                        for (readGlobalSnapshot in readGlobalsSnapshot.children) {
                            readGlobalIds.add(readGlobalSnapshot.key ?: "")
                        }

                        Log.d(TAG, "Notificaciones globales ya leídas: ${readGlobalIds.size}")

                        // Luego obtener notificaciones globales
                        val globalSnapshot = database.child(Constants.GLOBAL_NOTIFICATIONS_REF).get().await()

                        Log.d(TAG, "Notificaciones globales encontradas: ${globalSnapshot.childrenCount}")

                        for (globalNotifSnapshot in globalSnapshot.children) {
                            globalNotifSnapshot.getValue(Notification::class.java)?.let { notification ->
                                // Verificar si esta notificación global ya ha sido marcada como leída por este usuario
                                val isRead = readGlobalIds.contains(notification.id)

                                val notificationWithReadStatus = notification.copy(read = isRead)
                                Log.d(TAG, "Notificación global cargada: ${notification.id} - ${notification.title} - Leída: $isRead")
                                notifications.add(notificationWithReadStatus)

                                if (!isRead) {
                                    unreadCount++
                                }
                            }
                        }

                        // Ordenar por timestamp (más recientes primero)
                        notifications.sortByDescending {
                            when (val timestamp = it.timestamp) {
                                is Long -> timestamp
                                else -> 0L
                            }
                        }

                        Log.d(TAG, "Total notificaciones cargadas: ${notifications.size}, No leídas: $unreadCount")

                        _userNotifications.value = notifications
                        _unreadCount.value = unreadCount
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al cargar notificaciones", e)
                        _notificationState.value = NotificationState.Error(e.message ?: "Error al cargar notificaciones")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error al cargar notificaciones", error.toException())
                _notificationState.value = NotificationState.Error(error.message)
            }
        }

        // Agregar listener para notificaciones del usuario
        Log.d(TAG, "Agregando listener para notificaciones en: ${Constants.USER_NOTIFICATIONS_REF}/${currentUser.uid}")
        database.child(Constants.USER_NOTIFICATIONS_REF)
            .child(currentUser.uid)
            .addValueEventListener(notificationsListener!!)
    }

    fun sendNotification(
        title: String,
        message: String,
        recipientUid: String?,
        type: NotificationType = NotificationType.REGULAR
    ) {
        val currentUser = auth.currentUser ?: return
        _notificationState.value = NotificationState.Loading

        viewModelScope.launch {
            try {
                // Verificar si el usuario actual es administrador
                val userRef = database.child(Constants.USERS_REF).child(currentUser.uid).get().await()

                // Verificar si el campo "admin" existe y obtener su valor
                val isAdmin = userRef.child("admin").getValue(Boolean::class.java) ?: false
                Log.d(TAG, "¿Es admin (directo)? $isAdmin")

                // Para este caso, usamos el valor directo del campo admin en la base de datos
                if (!isAdmin) {
                    _notificationState.value = NotificationState.Error("Solo los administradores pueden enviar notificaciones")
                    return@launch
                }

                val notificationId = database.child(Constants.NOTIFICATIONS_REF).push().key
                    ?: throw Exception("No se pudo generar ID de notificación")

                // Obtener nombre del sender
                val senderName = userRef.child("name").getValue(String::class.java) ?: "Administrador"

                // Crear el mapa de datos de la notificación
                val notificationData = hashMapOf(
                    "id" to notificationId,
                    "title" to title,
                    "message" to message,
                    "senderUid" to currentUser.uid,
                    "senderName" to senderName,
                    "timestamp" to System.currentTimeMillis(),
                    "type" to type.name,
                    "read" to false
                )

                // Si es una notificación individual, incluir el recipientUid
                if (recipientUid != null) {
                    notificationData["recipientUid"] = recipientUid
                }

                // Guardar en la base de datos según el tipo de notificación
                if (recipientUid == null) {
                    // Notificación global
                    Log.d(TAG, "Enviando notificación global: $notificationId - $title")
                    database.child(Constants.GLOBAL_NOTIFICATIONS_REF)
                        .child(notificationId)
                        .setValue(notificationData)
                        .await()

                    // Enviar a todos los dispositivos
                    sendPushNotificationToAll(notificationId, title, message, type)
                } else {
                    // Notificación individual
                    Log.d(TAG, "Enviando notificación a usuario: $recipientUid - $notificationId - $title")
                    database.child(Constants.USER_NOTIFICATIONS_REF)
                        .child(recipientUid)
                        .child(notificationId)
                        .setValue(notificationData)
                        .await()

                    // Enviar al dispositivo del destinatario
                    sendPushNotificationToUser(notificationId, title, message, recipientUid, type)
                }

                _notificationState.value = NotificationState.Success("Notificación enviada correctamente")
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar notificación", e)
                _notificationState.value = NotificationState.Error(e.message ?: "Error al enviar notificación")
            }
        }
    }

// Solo se actualizan los métodos sendPushNotificationToUser y sendPushNotificationToAll

    private suspend fun sendPushNotificationToUser(
        notificationId: String,
        title: String,
        message: String,
        userId: String,
        type: NotificationType
    ) {
        try {
            // Obtener el token FCM del usuario
            val userSnapshot = database.child(Constants.USERS_REF).child(userId).get().await()
            val fcmToken = userSnapshot.child("fcmToken").getValue(String::class.java)

            if (fcmToken.isNullOrEmpty()) {
                Log.d(TAG, "No se puede enviar push: Token FCM no disponible para usuario $userId")
                return
            }

            Log.d(TAG, "Enviando push: ID=$notificationId, Título=$title, Token=${fcmToken.take(10)}...")

            // Crear el payload de la notificación
            val notification = mapOf(
                "title" to title,
                "body" to message,
                "sound" to "default",
                "badge" to "1",
                "click_action" to "OPEN_NOTIFICATION_ACTIVITY"
            )

            // Datos adicionales
            val data = mapOf(
                "id" to notificationId,
                "title" to title,
                "message" to message,
                "type" to type.name,
                "userId" to userId,
                "timestamp" to System.currentTimeMillis().toString()
            )

            // Mensaje completo (debe contener "notification" para mostrar en la bandeja)
            val fcmMessage = mapOf(
                "to" to fcmToken,
                "notification" to notification,
                "data" to data,
                "priority" to "high",
                "content_available" to true
            )

            // Enviar la notificación usando la API HTTP de FCM
            sendFcmMessage(fcmMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar notificación push", e)
        }
    }

    private suspend fun sendPushNotificationToAll(
        notificationId: String,
        title: String,
        message: String,
        type: NotificationType
    ) {
        try {
            // Obtener todos los tokens de FCM
            val usersSnapshot = database.child(Constants.USERS_REF).get().await()
            val fcmTokens = mutableListOf<String>()

            for (userSnapshot in usersSnapshot.children) {
                val fcmToken = userSnapshot.child("fcmToken").getValue(String::class.java)
                if (fcmToken != null && fcmToken.isNotEmpty()) {
                    fcmTokens.add(fcmToken)
                }
            }

            Log.d(TAG, "Enviando push global: ID=$notificationId, Título=$title, a ${fcmTokens.size} dispositivos")

            // Crear el payload de la notificación
            val notification = mapOf(
                "title" to title,
                "body" to message,
                "sound" to "default",
                "badge" to "1",
                "click_action" to "OPEN_NOTIFICATION_ACTIVITY"
            )

            // Datos adicionales
            val data = mapOf(
                "id" to notificationId,
                "title" to title,
                "message" to message,
                "type" to type.name,
                "timestamp" to System.currentTimeMillis().toString()
            )

            // Enviar a cada token (o usar multicast si son muchos)
            if (fcmTokens.size > 5) {
                // Usar multicast para muchos dispositivos
                val chunks = fcmTokens.chunked(500) // FCM permite máximo 500 receptores por request

                chunks.forEach { chunk ->
                    val fcmMessage = mapOf(
                        "registration_ids" to chunk, // Para múltiples dispositivos
                        "notification" to notification,
                        "data" to data,
                        "priority" to "high",
                        "content_available" to true
                    )
                    sendFcmMessage(fcmMessage)
                }
            } else {
                // Enviar individualmente si son pocos
                fcmTokens.forEach { token ->
                    val fcmMessage = mapOf(
                        "to" to token,
                        "notification" to notification,
                        "data" to data,
                        "priority" to "high",
                        "content_available" to true
                    )
                    sendFcmMessage(fcmMessage)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar notificación push global", e)
        }
    }

    // Método para enviar el mensaje FCM usando Volley - con mejor manejo de errores
    private fun sendFcmMessage(message: Map<String, Any>) {
        try {
            val jsonObject = JSONObject(message)

            Log.d(TAG, "Enviando mensaje FCM: $jsonObject")

            // Crear la solicitud
            val request = object : JsonObjectRequest(
                Method.POST, Constants.FCM_API_URL, jsonObject,
                { response ->
                    Log.d(TAG, "FCM Response: $response")

                    // Verificar si hay errores en la respuesta
                    if (response.has("failure") && response.getInt("failure") > 0) {
                        val results = response.optJSONArray("results")
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                val result = results.optJSONObject(i)
                                if (result != null && result.has("error")) {
                                    Log.e(TAG, "Error FCM en token #$i: ${result.getString("error")}")
                                }
                            }
                        }
                    }
                },
                { error ->
                    val errorMsg = when {
                        error.networkResponse == null -> "Error de red: Sin respuesta"
                        error.networkResponse.data != null -> "Error FCM: ${String(error.networkResponse.data)}"
                        else -> "Error FCM: ${error.message}"
                    }
                    Log.e(TAG, errorMsg)
                }
            ) {
                override fun getHeaders(): MutableMap<String, String> {
                    val headers = HashMap<String, String>()
                    headers["Content-Type"] = "application/json"
                    headers["Authorization"] = "key=${Constants.SERVER_KEY}"
                    return headers
                }
            }

            // Establecer timeout más largo
            request.retryPolicy = DefaultRetryPolicy(
                30000, // 30 segundos de timeout
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )

            // Añadir a la cola de solicitudes
            Volley.newRequestQueue(getApplication()).add(request)
        } catch (e: Exception) {
            Log.e(TAG, "Error al preparar mensaje FCM", e)
        }
    }
    fun markNotificationAsRead(notification: Notification) {
        val currentUser = auth.currentUser ?: return

        viewModelScope.launch {
            try {
                if (notification.recipientUid == null) {
                    // Notificación global - marcar como leída solo para este usuario
                    database.child(Constants.USER_NOTIFICATIONS_REF)
                        .child(currentUser.uid)
                        .child("read_globals")
                        .child(notification.id)
                        .setValue(true)
                        .await()
                } else {
                    // Notificación personal
                    database.child(Constants.USER_NOTIFICATIONS_REF)
                        .child(currentUser.uid)
                        .child(notification.id)
                        .child("read")
                        .setValue(true)
                        .await()
                }

                // No necesitamos recargar explícitamente las notificaciones
                // ya que el listener se activará automáticamente al cambiar los datos
                _notificationState.value = NotificationState.Success("Notificación marcada como leída")
            } catch (e: Exception) {
                Log.e(TAG, "Error al marcar como leída", e)
                _notificationState.value = NotificationState.Error(e.message ?: "Error al marcar como leída")
            }
        }
    }

    fun markAllNotificationsAsRead() {
        val currentUser = auth.currentUser ?: return

        viewModelScope.launch {
            try {
                val notifications = _userNotifications.value

                // Agrupar operaciones de escritura para realizarlas en lote
                val updates = mutableMapOf<String, Any>()

                // Marcar todas las notificaciones personales como leídas
                for (notification in notifications) {
                    if (notification.recipientUid != null) {
                        updates["${Constants.USER_NOTIFICATIONS_REF}/${currentUser.uid}/${notification.id}/read"] = true
                    } else {
                        // Marcar notificaciones globales como leídas
                        updates["${Constants.USER_NOTIFICATIONS_REF}/${currentUser.uid}/read_globals/${notification.id}"] = true
                    }
                }

                // Ejecutar actualizaciones en lote si hay alguna
                if (updates.isNotEmpty()) {
                    database.updateChildren(updates).await()
                }

                _notificationState.value = NotificationState.Success("Todas las notificaciones marcadas como leídas")
            } catch (e: Exception) {
                Log.e(TAG, "Error al marcar notificaciones", e)
                _notificationState.value = NotificationState.Error(e.message ?: "Error al marcar notificaciones")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Eliminar listeners al destruir el ViewModel
        notificationsListener?.let { listener ->
            auth.currentUser?.uid?.let { uid ->
                database.child(Constants.USER_NOTIFICATIONS_REF)
                    .child(uid)
                    .removeEventListener(listener)
            }
        }
    }

    sealed class NotificationState {
        object Initial : NotificationState()
        object Loading : NotificationState()
        data class Success(val message: String) : NotificationState()
        data class Error(val message: String) : NotificationState()
    }
}