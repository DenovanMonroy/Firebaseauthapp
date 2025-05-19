package com.example.firebaseauthapp.model

import com.google.firebase.database.PropertyName

data class User(
    val uid: String = "",
    val email: String = "",
    val name: String = "",

    @get:PropertyName("admin")
    @set:PropertyName("admin")
    var isAdmin: Boolean = false,

    val fcmToken: String = ""
) {
    // Constructor vacío requerido para Firebase
    constructor() : this("", "", "", false, "")

    override fun toString(): String {
        return "User(uid='$uid', email='$email', name='$name', isAdmin=$isAdmin, fcmToken='${fcmToken.take(10)}...')"
    }
}