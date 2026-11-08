# Informe de cierre — Fase 2: trazabilidad del stock inicial

Fecha: 2026-11-08  
Alcance: validar el alta de productos con stock inicial, movimientos de apertura, edición, reintentos, historial y migraciones existentes.

## Resumen

La Fase 2 cumple los criterios de aceptación revisados. La creación de producto continúa delegando la escritura atómica en `DBHelper.createProductWithOpening`; no se modificaron `DBHelper.java`, el esquema SQLite ni las dependencias. El cambio productivo adicional se limitó a impedir que la pantalla anuncie éxito cuando una edición no actualiza ninguna fila.

La verificación final terminó con:

- 9/9 pruebas unitarias aprobadas mediante `testPhase2TestUnitTest`.
- 12/12 pruebas instrumentadas aprobadas mediante `connectedPhase2TestAndroidTest` en el AVD `Pixel_5(AVD) - 17`. Durante la ejecución, `adb shell getprop ro.build.version.sdk` devolvió `37`.
- `BUILD SUCCESSFUL` para `assembleDebug` y `assemblePhase2Test`.

Las pruebas instrumentadas usaron el identificador aislado `com.example.sqlite.phase2test`. El paquete normal `com.example.sqlite` no fue el target de estas pruebas. No se migró ni modificó una base real de usuario.

## Pruebas realizadas

### Unitarias

Tarea ejecutada: `testPhase2TestUnitTest`.

| Suite | Casos | Pasaron | Fallaron | Omitidos |
|---|---:|---:|---:|---:|
| `ExampleUnitTest` | 1 | 1 | 0 | 0 |
| `LocationMapsLinksTest` | 5 | 5 | 0 | 0 |
| `StockRulesTest` | 3 | 3 | 0 | 0 |
| **Total** | **9** | **9** | **0** | **0** |

El intento final de invocar `testDebugUnitTest` devolvió `Task 'testDebugUnitTest' not found`. La tarea unitaria ejecutada y aprobada fue `testPhase2TestUnitTest`, correspondiente a la variante seleccionada. No se reporta `testDebugUnitTest` como ejecutada.

### Instrumentadas

Tarea ejecutada: `connectedPhase2TestAndroidTest`. El reporte del dispositivo registra 12 casos, cero fallos, cero errores y cero omitidos.

| Suite | Casos | Cobertura principal |
|---|---:|---|
| `ExampleInstrumentedTest` | 1 | Acepta únicamente el package normal o el package aislado de pruebas. |
| `InitialAdminFlowTest` | 4 | Regresión del aprovisionamiento y migración administrativa existente. |
| `OpeningMovementTest` | 5 | Stock cero/positivo, datos de apertura, rollback, reintento con el mismo SKU y migraciones v7/v8. |
| `InventoryOpeningUiTest` | 2 | Alta desde `Leer`, ID de sesión, edición sin apertura adicional, y filtros/conteos de historial. |
| **Total** | **12** | **12 pasaron; 0 fallaron; 0 omitidos.** |

Los casos instrumentados utilizan bases de prueba. `OpeningMovementTest` usa `opening_movement_test.db`; las pruebas de UI limpian `BD_BODEGA.db` únicamente dentro del package `.phase2test`, tras verificar que el dispositivo es un emulador y que el package coincide exactamente.

### Compilación

La ejecución final de `testPhase2TestUnitTest assembleDebug assemblePhase2Test` terminó con `BUILD SUCCESSFUL`.

- APK debug normal: `app/build/outputs/apk/debug/app-debug.apk` (application ID `com.example.sqlite`).
- APK aislado de pruebas: `app/build/outputs/apk/phase2Test/app-phase2Test.apk` (application ID `com.example.sqlite.phase2test`).

## Comprobaciones funcionales

- **Alta con stock positivo:** la prueba de Activity crea el producto por el formulario, confirma `stock_actual`, una única fila `APERTURA` y el `id_usuario` que transmite la sesión guardada.
- **Stock inicial cero:** la prueba de Activity confirma saldo cero y ninguna apertura.
- **Reintento con el mismo SKU:** la prueba del helper confirma que la segunda inserción se rechaza por unicidad y que se conserva un producto, una apertura y el saldo inicial. El formulario usa ese mismo helper.
- **Edición:** la prueba de Activity cambia el nombre, conserva el stock y verifica que no se añade otra apertura.
- **Historial:** la prueba comprueba los conteos y el rótulo `APERTURA`, y aplica filtros de tipo para Aperturas, Entradas y Salidas, además de filtros de usuario y producto.
- **Migraciones:** los fixtures v7 y v8 verifican conservación de movimientos históricos, sus IDs/campos y claves foráneas; v8 también valida la secuencia de IDs y que el tipo `APERTURA` se acepte después de migrar. La prueba v7 confirma que no se inventan aperturas retroactivas.
- **Atomicidad:** el caso de rollback provoca un fallo de clave foránea en la fila de movimiento y comprueba que tampoco quede creado el producto.

