# Sistematienda — desarrollo v1.1

Cliente Android y cliente Desktop para una tienda, con API Node/Express y SQLite. La referencia estable es `revision-final` / `c1b3142`; las correcciones están en `v1.1-desarrollo`. Este documento no significa que se hayan desplegado en producción.

## Requisitos

- Node.js **22 o superior**, npm y las dependencias nativas de better-sqlite3 y Sharp disponibles para el sistema.
- JDK 21, Android SDK configurado y Gradle wrapper del repositorio para Android.
- SQLite local en disco persistente. No se requiere MySQL ni Docker.

No copiar `node_modules` entre versiones/arquitecturas de Node. Tras instalar, comprobar el addon con una base `:memory:` antes de iniciar el servicio.

## Arquitectura real

- `backend/src/config`: apertura explícita de SQLite, baseline y migraciones versionadas.
- `backend/src/routes`, `controllers`, `services`: HTTP, permisos, validación y operaciones transaccionales.
- `backend/src/operations`: backup integral y verificación.
- `mobile/app/androidApp`: Compose, Retrofit/OkHttp, scanner CameraX/ML Kit y carrito persistente por servidor/usuario.
- `mobile/app/desktopApp`: cliente Compose JVM, Java HttpClient, IO fuera del hilo UI y operaciones de venta recuperables.
- `mobile/app/shared`, `core`, `iosApp`, `webApp`, `server`: plantilla KMP/Compose/Ktor; no implementan una tienda multiplataforma compartida. Se conservan.
- `deploy/systemd`: ejemplo de servicio para Ubuntu. La configuración efectiva del servidor no fue inspeccionada.

**Dockerfile, docker-compose y schema.sql son LEGACY e incompatibles con SQLite actual. No son un despliegue soportado.** El esquema vigente está en `src/config/schema.ts` y sus cambios en `migrations.ts`.

## Desarrollo en un entorno separado

Desde `backend`, instalar con `npm ci`. Crear `.env` desde `.env.example` únicamente si no existe; no sobrescribir configuraciones existentes. El ejemplo usa `data-dev`, `uploads-dev` y `backups-dev` para separar desarrollo de los datos reales.

Configurar un `JWT_SECRET` aleatorio de al menos 32 caracteres (por ejemplo, generado con `openssl rand -hex 32`) y las credenciales de bootstrap para una base nueva. No guardar secretos en Git, terminales compartidas o capturas.

```bash
npm ci
npm run build
npm run test:unit
npm run dev
```

`test:unit` **no utiliza DATA_DIR ni BASE_URL para sus fixtures**: crea SQLite en memoria, archivos temporales y sus propios servidores efímeros en loopback. No iniciar el backend real para ejecutar las pruebas. El entorno debe permitir sockets locales para las pruebas HTTP. Los antiguos entry points de tests delegan ahora a esta suite segura.

No ejecutar reset ni seed sobre datos reales. Reset exige `ALLOW_DESTRUCTIVE_DB_RESET=true` y rechaza `NODE_ENV=production`; el seed demo exige `ALLOW_DEMO_SEED=true` y también rechaza producción. No son procedimientos de actualización.

## Variables de entorno

| Variable | Uso |
|---|---|
| `NODE_ENV` | `development` o `production`; determina las guardas operativas |
| `PORT` | Puerto TCP; predeterminado 3000 |
| `JWT_SECRET` | Secreto privado >=32 caracteres; mantenerlo protegido |
| `DATA_DIR` | Directorio que contiene `tienda.db`; en producción usar la ruta absoluta existente |
| `UPLOAD_DIR` | Directorio de imágenes; en producción conservar el uploads existente |
| `BUSINESS_TIME_ZONE` | Zona IANA; predeterminada `America/Lima` |
| `BOOTSTRAP_ADMIN_USERNAME` | Nombre explícito del administrador inicial, solo si no existe ningún usuario |
| `BOOTSTRAP_ADMIN_PASSWORD` | Contraseña inicial >=12 caracteres y <=72 bytes, solo para una base sin usuarios |
| `BOOTSTRAP_ADMIN_NAME` | Nombre del administrador inicial; por defecto Administrador |
| `BACKUP_DIR` | Destino de backups completos, distinto del directorio de datos y fuera de uploads |
| `BACKUP_RETENTION_COUNT` | Número de backups completos verificados a conservar; predeterminado 14, mínimo 1 |

El arranque no crea `admin/admin123` ni `vendedor1/123`. Una base existente conserva sus usuarios y hashes: el operador debe cambiar las contraseñas iniciales débiles que todavía existan. Renombrar un usuario no lo recrea al reiniciar. Nunca se imprime la contraseña de bootstrap.

## Migraciones

Se conserva WAL, `foreign_keys=ON` y `busy_timeout=5000`. Las migraciones se registran en `schema_migrations`, se ejecutan en transacciones y no borran datos:

1. `baseline_sqlite`: esquema SQLite existente, mediante creación si falta.
2. `sessions_and_sale_operations`: `users.session_version`, `sales.operation_id`, `sales.operation_hash` e índice único de operación.
3. `sale_items_sale_index`: índice `sale_items(venta_id)`.

