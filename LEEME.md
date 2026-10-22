# Lumo — videos en Android Auto

Pantalla única y limpia en Android Auto para ver YouTube (y sitios de video sin DRM) en el auto.

- La página ocupa toda la pantalla; con el botón de pantalla completa de YouTube solo queda el video.
- Toca para usar la página, desliza para hacer scroll. Barra mínima: ← atrás, buscar (teclado del auto), inicio.
- Desde el teléfono: en YouTube toca **Compartir → Lumo**, o pega un enlace en la app.
- Si cambias a Maps y regresas, el video sigue donde estaba.
- Se pausa sola si el auto se mueve (cuando Android Auto reporta la velocidad) y sigue al detenerte.
- Netflix, Disney+, Prime, Max, etc. **no** funcionan: su DRM deja la pantalla en negro.

## 1. Obtener el APK (sin instalar nada)

1. Crea un repositorio nuevo en GitHub (puede ser privado).
2. Sube el contenido de esta carpeta (botón «Add file → Upload files», arrastra todo, incluida la carpeta `.github`).
   - Si tu computadora oculta la carpeta `.github` y no se sube, créala en GitHub con «Add file → Create new file», nombre `.github/workflows/build.yml`, y pega el contenido de ese archivo.
3. Ve a la pestaña **Actions** → «Compilar APK». Se ejecuta solo al subir; tarda ~4 min.
4. Al terminar, abre la ejecución y descarga **Lumo-apk** (un .zip con `app-release.apk`).

> Alternativa: abre la carpeta en Android Studio y usa *Build → Build APK(s)*.

## 2. Instalar en el teléfono

Pasa el APK al teléfono, ábrelo y permite «instalar apps desconocidas» para tu navegador o administrador de archivos.

## 3. Activar en Android Auto (una sola vez)

1. Ajustes del teléfono → **Android Auto**.
2. Baja hasta **Versión** y tócala unas 10 veces → acepta el modo desarrollador.
3. Menú **⋮ → Configuración para desarrolladores** → activa **Fuentes desconocidas**.
4. Abre Lumo en el teléfono y concede el acceso a videos.
5. Conecta el auto. Lumo aparece en el menú de apps (si no, en *Personalizar menú*).

### Si Lumo no aparece en el auto

Algunas versiones de Android Auto solo muestran apps instaladas «desde Play Store». Instálalo desde una computadora con ADB así:

```
adb install -i "com.android.vending" -r app-release.apk
```

## Notas

- Lumo usa la categoría de navegación de Android Auto para tener la pantalla completa; por eso no se puede publicar en Play Store, es para uso personal.
- Los toques directos sobre el video requieren Android Auto reciente; si tu unidad no los envía, usa los botones de la barra (▶ y ☰).
