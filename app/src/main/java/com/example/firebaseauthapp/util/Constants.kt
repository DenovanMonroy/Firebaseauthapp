package com.example.firebaseauthapp.util

object Constants {
    const val MASTER_PASSWORD = "Admin123!" // Contraseña maestra para crear administradores
    const val USERS_REF = "users" // Referencia para la tabla de usuarios en Realtime Database
    const val NOTIFICATIONS_REF = "notifications" // Referencia para la tabla de notificaciones
    const val USER_NOTIFICATIONS_REF = "user_notifications" // Referencia para las notificaciones de cada usuario
    const val GLOBAL_NOTIFICATIONS_REF = "global_notifications" // Referencia para notificaciones globales

    // FCM
    const val FCM_API_URL = "https://fcm.googleapis.com/fcm/send"
    const val SERVER_KEY = "AIzaSyAtH2y7uYU9_RxoJJ-uV3Zkjolkc-Ht6Aw" // Reemplazar con tu clave de servidor de FCM
}