Al iniciar se aplican únicamente estas migraciones no destructivas. Una versión desconocida o una migración fallida impide continuar; no se intenta reparar eliminando tablas. Repetir sobre una base correctamente migrada no vuelve a aplicar DDL.

Para ensayar o aplicar migraciones explícitas en una **copia** ya existente:

```bash
npm run build
npm run db:migrate -- /ruta/explicita/a/copia-de-datos
```

El comando exige un directorio explícito que ya contenga `tienda.db`. No utilizar reset para migrar.

## Ventas y compatibilidad

`POST /api/ventas` exige `operation_id` UUID y `items`. El cliente genera un ID por operación y conserva el ID y payload mientras el resultado sea incierto. Un reintento devuelve la misma venta: no descuenta stock ni agrega detalles/movimientos otra vez. Reutilizar un ID con otro usuario o cantidades devuelve 409.

Las líneas repetidas se agrupan antes de validar; los descuentos deben modificar exactamente una fila. Se mantienen una transacción y el precio calculado por el servidor. Dinero: precios con hasta dos decimales, operaciones en centavos enteros y almacenamiento REAL compatible.

**Los clientes anteriores que no envían operation_id deben actualizarse junto al backend.** Las ventas históricas siguen siendo válidas con operación nula. Los JWT anteriores sin versión de sesión requerirán iniciar sesión nuevamente. Los cambios de password, rol o estado revocan tokens previos. No se puede desactivar o degradar al último administrador activo.

La idempotencia es de ventas; un ajuste manual de inventario con respuesta perdida debe verificarse antes de repetirse. No se añadió operación offline.

## Clientes y configuración del servidor

Android y Desktop aceptan:

- `192.168.1.22` → `http://192.168.1.22:3000/`.
- `http://192.168.1.22:8080/`.
- `https://tienda.ejemplo.com/`.

Se configuran URLs base; el cliente añade `api/`, `health` y `uploads/`. Se rechazan credenciales embebidas, query y fragmentos. Probar conexión no guarda una configuración fallida; guardarla sin conexión requiere una acción explícita. Cambiar servidor invalida la sesión anterior.

HTTPS queda soportado en los clientes, **no habilitado en el servidor por estos cambios**. HTTP conserva compatibilidad LAN pero transmite credenciales sin cifrado. Se debe decidir y configurar TLS/aislamiento antes de exponerlo fuera de una red confiable. No se desactiva la verificación de certificados.

Android conserva carrito/operación en almacenamiento privado por servidor y usuario. El ID y el payload se escriben antes del envío. Un resultado incierto bloquea editar el carrito y permite reintentar la misma operación. Un borrador ilegible se presenta como error; no se descarta silenciosamente. Se excluyen sesión y borradores de backups/transferencia del sistema y se deshabilita `allowBackup`. No se añadió una librería de cifrado obsoleta: se usa el sandbox del sistema y exclusión de copias. No exportar JWT ni borrar borradores inciertos para solucionar un error.

Los formularios de alta preservan campos, URI y producto ya creado mediante estado guardable; los permisos persistentes del picker se toman cuando el proveedor lo permite. Esto es una mejora incremental: falta validación física completa de recreación y terminación durante un alta y no se promete idempotencia de altas de producto.

## Imágenes

Se mantienen 5 MiB. Backend admite JPEG/PNG/WebP estáticos: firma, MIME consistente, decodificación y normalización con Sharp, máximo 40 megapíxeles para acotar memoria. No acepta extensiones arbitrarias y elimina EXIF al normalizar. Los nombres son UUID con extensión derivada del formato. Se limita la recepción a ocho peticiones de producto simultáneas y la decodificación a dos imágenes, incluso ante desconexión del cliente; un exceso temporal devuelve 429 para reintentar.

Se conserva la imagen anterior hasta guardar la nueva referencia; solo se borra cuando ya no está referenciada. Los fallos controlados limpian el archivo nuevo. Un apagado forzado entre filesystem y SQLite puede dejar un huérfano: no existe una transacción distribuida entre ambos. No borrar uploads manualmente; ante un incidente revisar referencias sobre una copia y conservar backup antes de limpiar.

Los uploads se sirven públicamente por URL como imágenes, con `nosniff`; no son documentos privados. Dos fotografías operativas antiguas continúan versionadas para no borrar originales ni reescribir historial durante esta fase; no se consideran fixtures. Conservan EXIF de cámara; no se afirma que tengan GPS. La exclusión Git impide añadir nuevas imágenes operativas; no desplegar uploads a partir del contenido del repositorio.

## Scanner validado

Se conserva íntegro el comportamiento de lectura de la versión estable. El ROI **prioriza, no restringe estrictamente**: un candidato válido único puede aceptarse fuera del marco, con tamaño pequeño o sin transformación geométrica. El checksum valida el código, no la intención del operador. Probar físicamente con etiquetas cercanas y varios productos antes de decidir cambiar ese fallback; no exigir ROI estricto sin esas pruebas. En esta fase solo cambió logging: categorías acotadas, máximo 128 claves y detalle solo en debug.

