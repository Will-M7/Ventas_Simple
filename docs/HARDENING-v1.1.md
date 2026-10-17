# Cierre técnico v1.1

Referencia: `c1b3142`, trabajo sin commit sobre `v1.1-desarrollo`. Se preservan las correcciones del Bloque 1. Ningún procedimiento de este documento autoriza desplegar, conectarse a producción, resetear o sembrar datos reales.

## Decisiones

- Migrations: baseline actual y cambios aditivos versionados, transaccionales; sin reconstrucción ni borrado de datos.
- Ventas HTTP: UUID obligatorio. Repetición con mismo usuario/cantidades devuelve la original; otra asociación da 409. Se valida antes de recalcular precios/stock de un replay.
- Sesiones: versión en usuario y JWT, rol vigente desde SQLite. El deployment requiere nuevo login para tokens antiguos.
- Bootstrap: únicamente si no hay usuarios, con credenciales configuradas. No cambia usuarios existentes.
- Password: mínimo 8 caracteres, máximo 72 bytes; bootstrap mínimo 12. bcrypt async conserva costo 10 y evita los bucles síncronos de hashing. Sigue consumiendo CPU, con límites por IP y par IP/usuario.
- Dinero: centavos durante cálculo, REAL compatible en almacenamiento. Se rechazan precios de más de dos decimales; no se migran importes históricos ni se inventa costo pasado.
- Imágenes: Multer 2.4.0, buffer acotado, Sharp con validación/decodificación, formatos estáticos JPEG/PNG/WebP y máximo 40 MP. La restricción de píxeles y el máximo de dos decodificaciones simultáneas son defensas de memoria, no un aumento de 5 MiB.
- Zona comercial: Luxon con IANA, America/Lima por defecto; rangos UTC indexables y final exclusivo. Semana conserva el sentido de últimos 7 días.
- Android: AppContainer único por proceso, clientes vinculados a URL/token y pool compartido; 401 global protegido frente a respuestas de una sesión anterior. Cancelación no se convierte en error de negocio.
- Recuperación Android: ViewModel y borrador privado por usuario/servidor, escritura serializada y confirmada antes del envío. Una lectura corrupta bloquea, no descarta silenciosamente. El bloqueo del Bloque 1 se conserva y también cubre resultados tardíos.
- Formularios: recuperación incremental de campos/URI/createdId. No se declara persistencia completa de un alta multietapa ante cualquier terminación del proceso.
- Sesión Android: MODE_PRIVATE, backup deshabilitado y exclusiones explícitas de shared preferences para backup y transferencia. No se añadió una dependencia de cifrado obsoleta.
- Desktop: llamadas bloqueantes y lectura/subida de archivos en IO, timeouts, MIME real, reintento de imagen separado y archivo de venta pendiente por servidor/usuario, sin JWT en ese archivo.
- Scanner: únicamente logging; categorías estables, máximo 128 entradas y logs detallados solo debug. No se alteran selección, ROI, formatos, validadores, confirmación, cámara, zoom o cooldown.
- Backups: snapshot SQLite API, uploads, manifest, hashes, integridad, referencias, nombre único, retención de copias verificadas y verificador separado. Copia externa/restauración descritas en README, sin tocar servidor.

## Clasificación de código y plataformas

| Elemento | Clase | Acción |
|---|---|---|
| Express/SQLite, Android y Desktop | A: utilizado | Corregido y compilado/probado |
| KMP shared/core, iOS/Web, Ktor | B: plantilla/demo | Conservado; no presentado como tienda implementada |
| Docker/MySQL/schema.sql | C: legacy confirmado | Marcado incompatible y excluido de despliegue soportado |
| seed.js HTTP antiguo | C: legacy confirmado | Conservado como referencia, ejecución deshabilitada |
| test-api/test-ventas anteriores | C: contratos antiguos | Entry points sustituidos por suite aislada |
| PosViewModel/InventoryViewModel/ReportsViewModel Android anteriores | D: sin referencias | Retirados; cart model compartido con SaleViewModel actual. No forman parte de otras plataformas |
| Pantallas Android Categorías/Mercadería | Sin acceso actual, función a decidir | Conservadas sin conectar navegación |
| Algunos wrappers/casos de uso sin consumidores | D/posible reutilización | Conservados cuando no es necesaria su eliminación para estas correcciones |

