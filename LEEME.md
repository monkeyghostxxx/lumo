# Lumo — videos en Android Auto

Reproductor minimalista que muestra los videos de tu teléfono en la pantalla de Android Auto.

- Lista de videos del teléfono (con miniaturas) o cualquier archivo con el ícono de carpeta.
- En el auto: video a pantalla completa, controles que aparecen al tocar y se ocultan solos, ±10 s, deslizar para adelantar/regresar, lista ☰ para elegir videos.
- Botones del volante funcionan (play/pausa).
- Se pausa sola si el auto se mueve (cuando Android Auto reporta la velocidad) y sigue al detenerte.

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
