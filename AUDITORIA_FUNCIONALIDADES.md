# Auditoría de funcionalidades existentes — Sistema de Bodega Android

**Alcance:** inspección estática de código, recursos, configuración, documentación disponible y pruebas existentes. No se modificó la aplicación ni se ejecutaron build o pruebas. La única creación autorizada para esta tarea es este informe.

## 1. Resumen general

El proyecto es una aplicación Android local para inventario de bodega. Contiene autenticación y permisos, catálogo de productos, registro transaccional de entradas y salidas, informe de stock, historial de movimientos, administración básica y una pantalla de ubicación. Usa SQLite local y Google Code Scanner para leer códigos de barras.

Los flujos principales están representados en código, pero no hay evidencia de ejecución de las pruebas ni de validación manual en dispositivo. Por ello, en esta auditoría no se califica ningún flujo como **IMPLEMENTADA Y VERIFICADA**. La existencia de métodos y pruebas fuente demuestra implementación o intención, no funcionamiento en runtime.

Hay dos discrepancias funcionales concretas: CU-08 solicita Google Maps, pero la pantalla abre OpenStreetMap; además, el acceso al panel de Administración no aparece en el dashboard para usuarios que solo tienen permiso de gestionar datos maestros. El proyecto incluye únicamente `RF15-RF18.md`; no se encontró la especificación detallada de CU-03 a CU-08, de modo que la comparación con esos casos se limita a sus títulos y a la descripción proporcionada en la solicitud.

## 2. Tecnologías y arquitectura

- **Plataforma:** Android nativo; un módulo Gradle, `:app`.
- **Lenguaje:** Java 11; scripts de compilación Gradle Kotlin DSL.
- **SDK configurado:** `minSdk 30`, `compileSdk 37`, `targetSdk 37`.
- **Interfaz:** AndroidX AppCompat/Activity, vistas XML, ConstraintLayout y Material Components. Una Activity por pantalla principal.
- **Datos:** SQLite con `SQLiteOpenHelper`; esquema, semillas y migraciones en `DBHelper`, versión 7.
- **Escaneo:** Google Play Services Code Scanner (`play-services-code-scanner`); `BarcodeScanner` configura formatos lineales comunes.
- **Seguridad local:** hashes PBKDF2 para contraseñas y sesión cifrada AES-GCM con clave Android Keystore.
- **Pruebas:** JUnit y AndroidX Test/Espresso como dependencias.
- **No encontrados:** Google Maps SDK, Room, Retrofit, arquitectura por capas/repositorios, ViewModel, inyección de dependencias o módulo separado de dominio/datos.

La arquitectura efectiva es de una sola capa de aplicación: las Activities suelen combinar presentación, validación y SQL. `DBHelper`, `PermissionStore`, `SessionStore`, `StockRules`, `BarcodeScanner` y `DatabaseExecutor` reúnen responsabilidades compartidas. Algunas pantallas usan el ejecutor para operaciones SQLite; otras trabajan desde el hilo de UI.

## 3. Registro de funcionalidades existentes