## Estado de hallazgos

| ID | Estado | Evidencia / límite |
|---|---|---|
| GIT-01 | MITIGADO | Exclusión de nuevas fotos y documentación; dos fotos operativas con EXIF ya versionadas se conservan; no son fixtures, no se afirma GPS y no se borra historial |
| AND-02 | MITIGADO | Carrito/operación en ViewModel y persistencia privada; formularios guardables. Falta QA física completa de recreación/terminación durante altas |
| AND-03 | RESUELTO | apiCall/ApiFailure, interceptor 401, QueryContent y estados de carga/error/vacío/datos |
| AND-04 | RESUELTO | Contenedor estable, pool compartido y CancellationException propagada |
| SCN-01 | RESUELTO | ScanLog acotado, categorías estables y debug-only |
| SCN-02 | POSPUESTO CON JUSTIFICACIÓN | ROI solo prioriza: candidato único puede aceptarse fuera del marco o sin geometría. Documentado; cambio condicionado a pruebas físicas con etiquetas cercanas |
| IMG-01 | RESUELTO | Firma, MIME consistente, decodificación/normalización, extensión real y publicación restringida |
| IMG-02 | MITIGADO | Fallos controlados/reemplazo limpian sin borrar referencias; SIGKILL o fallos de disco aún pueden dejar huérfanos; no se borran huérfanos históricos automáticamente |
| IMG-03 | RESUELTO | MIME real Desktop y alta separada de reintento de imagen |
| BE-01 | RESUELTO | Validadores y errores HTTP comunes, limit numérico parametrizado y 500 genérico |
| DB-01 | RESUELTO | Dos decimales/centavos exactos sin migración REAL->INTEGER; histórico arbitrario no se reescribe |
| DB-02 | RESUELTO | schema_migrations, transacciones, repetibilidad y pruebas con esquema anterior |
| DB-03 | POSPUESTO CON JUSTIFICACIÓN | Precio vendido ya se conserva; faltan snapshots de nombre/código y costo histórico. Añadirlos al diseñar comprobantes/devoluciones/rentabilidad, sin inventar datos pasados |
| AUT-01 | RESUELTO | Versión de sesión y estado/rol actual por petición |
| AUT-02 | RESUELTO | Bootstrap único con configuración segura, usuarios existentes conservados |
| AUT-03 | RESUELTO | Límites temporales por IP/par usuario y bcrypt async; limiter local a un proceso |
| VTA-02 | RESUELTO | UUID HTTP obligatorio, índice único, fingerprint, persistencia/replay en clientes; actualizar clientes antiguos junto al servidor |
| REP-01 | RESUELTO | Zona comercial y límites UTC, tests medianoche y DST |
| REP-02 | RESUELTO | Rango explícito tiene precedencia y fechas inválidas/invertidas son 400 |
| REP-03 | RESUELTO | Ranking por período y mínimo unificado; truncamientos informados |
| DEP-01 | RESUELTO | Multer 2.4.0, memoryStorage acotado y tests de exceso/aborto |
| DEP-02 | RESUELTO | proxy-addr 2.0.8; no se habilita trust proxy |
| SEG-01 | MITIGADO | URLs HTTPS soportadas; TLS y aislamiento productivos requieren operación posterior, HTTP LAN permanece |
| SEG-02 | RESUELTO | Exclusiones de sesión/drafts y backups deshabilitados, sin logging de tokens ni cifrado cosmético. Permisos del servidor real requieren comprobación futura |
| OPS-01 | RESUELTO | Backup/restauración temporal/verificación/retención; programación y copia externa productivas aún deben instalarse |
| OPS-02 | MITIGADO | Docker/MySQL claramente legacy/incompatibles; no se declara un Docker nuevo funcional |
| OPS-03 | RESUELTO | Health real, shutdown esperando peticiones, reset protegido; observabilidad avanzada queda fuera |
| DEP-03 | RESUELTO | engines Node >=22 y README actualizados |
| TST-01 | RESUELTO | Suite aislada con HTTP propio; entry points antiguos ya no apuntan a BASE_URL |
| DEU-01 | MITIGADO | SaleViewModel/modelo de borrador y validación/errores extraídos; no se reescriben todas las pantallas ni se fusionan clientes |

