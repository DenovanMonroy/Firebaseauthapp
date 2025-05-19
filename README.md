# Firebase Auth App - Sistema de Autenticación y Notificaciones


## Descripción

Firebase Auth App es una aplicación Android moderna que implementa un sistema completo de autenticación y notificaciones usando Firebase. La aplicación permite el registro y autenticación de usuarios, así como la gestión de notificaciones personalizadas y globales.

## Características principales

- **Autenticación de usuarios**
  - Registro con email y contraseña
  - Inicio de sesión
  - Roles de usuario (administrador y usuario regular)

- **Sistema de notificaciones**
  - Notificaciones en tiempo real
  - Notificaciones personalizadas por usuario
  - Notificaciones globales para todos los usuarios
  - Visualización en la bandeja del sistema
  - Historial de notificaciones en la aplicación

- **Panel de administración**
  - Envío de notificaciones a usuarios específicos
  - Envío de notificaciones globales
  - Gestión de usuarios

## Tecnologías utilizadas

- **Kotlin** - Lenguaje de programación principal
- **Jetpack Compose** - Framework UI moderno para Android
- **Firebase Auth** - Autenticación de usuarios
- **Firebase Realtime Database** - Almacenamiento de datos en tiempo real
- **Firebase Cloud Messaging (FCM)** - Sistema de notificaciones push
- **Coroutines y Flow** - Programación asíncrona y reactiva
- **MVVM Architecture** - Patrón de arquitectura Model-View-ViewModel
- **Material 3 Design** - Componentes y estilos de UI modernos

## Configuración del proyecto

### Prerrequisitos

- Android Studio Electric Eel o superior
- Kotlin 1.8.0 o superior
- JDK 11 o superior
- Una cuenta de Firebase
- Firebase CLI (opcional, para despliegue desde línea de comandos)

### Instalación

1. Clona este repositorio
   ```bash
   git clone https://github.com/DenovanMonroy/firebaseauthapp.git
   ```

2. Abre el proyecto en Android Studio

3. Conecta el proyecto a Firebase
   - Crea un nuevo proyecto en la [consola de Firebase](https://console.firebase.google.com/)
   - Añade una aplicación Android con el package name `com.example.firebaseauthapp`
   - Descarga el archivo `google-services.json` y colócalo en la carpeta `app/`
   - Habilita Authentication, Realtime Database y Firebase Cloud Messaging en la consola de Firebase

4. Configura Firebase Authentication
   - Habilita el proveedor de Email/Password en la consola de Firebase

5. Configura Realtime Database
   - Crea una base de datos en modo prueba o producción
   - Establece las reglas de seguridad adecuadas:
     ```json
     {
       "rules": {
         "users": {
           "$uid": {
             ".read": "$uid === auth.uid || root.child('users').child(auth.uid).child('admin').val() === true",
             ".write": "$uid === auth.uid || root.child('users').child(auth.uid).child('admin').val() === true"
           }
         },
         "notifications": {
           ".read": "auth != null",
           ".write": "root.child('users').child(auth.uid).child('admin').val() === true"
         },
         "user_notifications": {
           "$uid": {
             ".read": "$uid === auth.uid",
             ".write": "$uid === auth.uid || root.child('users').child(auth.uid).child('admin').val() === true"
           }
         },
         "global_notifications": {
           ".read": "auth != null",
           ".write": "root.child('users').child(auth.uid).child('admin').val() === true"
         }
       }
     }
     ```

6. Configura FCM
   - Obtén la clave del servidor desde la configuración del proyecto de Firebase
   - Actualiza la constante `SERVER_KEY` en `Constants.kt` con tu clave

### Personalización

Para personalizar la aplicación, puedes modificar:

- `Constants.kt` - Contraseña maestra para crear administradores y otras constantes
- `colors.xml` - Colores de la aplicación
- `strings.xml` - Textos y mensajes
- `themes.xml` - Temas y estilos

## Arquitectura

La aplicación sigue el patrón MVVM (Model-View-ViewModel):

```
app/
├─ model/              # Modelos de datos
├─ ui/                 # Componentes de UI (Compose)
│  ├─ screen/          # Pantallas principales
│  ├─ components/      # Componentes reutilizables
│  ├─ theme/           # Estilos y temas
├─ viewmodel/          # ViewModels para lógica de UI
├─ service/            # Servicios (Firebase Messaging, etc.)
├─ util/               # Utilidades y helpers
```

## Flujo de autenticación

1. **Registro**
   - El usuario introduce email, contraseña y nombre
   - Opcionalmente, puede marcar como administrador e introducir contraseña maestra
   - Los datos se guardan en Firebase Auth y Realtime Database

2. **Inicio de sesión**
   - El usuario introduce email y contraseña
   - Se verifica contra Firebase Auth
   - Se cargan los datos del usuario desde Realtime Database

3. **Cierre de sesión**
   - Se elimina el token FCM
   - Se cierra la sesión en Firebase Auth
   - Se redirige a la pantalla de login

## Sistema de notificaciones

1. **Envío de notificaciones**
   - Los administradores pueden enviar notificaciones a usuarios específicos o a todos
   - Las notificaciones se guardan en Realtime Database
   - Se envía una notificación push a través de FCM

2. **Recepción de notificaciones**
   - Las notificaciones se reciben a través de FCM
   - Se muestran en la bandeja del sistema
   - Se almacenan en el historial dentro de la aplicación

3. **Visualización de notificaciones**
   - Los usuarios pueden ver su historial de notificaciones
   - Pueden marcar las notificaciones como leídas

## Permisos de la aplicación

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
```

## Contribuir al proyecto

1. Haz un fork del repositorio
2. Crea una nueva rama (`git checkout -b feature/nueva-caracteristica`)
3. Haz commit de tus cambios (`git commit -m 'Añadir nueva característica'`)
4. Haz push a la rama (`git push origin feature/nueva-caracteristica`)
5. Abre un Pull Request

## Licencia

Este proyecto está licenciado bajo la Licencia MIT - ver archivo [LICENSE](LICENSE) para detalles.

## Autor

**Denovan Monroy** - [GitHub](https://github.com/DenovanMonroy)

## Capturas de pantalla
![image](https://github.com/user-attachments/assets/36501646-bc3f-453e-b0c7-36e1d84fb30d)
![image](https://github.com/user-attachments/assets/f4e7a5f9-c8fb-4a84-92df-51217f08c5ad)
![image](https://github.com/user-attachments/assets/e6a8a085-facc-43fd-a672-cf9b7bd2f2fb)
![image](https://github.com/user-attachments/assets/df08cdd0-32d7-404d-b492-4ee321a1665a)
![image](https://github.com/user-attachments/assets/e3d817ee-9d83-4c6e-bb94-9aed6b1c8c0c)
![image](https://github.com/user-attachments/assets/30e75c7e-6378-4df2-a0c8-4c97b8f071bb)
![image](https://github.com/user-attachments/assets/0043e0d3-a920-440e-bf38-12be1fa43405)


Última actualización: 19 de mayo de 2025
