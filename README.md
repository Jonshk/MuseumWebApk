# MuseumWebApk — WebView wrapper

APK que carga https://museum-frontend-490907340553.europe-west1.run.app/ en pantalla completa.

## Compilar
Abrir en Android Studio y Build > Build APK(s), o por terminal:

    ./gradlew assembleRelease

APK resultante: app/build/outputs/apk/release/app-release.apk
(firmada con debug key — cambia el signingConfig si la vas a distribuir).

## Ajustes rápidos
- URL: constante START_URL en MainActivity.kt
- Nombre de la app: res/values/strings.xml
- Icono: res/drawable/ic_launcher_fg.xml
- Botón atrás: ahora navega dentro del WebView y no sale de la app (kiosco).