VTA-01, PRD-01, AND-01 y bloqueo/snapshot de VTA-03 siguen cubiertos por los casos del Bloque 1. El precio sigue siendo autoridad del servidor; el cliente muestra el total confirmado. No se implementa todavía negociación de cambios de precio mientras un carrito está abierto.

## Riesgos que no deben ocultarse

- No se verificó el Ubuntu Server, exposición del puerto, contraseñas, permisos ni backups productivos.
- No se ejecutaron pruebas físicas de Android, Photo Picker, cámara o scanner en esta fase. Se deben probar antes de distribuir.
- Un ajuste de inventario con respuesta perdida no tiene operation_id: comprobar histórico antes de repetirlo.
- Una pérdida física del almacenamiento del cliente puede impedir recuperar un ID pendiente. No borrar datos ni reinstalar para resolver una operación incierta sin conciliación.
- Una edición/alta de producto es multietapa; no hay idempotencia de creación de productos ni garantía de completar una subida tras muerte del proceso.
- Archivos y SQLite no forman una transacción única. Se mantienen referencias seguras en el flujo normal; un crash puede dejar huérfanos, que requieren revisión con backup.
- El backup local debe copiarse fuera del disco y programarse posteriormente. Ante imágenes concurrentemente eliminadas puede fallar con seguridad y requerir reintento/ventana de mantenimiento.
- Los nombres/códigos de ventas antiguas aún proceden del producto actual; el costo histórico no existe.
- Los indicadores de dinero operan en centavos seguros por venta, pero no se certifica contabilidad ni se alteran importes históricos.
- Los límites se muestran; no se implementó paginación general ni exportación comercial.
- Las versiones y la firma de distribución deben revisarse al preparar la release; estos builds son de validación, no un despliegue.

## Auditoría de dependencias

Antes: 6 paquetes afectados (1 crítica, 4 altas, 1 media). Cambios existentes limitados a Multer 2.3.0->2.4.0, proxy-addr 2.0.7->2.0.8 y brace-expansion 1.1.18->1.1.21. Nuevos runtime: Sharp 0.35.5 por IMG-01 y Luxon 3.7.2 por REP-01; tipos Luxon solo desarrollo. No se ejecuta audit fix.

La cadena ts-node-dev/chokidar/braces sigue siendo tooling de desarrollo sin flujo HTTP identificado. No ejecutar `npm run dev` como servicio productivo. Resultado final de `npm audit`: 3 altas (braces, chokidar y ts-node-dev), 0 críticas, 0 medias y 0 bajas. Son la misma cadena de tooling; el registry no ofrece fix para ella. No se identificó un flujo de entrada HTTP hacia esos globs. No equivalen a tres fallos de runtime independientes. Se pospone sustituir el runner de desarrollo; requiere validar su flujo de watch/reinicio y no se necesita para ejecutar `node dist/server.js`.

## Validación ejecutada