| Funcionalidad | Estado | Archivos relacionados | Observaciones |
| ------------- | ------ | --------------------- | ------------- |
| Inicio, splash y autenticación local | IMPLEMENTADA, NO VERIFICADA | `SplashActivity.java`, `MainActivity.java`, `DBHelper.java`, `SessionStore.java`, `activity_splash.xml`, `activity_main.xml` | Splash espera 3,1 s. Login consulta usuarios y roles activos y verifica hash. La base nueva siembra `admin` / `admin123`; no se encontró cambio obligatorio de contraseña. Login y PBKDF2 se ejecutan en UI. |
| Sesión persistida y cierre de sesión | IMPLEMENTADA, NO VERIFICADA | `SessionStore.java`, `MainActivity.java` | Preferencias cifradas AES-GCM/Keystore; al iniciar se vuelve a comprobar que usuario y rol sigan activos. No hay prueba visible del flujo. |
| Dashboard y navegación según permisos | IMPLEMENTADA CON ERRORES | `MainActivity.java`, `PermissionStore.java`, `activity_dashboard.xml` | Oculta/expone accesos según permisos. El botón de Administración considera `USER_MANAGE` y `PROFILE_MANAGE`, pero omite `MASTER_DATA_MANAGE`, pese a que `AdminActivity` permite entrar solo con este último. |
| Catálogo: lista, búsqueda y filtro por categoría | IMPLEMENTADA, NO VERIFICADA | `Leer.java`, `activity_inventory.xml`, `item_list_row.xml`, `DBHelper.java` | Lista productos activos y permite búsqueda por nombre, SKU o código de barras, con filtro de categoría. |
| Alta de productos | IMPLEMENTADA, NO VERIFICADA | `Leer.java` (`productForm`, `saveProduct`), `DBHelper.java`, `activity_inventory.xml` | Captura SKU, código de barras opcional, nombre, descripción, unidad, stock inicial, mínimo y categoría. Valida campos numéricos no negativos y captura restricciones únicas. El stock inicial se guarda directamente sin movimiento de apertura. |
| Edición y desactivación de productos | IMPLEMENTADA, NO VERIFICADA | `Leer.java`, `DBHelper.java` | Permite editar datos excepto SKU y stock actual; desactiva lógicamente conservando el historial. No es borrado físico. |
| Escaneo para buscar producto | IMPLEMENTADA, NO VERIFICADA | `BarcodeScanner.java`, `Leer.java`, `gradle/libs.versions.toml`, `AndroidManifest.xml` | Scanner entrega el valor a la búsqueda del catálogo. No se ejecutó en dispositivo ni se verificó disponibilidad de Google Play Services/cámara. |
| Consultar stock y disponible | IMPLEMENTADA, NO VERIFICADA | `MovimientosActivity.java`, `Leer.java`, `activity_movements.xml` | Consulta por SKU o código de barras; el disponible se presenta igual al actual. Esa igualdad coincide con la nota de RF-17 que indica que no hay reservas. |
| Registrar entradas | IMPLEMENTADA, NO VERIFICADA | `MovimientosActivity.java`, `BarcodeScanner.java`, `DBHelper.java`, `activity_movements.xml` | Valida cantidad entera positiva y producto activo; proveedor/nota opcionales; actualiza producto y añade movimiento dentro de transacción. Sin pruebas automatizadas del caso. |
| Registrar salidas | IMPLEMENTADA, NO VERIFICADA | `MovimientosActivity.java`, `DBHelper.java`, `activity_movements.xml` | Exige motivo y valida que no supere el stock consultado; actualización condicionada y registro se realizan dentro de transacción. Rollback y límites no están probados. |
| Informe de stock | IMPLEMENTADA, NO VERIFICADA | `StockReportActivity.java`, `StockRules.java`, `FilterListener.java`, `activity_stock_report.xml` | Filtros por categoría, producto y estado; presenta conteos de sin stock/críticos/normales. No se encontró exportación del informe. |
| Historial de movimientos | IMPLEMENTADA, NO VERIFICADA | `HistorialActivity.java`, `activity_history.xml`, `DBHelper.java` | Lista entradas y salidas con fecha, producto, usuario, cantidad, nota y proveedor cuando existe; filtros por producto, usuario y tipo. El stock inicial de productos no aparece como movimiento. |
| Administración de usuarios | IMPLEMENTADA, NO VERIFICADA | `AdminActivity.java`, `PermissionStore.java`, `DBHelper.java`, `activity_admin.xml` | Crea usuarios y permite activar/desactivar. Valida contraseña mínima de 4 caracteres y username único por restricción; no se encontró cambio/restablecimiento de contraseña para usuarios existentes. |
| Administración de roles y permisos | IMPLEMENTADA, NO VERIFICADA | `AdminActivity.java`, `PermissionManagerActivity.java`, `PermissionCatalog.java`, `PermissionStore.java`, `DBHelper.java` | Crea/desactiva roles salvo el rol Administrador; permite permisos por rol y ajustes individuales. Acceso al panel con solo `MASTER_DATA_MANAGE` está oculto por el dashboard. |
| Categorías y proveedores | PARCIALMENTE IMPLEMENTADA | `AdminActivity.java`, `CategoriasActivity.java`, `DBHelper.java` | Alta/lista y activación/desactivación para categorías; lectura de categorías activas. Proveedores: alta y listado, pero no se encontró edición ni desactivación desde UI. Acceso al panel depende del defecto del botón de Administración. |
| Ubicación del local y mapa | PARCIALMENTE IMPLEMENTADA | `LocationActivity.java`, `MainActivity.java`, `activity_location.xml`, `DBHelper.java` | Guarda nombre/dirección/coordenadas, permite vista pública y abre OpenStreetMap por coordenadas o búsqueda por dirección. No usa Google Maps. |
| Reglas de clasificación de stock | IMPLEMENTADA, NO VERIFICADA | `StockRules.java`, `StockRulesTest.java` | Tres pruebas fuente cubren stock cero, crítico y normal; no se ejecutaron durante esta auditoría. |
| Migración y conservación de datos SQLite | PARCIALMENTE IMPLEMENTADA | `DBHelper.java`, `RF15-RF18.md` | `DBHelper` está en versión 7 y contempla columnas de versiones antiguas más creación idempotente de tablas/índice y semillas. No se encontraron pruebas de migración y la documentación disponible no cubre toda la historia hasta v7. |

