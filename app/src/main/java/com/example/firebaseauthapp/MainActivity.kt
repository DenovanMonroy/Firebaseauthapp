package com.example.firebaseauthapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.firebaseauthapp.ui.screen.*
import com.example.firebaseauthapp.ui.theme.FirebaseAuthAppTheme
import com.example.firebaseauthapp.util.NotificationHelper
import com.example.firebaseauthapp.viewmodel.AuthViewModel
import com.example.firebaseauthapp.viewmodel.NotificationViewModel
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private lateinit var notificationHelper: NotificationHelper

    // Permiso para notificaciones en Android 13+
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Permiso de notificaciones concedido")
        } else {
            Log.d(TAG, "Permiso de notificaciones denegado")
        }
    }

    private fun askNotificationPermission() {
        // Verificar si necesitamos solicitar permiso (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) {
                // Permiso ya concedido
            } else {
                // Solicitar permiso
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar NotificationHelper
        notificationHelper = NotificationHelper(this)

        // Solicitar permiso de notificaciones
        askNotificationPermission()

        // Solicitar token FCM
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e(TAG, "Error al obtener el token FCM: ${task.exception}")
                return@addOnCompleteListener
            }

            // Obtener el token FCM
            val token = task.result
            Log.d(TAG, "FCM Token: $token")
        }

        setContent {
            FirebaseAuthAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel = viewModel()
                    val notificationViewModel: NotificationViewModel = viewModel()

                    val authState by authViewModel.authState.collectAsState()
                    val currentUser by authViewModel.currentUser.collectAsState()

                    // Observar notificaciones y mostrar las no leídas
                    LaunchedEffect(Unit) {
                        observeNotifications(notificationViewModel)
                    }

                    // Observar el estado de autenticación para navegación automática
                    LaunchedEffect(authState) {
                        when (authState) {
                            is AuthViewModel.AuthState.SignedOut -> {
                                Log.d(TAG, "Usuario desconectado, navegando al login")
                                navController.navigate("login") {
                                    // Limpiar el back stack para evitar volver a pantallas anteriores
                                    popUpTo(0) { inclusive = true }
                                    // Reiniciar la pila de navegación
                                    launchSingleTop = true
                                }
                            }
                            else -> { /* No hacer nada para otros estados */ }
                        }
                    }

                    // Verificar si hay un destino específico en el intent
                    val navigateTo = intent.getStringExtra("navigateTo")

                    // Comprobar si hay que navegar a una pantalla específica desde la notificación
                    LaunchedEffect(navigateTo) {
                        navigateTo?.let {
                            if (it == "notifications" && authViewModel.isUserLoggedIn()) {
                                navController.navigate("notifications")
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "login"
                    ) {
                        composable("login") {
                            LoginScreen(
                                onNavigateToRegister = { navController.navigate("register") },
                                authViewModel = authViewModel,
                                onLoginSuccess = {
                                    if (currentUser?.isAdmin == true) {
                                        navController.navigate("admin_home") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("user_home") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable("register") {
                            RegisterScreen(
                                onNavigateToLogin = { navController.navigate("login") },
                                authViewModel = authViewModel,
                                onRegisterSuccess = {
                                    if (currentUser?.isAdmin == true) {
                                        navController.navigate("admin_home") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("user_home") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable("user_home") {
                            LaunchedEffect(Unit) {
                                // Verificar si el usuario sigue autenticado
                                if (authState !is AuthViewModel.AuthState.Authenticated) {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                    return@LaunchedEffect
                                }

                                // Forzar recarga de notificaciones al entrar a la pantalla principal
                                Log.d(TAG, "Cargando notificaciones en pantalla principal")
                                notificationViewModel.loadUserNotifications()
                            }

                            UserHomeScreen(
                                authViewModel = authViewModel,
                                notificationViewModel = notificationViewModel,
                                onLogout = {
                                    // Ahora solo llamamos a logout()
                                    // La navegación se maneja en el LaunchedEffect
                                    authViewModel.logout()
                                },
                                onNavigateToNotifications = {
                                    navController.navigate("notifications")
                                }
                            )
                        }

                        composable("admin_home") {
                            LaunchedEffect(Unit) {
                                // Verificar si el usuario sigue autenticado y es admin
                                if (authState !is AuthViewModel.AuthState.Authenticated || currentUser?.isAdmin != true) {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                    return@LaunchedEffect
                                }
                            }

                            AdminHomeScreen(
                                authViewModel = authViewModel,
                                notificationViewModel = notificationViewModel,
                                onLogout = {
                                    // Solo llamamos a logout()
                                    // La navegación se maneja en el LaunchedEffect
                                    authViewModel.logout()
                                },
                                onNavigateToSendNotifications = {
                                    Log.d(TAG, "Navegando a admin_notifications")
                                    navController.navigate("admin_notifications")
                                },
                                onNavigateToViewNotifications = {
                                    navController.navigate("notifications")
                                }
                            )
                        }

                        composable("notifications") {
                            LaunchedEffect(Unit) {
                                // Verificar si el usuario sigue autenticado
                                if (authState !is AuthViewModel.AuthState.Authenticated) {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                    return@LaunchedEffect
                                }

                                // Forzar recarga de notificaciones al entrar a la pantalla de notificaciones
                                Log.d(TAG, "Cargando notificaciones en pantalla de notificaciones")
                                notificationViewModel.loadUserNotifications()
                            }

                            NotificationsScreen(
                                notificationViewModel = notificationViewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable("admin_notifications") {
                            LaunchedEffect(Unit) {
                                // Verificar si el usuario sigue autenticado y es admin
                                if (authState !is AuthViewModel.AuthState.Authenticated || currentUser?.isAdmin != true) {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                    return@LaunchedEffect
                                }
                            }

                            Log.d(TAG, "Mostrando AdminNotificationsScreen")
                            AdminNotificationsScreen(
                                notificationViewModel = notificationViewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observeNotifications(notificationViewModel: NotificationViewModel) {
        // Observar cambios en las notificaciones y mostrar las no leídas
        notificationViewModel.userNotifications
            .onEach { notifications ->
                Log.d(TAG, "Notificaciones actualizadas: ${notifications.size}")

                // Filtrar solo las notificaciones no leídas
                val unreadNotifications = notifications.filter { !it.read }

                if (unreadNotifications.isNotEmpty()) {
                    Log.d(TAG, "Mostrando ${unreadNotifications.size} notificaciones no leídas")
                    notificationHelper.showNotifications(unreadNotifications)
                }
            }
            .launchIn(CoroutineScope(Dispatchers.Main))
    }

    override fun onResume() {
        super.onResume()
        // No necesitamos hacer nada extra aquí, ya que las notificaciones se cargan
        // automáticamente cuando se navega a la pantalla correspondiente
    }
}