- Backend: `npm run build` y `npm run test:unit` correctos. Suite final: 48 pruebas, 48 aprobadas, cero fallidas, canceladas u omitidas. Entorno local Node 24.21.0 / npm 11.19.0; no se ejecutó una matriz de versiones Node.
- Android: `:app:androidApp:assembleDebug` y `:app:androidApp:testDebugUnitTest` correctos; 4 pruebas unitarias aprobadas. Son pruebas de modelo/serialización/URL; no sustituyen instrumentación de Activity ni pruebas físicas.
- Desktop: `:app:desktopApp:compileKotlin` correcto. No se abrió la aplicación ni se hicieron pruebas de interacción de escritorio.
- Gradle se ejecutó con `--no-configuration-cache`. Aviso de plantilla esperado: simulador iOS no disponible en Linux; no afecta estos builds.
- `git diff --check`, `node --check` de los scripts operativos y `bash -n` de test-api.sh correctos. `--check` comprueba sintaxis; reset/seed no fueron ejecutados.
- Regresiones backend cubren los 14 casos originales de Bloque 1, migraciones/rollback y PRAGMA, bootstrap/auth/roles/password/último admin, idempotencia/POST simultáneos/respuesta descartada, dinero, inventario, límites/errores, medianoche Lima/DST/rangos/ranking, formatos/spoofing/extensiones/tamaño/reemplazo/aborto/concurrencia de uploads, backup/integridad/tampering/retención/restauración temporal y cierre esperando una petición activa.
- La suite no abre `backend/data/tienda.db`: las fixtures usan `:memory:` y las pruebas de backup usan `mkdtemp`. No utiliza un servidor existente.
- Los 11 archivos originales de data/uploads/backups se compararon mediante SHA-256: cero diferencias, cero archivos nuevos en esas carpetas. Se incluyen DB, WAL, SHM, backups e imágenes.
- HEAD y revision-final permanecen en `c1b3142c851b3efa7c53f4bc599c4655266efa0b`. No hubo commit, push, despliegue, conexión al Ubuntu Server, instalación de APK ni ADB.

### Validación física pendiente antes de distribuir

- Rotar/recrear Activity con carrito abierto y con venta pendiente; interrumpir la respuesta y comprobar recuperación de la misma operación tras reiniciar la aplicación.
- Photo Picker/cámara: rotación durante captura, retorno a formulario y reintento de imagen de producto ya creado. Se preservan URI/createdId, pero aún falta QA en dispositivo real.
- Scanner: etiqueta única fuera del marco y varios productos próximos, distancias/luces/dispositivos ya usados en producción. El objetivo es observar el fallback, sin modificar algoritmo.
- Login nuevo tras revocación y cambio de servidor en los clientes reales; comprobar que no se reutiliza una sesión de otro servidor.

## Inventario de archivos del diff

Estado frente a HEAD `c1b3142`: 51 archivos rastreados modificados y 24 archivos sin rastrear. El asterisco indica trabajo de Bloque 1 que ya estaba presente y se conservó/amplió; el inventario no equivale a cambios exclusivos de esta fase. No hay archivos rastreados eliminados. Los builds/dependencias instaladas están ignorados.