## 4. Cumplimiento de CU-03 a CU-08

Los estados y observaciones siguientes comparan el código con los títulos de CU recibidos. No sustituyen una revisión de criterios de aceptación completos, que no están en los archivos del proyecto.

| Caso de uso | Estado | Implementado según el código | Parcial o pendiente | Errores o riesgos | Archivos para futura intervención |
| ----------- | ------ | ----------------------------- | ------------------- | ----------------- | ------------------------------- |
| **CU-03: Mantener productos** | IMPLEMENTADA, NO VERIFICADA | Alta, edición, búsqueda por nombre/SKU/código, filtro por categoría y desactivación lógica; campos de unidad y stock mínimo. | No se encontró restauración/edición de SKU o stock actual desde el formulario. El catálogo inicial incluye stock directo. Criterios exactos de CU no disponibles. | El saldo inicial no genera movimiento de apertura, por lo que el historial no explica ese saldo si se requiere trazabilidad completa. Sin pruebas de CRUD/validaciones. | `Leer.java`, `activity_inventory.xml`, `item_list_row.xml`, `DBHelper.java`, `PermissionStore.java`, `StockRules.java`. |
| **CU-04: Registrar entradas** | IMPLEMENTADA, NO VERIFICADA | Entrada por SKU/código escaneado o manual; cantidad positiva, proveedor/nota opcionales, producto activo, actualización y movimiento transaccionales. | No hay pruebas de integración ni ejecución en dispositivo. No se confirma si el CU exige campos o reportes adicionales. | No hay prueba de rollback. Las cantidades/stock se manejan como `int` en parte del flujo; escenarios extremos requieren verificación. | `MovimientosActivity.java`, `activity_movements.xml`, `BarcodeScanner.java`, `DBHelper.java`, `DatabaseExecutor.java`. |
| **CU-05: Registrar salidas** | IMPLEMENTADA, NO VERIFICADA | Exige motivo, cantidad entera positiva, producto activo y stock suficiente; actualiza stock y guarda movimiento en una transacción. | No se encontraron pruebas de transacción, rollback, doble envío o límites. | La lógica parece cubrir el invariante principal en fuente, pero su comportamiento en runtime no ha sido comprobado. | `MovimientosActivity.java`, `activity_movements.xml`, `DBHelper.java`, `DatabaseExecutor.java`. |
| **CU-06: Generar informe de stock** | IMPLEMENTADA, NO VERIFICADA | Informe de productos activos con filtros por categoría, producto y estado; muestra stock actual/mínimo y conteos por estado. | Exportación, ordenamientos o campos adicionales no pueden confirmarse frente al CU detallado ausente. | No hay prueba del filtrado ni de consistencia con registros reales. | `StockReportActivity.java`, `StockRules.java`, `FilterListener.java`, `activity_stock_report.xml`, `DBHelper.java`. |
| **CU-07: Buscar productos mediante código de barras** | IMPLEMENTADA, NO VERIFICADA | Escaneo ML Kit en catálogo y movimientos; consulta por código; columna e índice único parcial para códigos informados. | No se verificó escáner en dispositivo, disponibilidad de GMS ni los formatos requeridos por el CU. | No hay pruebas de integración para coincidencias, códigos desconocidos o cancelación/error del scanner. | `BarcodeScanner.java`, `Leer.java`, `MovimientosActivity.java`, `DBHelper.java`, `AndroidManifest.xml`, `activity_inventory.xml`, `activity_movements.xml`. |
| **CU-08: Mostrar ubicación mediante Google Maps** | PARCIALMENTE IMPLEMENTADA | Persistencia local de nombre, dirección, latitud/longitud; apertura de mapa/búsqueda desde la pantalla. | La app abre OpenStreetMap y no Google Maps; falta confirmar si se acepta proveedor alternativo. No hay Maps SDK ni clave/dependencia de Maps. | No cumple el proveedor nombrado en el título del CU según la implementación encontrada. La apertura externa depende de conexión y navegador/app disponible. | `LocationActivity.java`, `activity_location.xml`, `MainActivity.java`, `DBHelper.java`, `AndroidManifest.xml`, configuración Gradle si se aprueba la integración Google Maps. |

