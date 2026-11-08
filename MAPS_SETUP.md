# Configuración de Google Maps para Android

La app usa Google Maps SDK for Android para dibujar el mapa. Los enlaces de ubicación e indicaciones se mantienen como respaldo cuando el SDK no carga.

## Clave local

1. En Google Cloud, habilita **Maps SDK for Android**, configura facturación y crea una API key restringida a este SDK y al identificador Android de la app (`com.example.sqlite`) con la huella SHA-1 del certificado que corresponda. Consulta la [configuración oficial de Maps SDK](https://developers.google.com/maps/documentation/android-sdk/config) y la [guía de restricciones de claves](https://developers.google.com/maps/api-security-best-practices).
2. En la raíz del proyecto, crea `secrets.properties` (está excluido de Git) con:

   ```properties
   MAPS_API_KEY=TU_CLAVE_RESTRINGIDA
   ```

3. Sincroniza y compila el proyecto. `local.defaults.properties` contiene solo `DEFAULT_API_KEY` para que el proyecto pueda configurarse sin guardar una clave real en el repositorio; el mapa incrustado no mostrará mosaicos válidos hasta configurar una clave real. El Secrets Gradle Plugin lee el archivo local y pasa el valor al manifest ([documentación](https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin)).

4. En Google Cloud, registra la huella SHA-1 de cada certificado usado (debug y release, según corresponda) y limita la clave a **Maps SDK for Android**. No compartas `secrets.properties` ni publiques la clave en logs o documentación.

Las API keys incluidas en una app Android pueden extraerse del APK. Las restricciones de aplicación y de API en Google Cloud siguen siendo necesarias.