- [.gitignore](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/.gitignore)
- [README.md](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/README.md)
- [backend/.env.example](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/.env.example)
- [backend/Dockerfile](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/Dockerfile)
- [backend/docker-compose.yml](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/docker-compose.yml)
- [backend/package-lock.json](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/package-lock.json)
- [backend/package.json](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/package.json) — Bloque 1 previo *
- [backend/schema.sql](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/schema.sql)
- [backend/scripts/backup-db.js](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/scripts/backup-db.js)
- [backend/scripts/migrate-db.js](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/scripts/migrate-db.js)
- [backend/scripts/reset-db.js](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/scripts/reset-db.js)
- [backend/scripts/seed-demo.js](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/scripts/seed-demo.js)
- [backend/scripts/verify-backup.js](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/scripts/verify-backup.js)
- [backend/seed.js](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/seed.js)
- [backend/src/app.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/app.ts)
- [backend/src/config/database.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/config/database.ts)
- [backend/src/config/migrations.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/config/migrations.ts)
- [backend/src/config/schema.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/config/schema.ts)
- [backend/src/controllers/categoria.controller.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/controllers/categoria.controller.ts)
- [backend/src/controllers/inventory.controller.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/controllers/inventory.controller.ts)
- [backend/src/controllers/producto.controller.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/controllers/producto.controller.ts)
- [backend/src/controllers/reporte.controller.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/controllers/reporte.controller.ts)
- [backend/src/controllers/usuario.controller.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/controllers/usuario.controller.ts)
- [backend/src/controllers/venta.controller.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/controllers/venta.controller.ts)
- [backend/src/middleware/authenticate.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/middleware/authenticate.ts)
- [backend/src/middleware/errors.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/middleware/errors.ts)
- [backend/src/middleware/loginLimit.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/middleware/loginLimit.ts)
- [backend/src/middleware/upload.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/middleware/upload.ts)
- [backend/src/operations/backup.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/operations/backup.ts)
- [backend/src/routes/usuarios.routes.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/routes/usuarios.routes.ts)
- [backend/src/server.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/server.ts)
- [backend/src/services/categoria.service.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/services/categoria.service.ts)
- [backend/src/services/image.service.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/services/image.service.ts)
- [backend/src/services/inventory.service.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/services/inventory.service.ts)
- [backend/src/services/producto.service.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/services/producto.service.ts) — Bloque 1 previo *
- [backend/src/services/reporte.service.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/services/reporte.service.ts)
- [backend/src/services/usuario.service.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/services/usuario.service.ts)
- [backend/src/services/venta.service.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/services/venta.service.ts) — Bloque 1 previo *
- [backend/src/utils/dates.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/utils/dates.ts)
- [backend/src/utils/http.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/utils/http.ts)
- [backend/src/utils/operationGuard.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/utils/operationGuard.ts)
- [backend/src/utils/security.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/utils/security.ts)
- [backend/src/utils/validation.ts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/src/utils/validation.ts)
- [backend/test-api.sh](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/test-api.sh)
- [backend/test-ventas.js](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/test-ventas.js)
- [backend/tests/fixtures.cjs](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/tests/fixtures.cjs)
- [backend/tests/hardening.test.cjs](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/tests/hardening.test.cjs)
- [backend/tests/run.cjs](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/tests/run.cjs)
- [backend/tests/stock-sales.test.cjs](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/tests/stock-sales.test.cjs) — Bloque 1 previo *
- [backend/tests/uploads-backups.test.cjs](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/backend/tests/uploads-backups.test.cjs)
- [deploy/systemd/sistematienda.service.example](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/deploy/systemd/sistematienda.service.example)
- [docs/HARDENING-v1.1.md](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/docs/HARDENING-v1.1.md)
- [mobile/app/androidApp/build.gradle.kts](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/build.gradle.kts)
- [mobile/app/androidApp/src/main/AndroidManifest.xml](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/AndroidManifest.xml)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/data/AppContainer.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/data/AppContainer.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/data/local/UserPreferencesManager.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/data/local/UserPreferencesManager.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/data/remote/ApiFactory.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/data/remote/ApiFactory.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/data/remote/ServerAddress.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/data/remote/ServerAddress.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/data/remote/StoreApi.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/data/remote/StoreApi.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/data/repositories/RepositoryImpl.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/data/repositories/RepositoryImpl.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/domain/model/Models.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/domain/model/Models.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/domain/model/SaleDraft.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/domain/model/SaleDraft.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/domain/repository/Repositories.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/domain/repository/Repositories.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/domain/usecase/UseCases.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/domain/usecase/UseCases.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/ui/BarcodeScanner.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/ui/BarcodeScanner.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/ui/StoreApp.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/ui/StoreApp.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/ui/navigation/AppNavigation.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/ui/navigation/AppNavigation.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/ui/screens/Screens.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/ui/screens/Screens.kt) — Bloque 1 previo *
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/ui/viewmodels/SaleViewModel.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/ui/viewmodels/SaleViewModel.kt)
- [mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/ui/viewmodels/ViewModels.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/kotlin/com/example/sistema_tienda/ui/viewmodels/ViewModels.kt)
- [mobile/app/androidApp/src/main/res/xml/backup_rules.xml](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/res/xml/backup_rules.xml)
- [mobile/app/androidApp/src/main/res/xml/data_extraction_rules.xml](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/main/res/xml/data_extraction_rules.xml)
- [mobile/app/androidApp/src/test/kotlin/com/example/sistema_tienda/SaleDraftTest.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/androidApp/src/test/kotlin/com/example/sistema_tienda/SaleDraftTest.kt)
- [mobile/app/desktopApp/src/main/kotlin/com/example/sistema_tienda/DesktopApi.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/desktopApp/src/main/kotlin/com/example/sistema_tienda/DesktopApi.kt)
- [mobile/app/desktopApp/src/main/kotlin/com/example/sistema_tienda/DesktopApp.kt](/home/willian/Desarrollo/Proyectos/Ventas/sistematienda/mobile/app/desktopApp/src/main/kotlin/com/example/sistema_tienda/DesktopApp.kt)