## 5. Problemas y errores detectados

### Confirmados por inspección de código

1. **Credencial de administrador conocida:** `DBHelper.seed()` crea `admin` con `admin123`; `MainActivity.authenticate()` permite iniciar sesión con esa credencial. PBKDF2 protege el hash almacenado, pero no mitiga una contraseña inicial pública. No se encontró cambio obligatorio.
2. **CU-08 no usa Google Maps:** `LocationActivity.openMap()` arma enlaces `openstreetmap.org`. La pantalla y el código fuente identifican explícitamente OpenStreetMap.
3. **Administración inaccesible a un permiso permitido:** `AdminActivity.onCreate()` permite acceso con `MASTER_DATA_MANAGE`, pero `MainActivity.dashboard()` muestra el botón solo con `USER_MANAGE || PROFILE_MANAGE`. Un usuario con únicamente permiso de datos maestros no tiene acceso desde el dashboard.
4. **Saldo inicial sin asiento de movimiento:** al crear producto, `Leer.saveProduct()` persiste `stock_actual`; el historial consulta `MOVIMIENTO`. Por tanto, ese saldo no tiene un evento correspondiente en el historial. La necesidad de asiento de apertura depende de los requisitos de auditoría del negocio.

### Riesgos o hipótesis que requieren verificación

- **Fluidez:** autenticación/hash y varias operaciones en `MainActivity`, `MovimientosActivity`, `LocationActivity`, `AdminActivity` y `PermissionManagerActivity` usan SQLite desde UI; podría congelar la interfaz con operaciones lentas o más datos, pero no se observó una ejecución.
- **Ciclo de vida:** algunas Activities cierran `DatabaseExecutor` y `DBHelper` al destruirse mientras hay tareas; hace falta probar navegación/cierre durante cargas para determinar si hay resultados perdidos o callbacks tardíos.
- **Backup:** `AndroidManifest.xml` permite backup y los XML asociados no especifican exclusiones concretas. Debe decidirse si la base de datos local y preferencias deben entrar en respaldo.
- **Extremos numéricos:** el flujo de movimientos parsea cantidades y lee stock como `int`; verificar valores grandes y el límite práctico de inventario.
- **Migraciones:** el esquema está en v7, pero la documentación encontrada solo describe cambios seleccionados hasta v4. No se probaron conversiones desde bases de datos existentes.

## 6. Funcionalidades que deben preservarse

- La transacción de `MovimientosActivity` que actualiza `PRODUCTO` y registra `MOVIMIENTO` como una sola operación.
- Las validaciones y restricciones de base de datos para stock no negativo, cantidad positiva, tipo de movimiento y unicidad de SKU/código de barras.
- Claves foráneas habilitadas en `DBHelper.onConfigure()` y el índice único parcial que permite códigos de barras vacíos en productos existentes.
- `StockRules` como lógica pura separada de UI, y sus pruebas de frontera existentes.
- Los componentes focalizados `BarcodeScanner`, `SessionStore` y `PermissionStore`; las Activities/layouts actuales pueden mantenerse sin reescritura de plataforma.
- La desactivación lógica de productos que conserva referencias e historial.

## 7. Funcionalidades que necesitan mejoras

