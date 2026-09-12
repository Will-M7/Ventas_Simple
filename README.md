# Sistematienda 1.0.0

Aplicación local para una tienda pequeña. Usa Node.js + Express + SQLite como servidor y clientes Kotlin/Compose para Android y Desktop.

## Requisitos

- Node.js 20 o superior
- JDK configurado para Gradle/Android
- No requiere Docker, MySQL, nube ni servicios externos

## Backend

```bash
cd backend
npm install
cp .env.example .env
# Edita .env y define un JWT_SECRET largo y local
npm run build
npm start
```

El servidor escucha en `0.0.0.0:3000`. La base se crea automáticamente en `backend/data/tienda.db` con WAL. Health: `GET /health`.

Comandos operativos:

```bash
npm run db:reset       # elimina datos y recrea usuarios iniciales
npm run db:seed-demo   # agrega tres categorías y productos demo
npm run db:backup      # crea una copia segura en backend/backups/
```

Usuarios iniciales: `admin / admin123` y `vendedor1 / 123`. Cámbialos en una instalación real.

## Clientes

En Android o Desktop se configura el host/IP del equipo servidor desde Configuración. La API usada es `http://<host>:3000/api/` y las imágenes `http://<host>:3000/uploads/products/`.

Android:

```bash
cd mobile
./gradlew clean :app:androidApp:assembleDebug --no-configuration-cache
```

APK: `mobile/app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

Desktop Linux:

```bash
./gradlew :app:desktopApp:compileKotlin --no-configuration-cache
./gradlew :app:desktopApp:packageDistributionForCurrentOS --no-configuration-cache
```

Windows (desde Windows):

```bat
gradlew.bat :app:desktopApp:packageMsi --no-configuration-cache
```

## Roles

`admin` administra usuarios, categorías, productos, inventario, ventas y reportes globales. `vendedor` vende, consulta productos, registra mercadería y consulta únicamente sus ventas/reportes.

## Estructura

- `backend/src`: API, autenticación, servicios y SQLite.
- `backend/uploads/products`: imágenes opcionales, no almacenadas en la base.
- `mobile/app/androidApp`: cliente Android, Retrofit, scanner ML Kit y UI.
- `mobile/app/desktopApp`: cliente Desktop con lector USB tipo teclado.
- `mobile/app/shared`: componentes base multiplataforma existentes.

Los datos SQLite, imágenes, backups, `.env`, builds y dependencias no se versionan.
