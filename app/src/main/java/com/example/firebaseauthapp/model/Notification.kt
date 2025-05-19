package com.example.firebaseauthapp.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName
import java.util.Date

@IgnoreExtraProperties
data class Notification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val recipientUid: String? = null,
    val timestamp: Long = System.currentTimeMillis(),

    @get:PropertyName("type")
    @set:PropertyName("type")
    var notificationType: String = NotificationType.REGULAR.name,

    @get:PropertyName("read")
    @set:PropertyName("read")
    var read: Boolean = false
) {
    // Constructor sin argumentos requerido por Firebase
    constructor() : this("", "", "", "", "", null, System.currentTimeMillis(), NotificationType.REGULAR.name, false)

    // Propiedad calculada que no se guarda en Firebase
    @get:Exclude
    val type: NotificationType
        get() = try {
            NotificationType.valueOf(notificationType)
        } catch (e: Exception) {
            NotificationType.REGULAR
        }

    // Método para crear una copia con un campo read diferente
    fun copy(read: Boolean): Notification {
        return Notification(
            id = this.id,
            title = this.title,
            message = this.message,
            senderUid = this.senderUid,
            senderName = this.senderName,
            recipientUid = this.recipientUid,
            timestamp = this.timestamp,
            notificationType = this.notificationType,
            read = read
        )
    }

    override fun toString(): String {
        return "Notification(id='$id', title='$title', message='$message', from='$senderName', read=$read)"
    }
}

enum class NotificationType {
    REGULAR,
    ALERT,
    INFO
}