## Archivos modificados

| Archivo | Cambio justificado |
|---|---|
| `app/src/main/java/com/example/sqlite/Leer.java` | La edición comprueba que `UPDATE PRODUCTO` afecte exactamente una fila; si no, no informa éxito. La alta sigue delegada al helper existente. |
| `app/src/androidTest/java/com/example/sqlite/OpeningMovementTest.java` | Añade cobertura de reintento con el mismo SKU y ausencia de duplicación. Conserva los casos previos. |
| `app/src/androidTest/java/com/example/sqlite/InventoryOpeningUiTest.java` | Añade comprobaciones instrumentadas de alta/edición y del historial; exige emulador y package aislado antes de limpiar su base de prueba. |
| `app/src/androidTest/java/com/example/sqlite/ExampleInstrumentedTest.java` | Permite explícitamente los identificadores `com.example.sqlite` y `com.example.sqlite.phase2test`. |
| `app/build.gradle.kts` | Define `phase2Test`, derivado de debug con sufijo de package, y lo selecciona para instrumentación. El build `debug` normal conserva su ID. |
| `PHASE_2_REPORT.md` | Registra el alcance, cambios, resultados, incidencias y pendientes. |

No se modificaron `DBHelper.java`, las versiones/dependencias, ni el esquema de base de datos. No se introdujeron migraciones.

## Incidencias encontradas y resueltas

1. La primera invocación instrumentada apuntó a una tarea que no existía antes de seleccionar `phase2Test`; no ejecutó pruebas.
2. Una invocación posterior pasó un argumento de runner que Gradle interpretó como nombre de tarea; tampoco llegó a ejecutar pruebas.
3. La primera suite ejecutada en AVD headless tuvo un fallo de expectativa del package y dos fallos de foco de ventana. La expectativa se amplió a los dos packages permitidos y el AVD se reinició con ventana.
4. La siguiente ejecución detectó que Espresso no admite las animaciones del dispositivo, que el test usaba `scrollTo()` sobre campos no contenidos en un contenedor desplazable, y que el matcher de apertura encontraba dos filas. Se desactivaron animaciones solo en el AVD, se retiró `scrollTo()` y se seleccionó una fila concreta. La ejecución final pasó 12/12.
5. Gradle muestra advertencias de AGP/Secrets Gradle Plugin indicando que `buildConfigFields` no tiene efecto porque `buildConfig` está deshabilitado para debug, release y `phase2Test`. No impidieron compilar ni probar.

## Límites y pendientes

- Los movimientos `ENTRADA` y `SALIDA` usados por la prueba de historial se insertan como fixtures SQLite; la prueba valida presentación, filtros y conteos, no los flujos de registro de `MovimientosActivity` ni su efecto sobre el stock.
- El alta UI crea una sesión instrumentada mediante `SessionStore`; valida la propagación del ID de sesión, pero no recorre la pantalla de login.
- La fecha de apertura se valida como no nula; no se afirma una validación de formato o zona horaria.
- El reporte de stock no recibió una prueba de regresión específica en esta fase.
- Las migraciones se probaron sobre fixtures v7/v8 en el AVD. No se ejecutaron sobre bases reales, según la restricción indicada.
- El workspace no contiene `.git` ni en la raíz ni en su padre. Por eso no se puede informar un `git status` ni confirmar cambios pendientes en Git. Los artefactos de compilación y resultados de prueba se generaron bajo `app/build/`.
- `AUDITORIA_FUNCIONALIDADES.md` corresponde a una auditoría previa y no se actualizó como parte de este entregable; debe leerse como baseline anterior a la Fase 2.

## Evaluación

La Fase 2 está **implementada y verificada para los criterios acordados**: alta positiva/stock cero, atribución a la sesión, reintento por SKU, edición sin aperturas adicionales, historial y migraciones v7/v8. Los límites anteriores describen flujos relacionados no cubiertos por esta suite, no fallos pendientes de los criterios principales de la fase.