## Reportes

Los timestamps continúan en UTC. Los períodos se convierten a límites UTC desde la zona comercial:

- día/hoy: día local;
- semana: últimos siete días locales, incluido hoy;
- mes/año: calendario local;
- desde/hasta: fechas locales inclusivas `YYYY-MM-DD`, con precedencia sobre período. Si falta desde se usa 1970-01-01; si falta hasta, hoy.

El ranking respeta el período. Stock bajo usa `COALESCE(stock_minimo,5)` en todos los indicadores. Límites: productos/usuarios/categorías 1000, ventas 500, movimientos generales 200, historial de resumen 100 y ranking 20. Los arrays informan truncamiento en `X-Result-Limit` y `X-Results-Truncated`; los clientes muestran aviso. El resumen incorpora `historialTruncado` y sus totales no se reducen al límite del detalle.

## Backups integrales

Compilar antes de instalar la versión operativa. El comando de backup ya no compila durante el respaldo:

```bash
npm run db:backup
npm run db:verify-backup -- /ruta/a/backup-completo
```

Cada directorio contiene SQLite obtenida mediante `db.backup()`, uploads y `manifest.json`: fecha única con UUID, versión de aplicación/esquema, tamaños y SHA-256. Se verifica integridad SQLite, foreign keys, archivos y referencias antes de publicar. Las copias incompletas se retiran. La retención solo afecta a directorios completos de esta herramienta, nunca a los `.db` legados o a directorios no verificables.

Con imágenes que se sustituyen simultáneamente, el backup puede fallar si falta una referencia: no publica silenciosamente una copia incompleta. Para la copia de despliegue usar una ventana sin escrituras. Las URLs externas legadas se registran como referencias externas; no se descargan.

Un backup en el mismo disco no protege de su avería. Copiar después el directorio completo y verificado a almacenamiento externo y volver a verificarlo allí. La programación y destino externo quedan a cargo de la operación; no se configuraron tareas en el servidor.

## Actualización futura en Ubuntu/systemd — NO ejecutada

1. Revisar el diff y aprobar una versión; resolver operaciones inciertas de los clientes antiguos y planificar actualización conjunta.
2. Comprobar Node >=22, secretos, zona y rutas absolutas existentes. No copiar `.env.example` encima del `.env` productivo.
3. Preparar código/dependencias/build en un directorio de release separado, sin abrir datos productivos. Comprobar better-sqlite3/Sharp y pasar suites.
4. En una ventana de mantenimiento, detener el servicio y crear un backup integral con la herramienta nueva, antes de migrar. Verificar y copiar fuera del disco.
5. Restaurar ese backup en un directorio de ensayo independiente; aplicar migraciones allí y comprobar usuarios, stock, ventas, imágenes y reportes.
6. Solo después de aprobar el ensayo, cambiar el release del servicio conservando rutas de datos/uploads, propietarios y secretos. Aplicar migraciones aditivas e iniciar.
7. Validar health, login, nueva sesión, permisos, imágenes, venta y recuperación idempotente con una operación controlada. Actualizar clientes firmados con la identidad existente; revisar versionCode/versionName antes de distribuir.
8. Revisar logs, espacio, retención y copia externa. El ejemplo systemd incluye usuario específico, reinicio y umask 0027. No fue instalado ni aplicado.

SIGTERM/SIGINT dejan de aceptar conexiones y esperan las peticiones activas antes de cerrar SQLite. No matar a la fuerza ni borrar `-wal`/`-shm` durante un cierre normal.

## Restauración futura — NO ejecutarla sobre producción sin aprobación

1. Detener escrituras/servicio y conservar una copia del estado fallido.
2. Verificar el backup completo; rechazar hashes, referencias o integridad inválidos.
3. Copiar `tienda.db` y `uploads` a **directorios de restauración nuevos**, separados de los actuales. No mezclar auxiliares WAL/SHM de otra base.
4. Probar primero con código compatible, migraciones y configuración de ensayo. La suite demuestra restauración sobre temporales.
5. Corregir propietario/permisos para el usuario del servicio. Cambiar rutas del servicio al conjunto completo restaurado únicamente tras validación.
6. Verificar health, cuentas, stocks, ventas e imágenes. Iniciar con el release compatible y exigir nuevo login si corresponde.

No hay downgrade destructivo automático. Un rollback debe considerar código, esquema, clientes y datos como conjunto. Volver al backend antiguo también elimina las garantías nuevas de sesión/idempotencia; requiere evaluación explícita.

## Builds y pruebas

```bash
# backend
npm run build
npm run test:unit
npm audit
# desde mobile
./gradlew :app:androidApp:assembleDebug :app:androidApp:testDebugUnitTest --no-configuration-cache
./gradlew :app:desktopApp:compileKotlin --no-configuration-cache
```

No instalar APK ni ejecutar ADB como parte de estas validaciones. Véase `docs/HARDENING-v1.1.md` para hallazgos, decisiones y límites pendientes.
