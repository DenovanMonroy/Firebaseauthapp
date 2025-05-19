package com.example.firebaseauthapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.firebaseauthapp.MainActivity
import com.example.firebaseauthapp.R
import com.example.firebaseauthapp.util.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val TAG = "FCMService"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Logging detallado
        Log.d(TAG, "========== INICIO DE NOTIFICACIÓN RECIBIDA ==========")
        Log.d(TAG, "De: ${remoteMessage.from}")
        Log.d(TAG, "ID: ${remoteMessage.messageId}")
        Log.d(TAG, "Tipo: ${remoteMessage.messageType}")
        Log.d(TAG, "TTL: ${remoteMessage.ttl}")

        // Log notificación
        if (remoteMessage.notification != null) {
            Log.d(TAG, "Notificación: Título=${remoteMessage.notification?.title}, Cuerpo=${remoteMessage.notification?.body}")
        } else {
            Log.d(TAG, "Notificación: NULA")
        }

        // Log datos
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Datos:")
            remoteMessage.data.forEach { (key, value) ->
                Log.d(TAG, "  $key: $value")
            }
        } else {
            Log.d(TAG, "Datos: VACÍO")
        }
        Log.d(TAG, "========== FIN DE NOTIFICACIÓN RECIBIDA ==========")

        // Siempre crear una notificación del sistema, independientemente si la app está en primer plano
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Nueva notificación"
        val message = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: "Has recibido una nueva notificación"

        sendNotification(title, message)
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Nuevo token FCM: $token")

        // Guardar el nuevo token en Firebase Database
        saveTokenToDatabase(token)
    }

    private fun saveTokenToDatabase(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        FirebaseDatabase.getInstance().reference
            .child(Constants.USERS_REF)
            .child(currentUser.uid)
            .child("fcmToken")
            .setValue(token)
            .addOnSuccessListener {
                Log.d(TAG, "Token guardado correctamente")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar token: ${e.message}")
            }
    }

    private fun sendNotification(title: String, messageBody: String) {
        // Intent para abrir la app cuando se pulsa la notificación
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Opcionalmente, agrega datos para abrir una pantalla específica
            putExtra("navigateTo", "notifications")
        }

        // Asegúrate de usar un requestCode único para cada notificación
        val notificationId = System.currentTimeMillis().toInt()

        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val channelId = "fcm_default_channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Construir la notificación
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Alta prioridad para Android 7.1 y menor
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible en pantalla de bloqueo

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Configurar el canal de notificación para Android Oreo y superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Canal de notificaciones",
                NotificationManager.IMPORTANCE_HIGH // Usar importancia alta
            ).apply {
                description = "Notificaciones de la aplicación"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Mostrar la notificación con un ID único
        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d(TAG, "Notificación mostrada con ID: $notificationId")
    }
}