- Aprovisionamiento de cuenta inicial y cambio de contraseña; revisar también el mínimo de longitud actual para nuevas cuentas.
- Acceso al panel Administración según los mismos permisos que la Activity acepta.
- Trazabilidad de stock inicial, si el requisito de negocio exige reconstruir el saldo desde el historial.
- Trabajo SQLite fuera de UI de manera uniforme y callbacks seguros al cambiar el ciclo de vida.
- Pruebas de movimientos, login/permisos, CRUD, migraciones, filtros de informes e integración de escáner.
- Registro/documentación de todas las migraciones hasta la versión 7 y política explícita de backup.
- Definir si CU-08 debe abrir Google Maps específicamente o si se acepta el mapa OpenStreetMap existente.
- Mantenibilidad: `AdminActivity` y partes de `PermissionManagerActivity` concentran múltiples responsabilidades y usan métodos muy compactos; priorizar mejoras locales cuando se amplíe ese flujo.

## 8. Funcionalidades que deben desarrollarse o completarse

- **CU-08:** integración con Google Maps si el proveedor indicado es obligatorio; falta la dependencia/configuración y el flujo correspondiente. Si no es obligatorio, documentar formalmente la equivalencia con OpenStreetMap.
- **Acceso de datos maestros:** hacer accesible desde dashboard el panel para el rol que tenga exclusivamente `MASTER_DATA_MANAGE`.
- **Administración de proveedores:** si el alcance lo requiere, completar edición y activación/desactivación; actualmente se observan alta y lista.
- **Trazabilidad de apertura:** implementar un movimiento de apertura solo si el requisito funcional/auditoría confirma que el stock inicial debe quedar registrado.
- **Verificación:** incorporar pruebas para los flujos funcionales críticos y probar migraciones; a la fecha de esta auditoría no hay evidencia de ejecución de esas verificaciones.

No se infiere que deban desarrollarse funcionalidades no descritas en el código o en los documentos disponibles (por ejemplo, exportación de informes, sincronización remota o administración de múltiples bodegas).

## 9. Recomendaciones y orden de implementación

1. **Completar la especificación:** añadir el documento con escenarios y criterios de aceptación de CU-03 a CU-08. Aclarar expresamente Google Maps vs. OpenStreetMap y trazabilidad del stock inicial.
2. **Resolver riesgos de acceso:** reemplazar la credencial inicial conocida por un aprovisionamiento seguro y corregir la visibilidad del panel de Administración para `MASTER_DATA_MANAGE`.
3. **Fijar comportamiento de inventario:** acordar si el alta con stock crea un movimiento de apertura; preservar la transacción existente de entradas/salidas.
4. **Verificar en pruebas:** cubrir alta/edición/desactivación, permisos, movimientos y rollback, filtros de informe, escaneo y migración con datos previos. Después ejecutar build y pruebas en entorno Android; no se hicieron en esta auditoría.
5. **Completar CU-08:** implementar proveedor requerido o registrar aceptación de la solución existente. Añadir manejo comprobado de ausencia de aplicación/conexión según el criterio acordado.
6. **Mejorar mantenibilidad de forma incremental:** mover primero operaciones de SQLite que afectan fluidez y luego separar reglas/datos de Activities en los flujos que se toquen. No se justifica por la evidencia actual una reescritura general ni migración a Compose.
7. **Preparar distribución:** definir política de backup, documentar migraciones y probarlas antes de publicar actualizaciones.

## Evidencia y límites de la auditoría

- **Código encontrado:** fuente Java, layouts XML, manifiesto, configuración Gradle, esquema SQLite y documento `RF15-RF18.md`.
- **Pruebas fuente encontradas:** `StockRulesTest.java` (tres casos de clasificación), `ExampleUnitTest.java` (suma trivial) y `ExampleInstrumentedTest.java` (package name).
- **Comportamiento probado en esta auditoría:** ninguno. No se ejecutaron pruebas, build, aplicación en emulador/dispositivo ni flujos manuales.
- **Hipótesis:** riesgos de fluidez/ciclo de vida y los requisitos no visibles en CU detallados requieren reproducción o aclaración; no se presentan como defectos observados en ejecución.
- **Documentación ausente:** no se encontró README ni especificación completa de CU-03 a CU-08 en los archivos del proyecto inspeccionados.
