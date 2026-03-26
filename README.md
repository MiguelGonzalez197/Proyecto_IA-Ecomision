# EcoSort MVP Android

Aplicacion Android MVP para clasificar residuos con camara en tiempo real y sugerir:

- caneca blanca
- caneca negra
- caneca verde

La app no le pregunta al usuario si el residuo esta limpio o sucio. En su lugar:

1. detecta internamente la falta de evidencia
2. decide que vista falta
3. pide una nueva vista concreta del objeto
4. reanaliza automaticamente al capturar esa vista

## Estado actual

El proyecto ya compila y genera APK de debug.

APK generado:

- `dist/EcoSort-debug.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

Comando verificado en este entorno:

```powershell
.\gradlew.bat assembleDebug
```

## Stack real del MVP

- Kotlin
- Android Gradle Plugin 8.5.2
- Jetpack Compose
- CameraX con `LifecycleCameraController`
- ML Kit Object Detection para bounding boxes en tiempo real
- ML Kit Image Labeling para apoyo de clasificacion del objeto seleccionado
- Room para catalogo local, reglas y futuro historial
- MVVM con `StateFlow`

## Arquitectura implementada

La app se construyo con una arquitectura modular dentro del modulo `app`, separando:

- `analysis/`: clasificacion heuristica, incertidumbre, reglas y vistas guiadas
- `camera/`: detector en vivo, analyzer, mapeo de coordenadas, conversion de frame
- `data/db/`: Room, DAOs y entidades
- `repository/`: seed del catalogo y reglas desde `assets`
- `ui/`: pantalla principal, overlay de deteccion y panel de resultados
- `model/`: modelos de dominio

## Estructura de carpetas

```text
Proyecto_IA-Ecomision/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── bin_rules.json
│       │   └── waste_catalog.json
│       ├── java/com/ecomision/ecosort/
│       │   ├── MainActivity.kt
│       │   ├── EcoSortApplication.kt
│       │   ├── AppContainer.kt
│       │   ├── analysis/
│       │   ├── camera/
│       │   ├── data/db/
│       │   ├── model/
│       │   ├── repository/
│       │   └── ui/
│       └── res/
├── dist/
│   └── EcoSort-debug.apk
├── docs/
│   └── arquitectura_mvp_android.md
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── gradlew
├── gradlew.bat
└── README.md
```

## Flujo funcional del MVP

1. La camara abre en tiempo real.
2. ML Kit detecta objetos potenciales y dibuja `bounding boxes`.
3. El usuario toca uno.
4. La app toma el `bounding box` seleccionado y lo aisla por recorte.
5. El crop se analiza con:
   - `WasteHeuristicClassifier`
   - ML Kit Image Labeling
   - heuristicas visuales de limpieza, liquido, restos, grasa, humedad, organicidad
6. `BinRuleEngine` decide caneca.
7. `UncertaintyEngine` decide si la evidencia alcanza.
8. Si no alcanza, `GuidedViewPlanner` pide una vista concreta.
9. El usuario muestra esa vista y pulsa `Analizar vista`.
10. La app reanaliza y confirma o mantiene advertencia.

## Lo que reconoce actualmente el sistema

La base local inicial incluye estas categorias:

- botella plastica
- envase plastico
- bolsa plastica
- papel
- carton
- caja de pizza
- vaso desechable
- vaso de cafe
- lata
- frasco de vidrio
- Tetra Pak
- empaque metalizado
- bandeja de icopor
- recipiente de domicilio
- servilleta
- residuo organico
- papel aluminio
- cubiertos desechables
- tapa

## Que residuos reconoce mejor hoy

El MVP reconoce mejor los residuos con forma clara y etiquetas visuales genericas fuertes:

- botellas
- latas
- vasos
- cajas
- papel/carton relativamente limpio
- organicos visibles
- bandejas/recipientes de comida bien encuadrados

## Que residuos son mas ambiguos hoy

Estos casos siguen siendo dificiles en la version actual:

- vaso de cafe oscuro por dentro
- botella transparente con pocas gotas
- carton con grasa leve
- servilleta casi limpia vs usada
- Tetra Pak limpio por fuera pero con liquido dentro
- aluminio con aceite fino
- bolsa con residuos adheridos pequenos
- frascos sucios con reflejos
- empaques arrugados o deformados
- residuos parcialmente visibles

## En cuales casos necesita vistas guiadas

El sistema suele pedir vistas guiadas cuando ocurre alguna de estas condiciones:

- la confianza final queda baja
- falta una vista critica como interior o ambas caras
- la imagen esta borrosa
- la mano tapa parte del objeto
- el fondo confunde el analisis
- la categoria base parece reciclable, pero el estado visual no es concluyente

## Vistas guiadas que sabe pedir por categoria

### Vasos y recipientes

- mostrar interior
- inclinar el vaso
- acercar la camara al borde
- mostrar fondo interno

### Carton y papel

- mostrar ambas caras
- mostrar el otro lado
- acercar a esquinas
- mostrar grasa o manchas

### Botellas y frascos

- mostrar interior
- mostrar abertura o tapa
- mostrar base
- girar lentamente el envase

### Latas

- mostrar abertura superior
- mostrar base
- mostrar interior

### Empaques de comida

- mostrar lado interno
- mostrar zonas con salsa o grasa
- abrir o extender el empaque

### Bolsas plasticas

- extender
- mostrar ambas caras
- separar del fondo

### Bandejas e icopor

- mostrar interior
- mostrar base
- acercar zonas con restos

### Organicos

- mostrar textura
- mostrar volumen completo
- separar de otros objetos

### Objetos ocluidos

- retirar la mano
- centrar el objeto
- acercar la camara
- reducir fondo irrelevante

## Reglas actuales para decidir blanca, negra y verde

Las reglas viven en `app/src/main/assets/bin_rules.json`.

Reglas principales:

- organico claro -> verde
- liquido visible -> negra
- restos fuertes de comida -> negra
- grasa visible en carton de pizza -> negra
- servilleta usada o humeda -> negra
- limpio y seco -> blanca
- Tetra Pak limpio y drenado -> blanca
- si falta evidencia fuerte -> negra conservadora

## Implementacion provisional vs reemplazable

El proyecto esta listo para usar, pero hoy mezcla piezas reales con piezas provisionales controladas:

- Deteccion en tiempo real:
  - real
  - usa ML Kit Object Detection
- Seleccion tactil:
  - real
  - overlay Compose con hit testing sobre los boxes
- Aislamiento del objeto:
  - realista pero simple
  - recorte por `bounding box` con padding
- Clasificacion:
  - provisional pero funcional
  - `WasteHeuristicClassifier` combina ML Kit Image Labeling + rasgos visuales
- Reglas:
  - reales y locales
  - `Room` + JSON seed
- Incertidumbre:
  - real
  - `UncertaintyEngine`
- Vistas guiadas:
  - reales
  - `GuidedViewPlanner`

Puntos de reemplazo pensados para una version con modelo entrenado:

- `MlKitWasteObjectDetector`
- `BoundingBoxIsolationEngine`
- `WasteHeuristicClassifier`

## Base local inicial

Archivos:

- `app/src/main/assets/waste_catalog.json`
- `app/src/main/assets/bin_rules.json`

La primera ejecucion los carga a Room mediante:

- `CatalogRepositoryImpl`

## Interfaz actual

La UI esta hecha en Compose y apunta a una estetica:

- limpia
- minimalista
- clara para pruebas reales
- acento verde
- panel inferior elegante y compacto

## Como abrir en Android Studio

1. Abre Android Studio.
2. Selecciona `Open`.
3. Abre la carpeta raiz del repositorio:
   - `E:\Proyecto-DiseñoHTML\Proyecto_IA-Ecomision`
4. Espera a que sincronice Gradle.
5. Si Android Studio pide SDK, selecciona el SDK instalado.

## Como compilar APK

### Debug APK

```powershell
.\gradlew.bat assembleDebug
```

Salida:

- `app/build/outputs/apk/debug/app-debug.apk`
- copia util: `dist/EcoSort-debug.apk`

### Desde Android Studio

1. `Build`
2. `Build APK(s)`

## Limitaciones reales del MVP

- El detector actual es generico y no exclusivo para residuos.
- La clasificacion fina aun no usa un modelo entrenado propio del dominio.
- La segmentacion actual es por recorte, no por mascara semantica completa.
- Los transparentes y reflejos siguen siendo dificiles.
- La contaminacion no visible no puede inferirse bien.
- El detector de ML Kit puede requerir descarga de modelo en primer uso.

## Ruta clara para mejorar el modelo despues

### Fase de datos

Construir dataset local con:

- objetos limpios
- objetos contaminados
- varias vistas del mismo residuo
- fondos reales con manos, mesas, ropa y sombras

### Fase de modelo

1. entrenar detector custom para familias de residuos
2. entrenar segmentador del objeto seleccionado
3. entrenar clasificador multi-head:
   - categoria
   - material
   - limpieza
   - liquido
   - grasa
   - restos de comida
4. exportar a TFLite

### Fase de integracion

Reemplazar:

- `MlKitWasteObjectDetector` por detector TFLite/YOLO mobile
- `WasteHeuristicClassifier` por clasificador TFLite multi-head
- `BoundingBoxIsolationEngine` por segmentacion real

## Observaciones tecnicas

- El proyecto ya tiene `gradle wrapper`.
- Para este workspace se agrego `android.overridePathCheck=true` porque la ruta contiene `Diseño`.
- Se uso `local.properties` local para compilar con:
  - `sdk.dir=C:\\Users\\juand\\AppData\\Local\\Android\\Sdk`

## Documentacion adicional

- Arquitectura Fase 1:
  - `docs/arquitectura_mvp_android.md`
- Pipeline ML Fase 3:
  - `docs/fase3_pipeline_ml.md`
  - `ml/README.md`
