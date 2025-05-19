package com.example.firebaseauthapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firebaseauthapp.model.User
import com.example.firebaseauthapp.util.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val TAG = "AuthViewModel"
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val messaging = FirebaseMessaging.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    init {
        // Verificar si el usuario ya está autenticado al iniciar la app
        auth.currentUser?.let { firebaseUser ->
            viewModelScope.launch {
                try {
                    _authState.value = AuthState.Loading
                    val userSnapshot = database.child(Constants.USERS_REF)
                        .child(firebaseUser.uid)
                        .get()
                        .await()

                    Log.d(TAG, "Datos del usuario al iniciar: ${userSnapshot.value}")

                    // Comprobar específicamente si el campo admin existe
                    val isAdmin = userSnapshot.child("admin").getValue(Boolean::class.java) ?: false
                    Log.d(TAG, "¿Es admin al iniciar? $isAdmin")

                    // Intentar obtener el usuario completo
                    val user = userSnapshot.getValue(User::class.java)

                    if (user != null) {
                        Log.d(TAG, "Usuario cargado en init: $user")
                        _currentUser.value = user

                        // Actualizar el token FCM si es necesario
                        updateFcmToken(firebaseUser.uid)

                        _authState.value = AuthState.Authenticated
                    } else {
                        // Si no se pudo convertir automáticamente, creamos manualmente el objeto User
                        val uid = userSnapshot.child("uid").getValue(String::class.java) ?: firebaseUser.uid
                        val email = userSnapshot.child("email").getValue(String::class.java) ?: firebaseUser.email ?: ""
                        val name = userSnapshot.child("name").getValue(String::class.java) ?: ""
                        val fcmToken = userSnapshot.child("fcmToken").getValue(String::class.java) ?: ""

                        val manualUser = User(uid, email, name, isAdmin, fcmToken)
                        Log.d(TAG, "Usuario creado manualmente: $manualUser")

                        _currentUser.value = manualUser
                        _authState.value = AuthState.Authenticated
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al cargar usuario en init", e)
                    _authState.value = AuthState.Error(e.message ?: "Error al cargar los datos del usuario")
                }
            }
        }
    }

    fun registerUser(email: String, password: String, name: String, isAdmin: Boolean, masterPassword: String?) {
        if (isAdmin && masterPassword != Constants.MASTER_PASSWORD) {
            _authState.value = AuthState.Error("Contraseña maestra incorrecta")
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                // Crear usuario en Firebase Auth
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val userId = authResult.user?.uid ?: throw Exception("Error al crear usuario")

                // Obtener token FCM
                val fcmToken = try {
                    messaging.token.await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error al obtener token FCM", e)
                    ""
                }

                // Crear objeto de usuario para guardar en la base de datos
                val newUser = User(userId, email, name, isAdmin, fcmToken)

                // Log para depuración
                Log.d(TAG, "Registrando usuario: $newUser con isAdmin=$isAdmin")

                // Crear un mapa para guardar en Firebase (para asegurar que el campo admin se guarde correctamente)
                val userMap = hashMapOf(
                    "uid" to userId,
                    "email" to email,
                    "name" to name,
                    "admin" to isAdmin,
                    "fcmToken" to fcmToken
                )

                // Guardar información en Realtime Database usando el mapa
                database.child(Constants.USERS_REF).child(userId).setValue(userMap).await()

                _currentUser.value = newUser
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                Log.e(TAG, "Error al registrar usuario", e)
                _authState.value = AuthState.Error(e.message ?: "Error durante el registro")
            }
        }
    }

    fun loginUser(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                // Autenticar usuario con Firebase Auth
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val userId = authResult.user?.uid ?: throw Exception("Error al iniciar sesión")

                // Obtener datos del usuario desde Realtime Database
                val userSnapshot = database.child(Constants.USERS_REF).child(userId).get().await()

                // Log para depuración
                Log.d(TAG, "Datos del usuario: ${userSnapshot.value}")

                // Verificar si el campo "admin" existe y obtener su valor
                val isAdmin = userSnapshot.child("admin").getValue(Boolean::class.java) ?: false
                Log.d(TAG, "¿Es admin? $isAdmin")

                // Intentar obtener el usuario completo
                val user = userSnapshot.getValue(User::class.java)

                if (user != null) {
                    Log.d(TAG, "Usuario cargado: $user, isAdmin: ${user.isAdmin}")
                    _currentUser.value = user
                } else {
                    // Si no se pudo convertir automáticamente, creamos manualmente el objeto User
                    val email = userSnapshot.child("email").getValue(String::class.java) ?: ""
                    val name = userSnapshot.child("name").getValue(String::class.java) ?: ""
                    val fcmToken = userSnapshot.child("fcmToken").getValue(String::class.java) ?: ""

                    val manualUser = User(userId, email, name, isAdmin, fcmToken)
                    Log.d(TAG, "Usuario creado manualmente: $manualUser")

                    _currentUser.value = manualUser
                }

                // Actualizar el token FCM
                updateFcmToken(userId)

                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                Log.e(TAG, "Error en login", e)
                _authState.value = AuthState.Error(e.message ?: "Error durante el inicio de sesión")
            }
        }
    }

    private suspend fun updateFcmToken(userId: String) {
        try {
            val token = messaging.token.await()
            database.child(Constants.USERS_REF).child(userId).child("fcmToken").setValue(token).await()

            // Actualizar el objeto de usuario actual con el nuevo token
            _currentUser.value = _currentUser.value?.copy(fcmToken = token)
            Log.d(TAG, "Token FCM actualizado: ${token.take(10)}...")

        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar token FCM", e)
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading

                // Eliminar token FCM al cerrar sesión
                auth.currentUser?.uid?.let { userId ->
                    try {
                        database.child(Constants.USERS_REF).child(userId).child("fcmToken").setValue("").await()
                        Log.d(TAG, "Token FCM eliminado correctamente")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al limpiar token FCM", e)
                    }
                }

                // Cerrar sesión en Firebase Auth
                auth.signOut()
                Log.d(TAG, "Usuario desconectado correctamente")

                // Limpiar datos y actualizar estado
                _currentUser.value = null
                _authState.value = AuthState.SignedOut
            } catch (e: Exception) {
                Log.e(TAG, "Error al cerrar sesión", e)
                _authState.value = AuthState.Error(e.message ?: "Error al cerrar sesión")
            }
        }
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    // Estados para la autenticación
    sealed class AuthState {
        object Initial : AuthState()
        object Loading : AuthState()
        object Authenticated : AuthState()
        object SignedOut : AuthState()
        data class Error(val message: String) : AuthState()
    }
}