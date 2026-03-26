# Arquitectura MVP Android - Clasificacion de residuos con camara en tiempo real

## 0. Punto de partida actual

El repositorio hoy contiene un baseline de clasificacion por imagen en
`ProyectoEcomision.ipynb`, basado en `TensorFlow` + `MobileNetV2` + salida de
3 clases.

Ese baseline sirve como Fase 0, pero no cubre:

- deteccion multiple en escena
- seleccion por toque
- segmentacion del objeto
- analisis de estado visual
- seguimiento temporal
- incertidumbre interna
- guias de "vista faltante"
- motor de reglas explicable

La arquitectura propuesta parte de ese baseline y lo convierte en una app Android instalable, offline-first y escalable.

## 1. Arquitectura general

### Objetivo tecnico

Construir una app Android con inferencia mayormente on-device que:

1. detecte multiples residuos en tiempo real
2. permita seleccionar uno por toque
3. aisle visualmente el objeto
4. infiera familia de residuo, material y estado visual
5. acumule evidencia de varias vistas
6. decida caneca blanca/negra/verde con reglas explicables
7. pida nuevas vistas solo cuando la evidencia sea insuficiente

### Estilo de arquitectura

- App: Clean Architecture ligera
- UI: MVVM + UDF
- Inferencia: pipeline modular orientado a eventos
- Decision: reglas declarativas + puntajes probabilisticos
- Datos: Room para persistencia local + JSON seed para taxonomias y reglas
- Escalabilidad: separacion clara entre percepcion, decision y politica de vistas

### Capas

1. UI Layer
2. Camera/Realtime Layer
3. Perception Layer
4. Evidence Layer
5. Decision Layer
6. Data Layer
7. Telemetry/ModelOps Layer

## 2. Stack 

| Capa | Tecnologia recomendada | Motivo |
|---|---|---|
| Lenguaje | Kotlin | estandar moderno de Android |
| UI | Jetpack Compose | rapidez para MVP, overlays y estado reactivo |
| Navegacion | Navigation Compose | flujo simple y desacoplado |
| Camara | CameraX `Preview` + `ImageAnalysis` | captura estable en tiempo real |
| Concurrencia | Coroutines + Flow | control fino de streaming y estado |
| DI | Hilt | inyeccion limpia y testeable |
| Persistencia estructurada | Room | reglas, historial, evidencias, auditoria |
| Config simple | DataStore | preferencias, thresholds, perfil local |
| Vision on-device | MediaPipe Tasks + LiteRT | buen soporte Android para inferencia edge |
| Deteccion | MediaPipe Object Detector con modelo custom | integracion directa en Android |
| Segmentacion del objeto seleccionado | MediaPipe Interactive Segmenter sobre frame congelado | ideal para toque sobre objeto |
| Clasificacion de residuo/estado | modelo custom LiteRT multi-head | separa tipo/material/estado |
| Tracking | Kalman + IoU tracker ligero | estable y barato para MVP |
| Observabilidad | Timber + Firebase Crashlytics opcional | trazabilidad |
| Analitica futura | Firebase Analytics o backend propio | error analysis y mejora continua |

## 3. Modulos del proyecto

### Gradle modules

| Modulo | Responsabilidad |
|---|---|
| `:app` | arranque, navegacion, DI global |
| `:core:camera` | CameraX, frame pipeline, conversiones, coordenadas |
| `:core:ml` | wrappers de modelos, delegates, benchmarking |
| `:core:ui` | componentes Compose, overlays, tema, estados comunes |
| `:data:local` | Room, DAOs, seed JSON, DataStore |
| `:data:rules` | parser y repositorio de taxonomia/reglas |
| `:domain:vision` | casos de uso de deteccion, segmentacion, tracking |
| `:domain:evidence` | fusion temporal, slots de evidencia, cobertura de vistas |
| `:domain:decision` | scoring, incertidumbre, motor de reglas, explicaciones |
| `:feature:scanner` | pantalla principal de camara y seleccion |
| `:feature:guidedscan` | guias visuales, prompts de nuevas vistas |
| `:feature:result` | pantalla final con caneca, razon, evidencia y warning |
| `:feature:history` | historial local de analisis y debug de errores |

### Componentes principales

- `CameraFrameOrchestrator`
- `WasteObjectDetector`
- `SelectedObjectTracker`
- `TapToMaskSegmenter`
- `WasteMultiHeadClassifier`
- `StateCueExtractor`
- `EvidenceAccumulator`
- `UncertaintyEngine`
- `NextBestViewPlanner`
- `BinRuleEngine`
- `ExplanationBuilder`

## 4. Diagrama logico en texto

```text
CameraX Preview + ImageAnalysis
    -> Frame Sampler / Preprocessor
    -> Object Detector (multiples residuos)
    -> Tracker (IDs temporales)
    -> Overlay (bounding boxes)
    -> Usuario toca un objeto
    -> Interactive Segmenter sobre frame/ROI seleccionado
    -> Masked Crop Builder
    -> Multi-Head Classifier
         -> familia de residuo
         -> material
         -> estado visual
         -> cues de contaminacion/liquido/humedad/occlusion
    -> Evidence Accumulator
         -> fusion temporal
         -> coverage por vista
    -> Uncertainty Engine
         -> decision segura? si/no
    -> Si NO:
         -> Next Best View Planner
         -> instruccion guiada
         -> captura nueva vista
         -> reanalisis
    -> Bin Rule Engine
         -> blanca / negra / verde
    -> Explanation Builder
         -> razon breve
         -> evidencia usada
         -> warning si persiste ambiguedad
```

## 5. Pipeline paso a paso

### 5.1 Tiempo real sin objeto seleccionado

1. CameraX entrega frames RGBA/YUV.
2. Se analiza 1 de cada N frames para mantener FPS.
3. El detector devuelve cajas de residuos potenciales.
4. El tracker asigna `track_id` y estabiliza cajas entre frames.
5. El overlay dibuja bounding boxes con score y color neutro.
6. Se priorizan residuos mas centrados, grandes y menos ocluidos.

### 5.2 Cuando el usuario toca un residuo

1. Se toma el `track_id` activo mas cercano al toque.
2. Se congela un frame de alta calidad del ROI.
3. El `Interactive Segmenter` recibe un punto/ROI sobre el objeto.
4. Se genera mascara del objeto y se atenúa el fondo.
5. Se crea un `masked crop` con padding contextual pequeno.
6. El clasificador multi-head infiere:
   - familia del objeto
   - material dominante
   - subtipo
   - indicadores de limpieza/suciedad
   - presencia de restos de comida
   - presencia de liquido/espuma
   - humedad aparente
   - grado de oclusion
7. El acumulador temporal fusiona resultados entre cuadros/vistas.
8. El motor de incertidumbre decide si ya hay evidencia suficiente.
9. Si falta evidencia, el planificador de vistas genera la siguiente instruccion.
10. Se vuelve a analizar hasta confirmar o alcanzar el maximo de rondas.
11. Se emite resultado final con explicacion.

### 5.3 Salida del sistema

- residuo detectado
- material estimado
- caneca sugerida
- score de confianza
- razon breve
- vistas/evidencias usadas
- advertencia si sigue ambiguo

## 6. Estrategia de percepcion

### 6.1 Deteccion

**MVP:** detector custom compatible con MediaPipe Object Detector.

Clases del detector:

- no deben ser "blanca/negra/verde"
- deben ser familias visuales de residuos
- deben tolerar clutter y multiples objetos

Familias visuales sugeridas para deteccion:

- botella plastica
- envase plastico rigido
- vaso desechable
- vaso de cafe
- bolsa plastica
- tapa
- lata
- botella de vidrio
- frasco de vidrio
- papel
- carton
- caja de pizza
- servilleta/papel tisue
- bandeja de icopor
- recipiente de domicilio
- empaque multicapa
- tetra pak
- aluminio
- cubiertos desechables
- residuo organico

### 6.2 Segmentacion / aislamiento

**MVP realista:** usar segmentacion interactiva sobre el objeto tocado, no segmentacion total por frame.

Ventajas:

- aprovecha el toque del usuario sin pedirle juicio semantico
- reduce mucho el ruido de fondo
- simplifica el problema de varios objetos
- permite extraer crops limpios para la clasificacion final

### 6.3 Clasificacion multi-head

En vez de un unico clasificador de canecas, usar un modelo multi-head:

- Head A: familia/subtipo del residuo
- Head B: material principal
- Head C: estado visual
- Head D: cues binarios/multietiqueta

#### Heads

| Head | Salida |
|---|---|
| `waste_family` | botella, lata, carton, servilleta, organico, etc. |
| `material` | PET, HDPE, PP, papel, carton, vidrio, metal, organico, multicapa, EPS |
| `state_cleanliness` | limpio, ligeramente sucio, contaminado |
| `state_liquid` | vacio, gotas, liquido visible |
| `state_food_residue` | sin restos, restos leves, restos fuertes |
| `state_wetness` | seco, humedo |
| `state_occlusion` | visible, parcialmente tapado, mal visible |
| `state_view_quality` | buena, borrosa, contraluz, recorte pobre |

### 6.4 Decision final

No se decide por clase visual directa. Se decide por:

`objeto + material + estado + evidencia temporal + reglas`

Eso evita errores como:

- carton limpio vs carton grasoso
- vaso limpio vs vaso con cafe
- botella vacia vs botella con liquido

## 7. Modelo para movil

### Opcion principal para MVP

| Tarea | Modelo recomendado | Motivo |
|---|---|---|
| Deteccion | detector custom MobileNet-based exportado a `.tflite` y consumido con MediaPipe Object Detector | integracion Android directa, latencia razonable |
| Segmentacion por toque | modelo compatible con MediaPipe Interactive Segmenter | aislamiento practico del objeto seleccionado |
| Clasificacion multi-head | `MobileNetV3-Small` o `EfficientNet-Lite0` custom en LiteRT | buen balance entre precision y latencia |

### Configuracion 

- detector:
  - entrada `320x320`
  - version `int8` para CPU o `fp16` para GPU
- clasificador:
  - entrada `224x224`
  - version `int8` si el objetivo es gama media
  - version `fp16` si se usa GPU y la calidad lo justifica

### Recopilacion Apk

Para la primera APK funcional:

- detector `int8`
- clasificador multi-head `int8`
- segmentador en `fp16` si el dispositivo soporta aceleracion estable

Eso prioriza estabilidad y bateria sobre una precision maxima temprana.

## 8. Logica interna de incertidumbre

La incertidumbre no sale de una sola probabilidad. Sale de una combinacion:

```text
U =
  baja separacion entre clases
  + conflicto entre reglas
  + evidencia faltante para slots criticos
  + inestabilidad temporal entre frames
  + mala segmentacion
  + oclusion / blur / contraluz
```

### Señales concretas

| Señal | Ejemplo |
|---|---|
| bajo margen de clase | botella limpia 0.44 vs botella con liquido 0.40 |
| regla en conflicto | material reciclable pero restos visibles |
| slot faltante | se ve el vaso por fuera, pero no el interior |
| alta varianza temporal | blanca en un frame, negra en otro |
| mala calidad visual | mano tapa borde o interior |
| baja calidad de mascara | mascara invade fondo o pierde parte del objeto |

### Umbrales operativos MVP

| Estado | Condicion |
|---|---|
| `Seguro` | score final >= 0.80 y sin slots criticos faltantes |
| `Requiere vista` | 0.45 a 0.79 o conflicto de reglas |
| `Ambiguo persistente` | despues de 2 a 3 vistas sigue sin resolverse |

### Politica de resolucion

1. pedir primero una vista barata y de alta ganancia
2. reanalizar
3. si persiste ambiguedad, pedir una segunda vista mas especifica
4. si aun no alcanza umbral, devolver mejor hipotesis + warning

## 9. Active vision / vistas faltantes

La app no pregunta "esta limpio?".
La app pregunta por la parte del objeto que maximiza la ganancia de informacion.

### Slots de evidencia

| Slot | Sirve para |
|---|---|
| `outer_full` | forma y clase base |
| `inner_view` | restos internos, cafe, salsa, liquido |
| `back_side` | manchas ocultas, grasa, humedad |
| `bottom_view` | escurrimientos, residuo pegado, grasa |
| `opening_neck` | botellas, latas, frascos |
| `both_faces` | papel, carton, servilleta, bolsa |
| `close_texture` | organicos, grasa, residuos adheridos |
| `unfolded_view` | bolsas y laminas flexibles |
| `separated_from_background` | ruido de fondo y oclusion |

## 10. Estrategia de vistas guiadas por tipo de objeto

| Tipo | Vistas guiadas prioritarias | Que busca confirmar |
|---|---|---|
| vaso desechable | interior, borde, fondo | cafe, espuma, azucar, liquido |
| vaso de cafe | interior, union carton-plastico, fondo | residuo adherido y mezcla de materiales |
| botella plastica | cuello/tapa, interior, base | liquido, viscosidad, suciedad |
| envase plastico rigido | interior, tapa, esquinas | salsa, grasa, comida seca |
| bolsa plastica | extenderla, ambas caras | restos pegados, humedad, mezcla |
| lata | abertura superior, interior, base | liquido, comida, aceite |
| frasco de vidrio | interior, boca, fondo | residuos, suciedad, contenido |
| botella de vidrio | cuello, interior, base | liquido o restos pegados |
| carton | cara opuesta, esquinas, close-up | grasa, humedad, moho |
| caja de pizza | interior, parte inferior, bordes | grasa fuerte y restos de queso/salsa |
| papel | ambas caras, close-up | limpieza, manchas, humedad |
| servilleta | ambas caras, close-up | uso, grasa, restos de comida |
| bandeja de icopor | interior, fondo, esquinas | salsa, aceite, grasa |
| recipiente de domicilio | interior, tapa, bisagras | comida pegada, grasa |
| empaque multicapa | interior, exterior, pliegues | residuos internos y composicion |
| tetra pak | abertura, interior, estado de drenado | liquido remanente y lavado |
| aluminio | ambas caras, close-up | grasa, comida adherida |
| cubiertos desechables | superficie completa, extremos | restos visibles y mezcla |
| residuo organico | close-up textura, otro angulo | confirmar que es alimento/organico |
| objeto ocluido por mano | separar mano, centrar, reencuadrar | eliminar falsa evidencia |
| objeto con fondo confuso | acercar, centrar, aislar | mejorar segmentacion |

### Heuristica del siguiente prompt

El siguiente prompt se elige por:

`impacto esperado en la decision - costo de captura`

Ejemplos:

- si es un vaso y falta `inner_view`, ese slot gana siempre
- si es carton y hay sospecha de grasa, gana `back_side`
- si el problema es oclusion, gana `separated_from_background`

## 11. Manejo de multiples objetos en pantalla

### Estrategia

1. detectar todos los residuos candidatos
2. asignar `track_id` a cada caja
3. mostrar bounding boxes simultaneas
4. permitir toque para fijar el objetivo
5. al seleccionar uno, disminuir opacidad del resto
6. correr segmentacion solo sobre el ROI seleccionado

### Reglas UX

- si dos cajas se solapan, seleccionar la de mayor IoU con el toque
- si el objeto seleccionado sale del cuadro, mostrar "reencuadra el objeto"
- si aparece un nuevo objeto que tapa al seleccionado, pausar clasificacion

## 12. Como ignorar fondo y ruido visual

### Capas de defensa

1. detector entrenado con escenas reales ruidosas
2. seleccion manual del objeto por toque
3. segmentacion del objeto seleccionado
4. clasificacion sobre `masked crop`, no sobre frame completo
5. descarte de frames borrosos/ocluido/contraluz
6. fusion temporal para no decidir por un solo frame malo
7. dataset negativo con manos, mesas, ropa, paredes y objetos no residuo

### Augmentations clave

- clutter de fondo
- manos sujetando residuos
- sombras fuertes
- rotacion y escalado
- motion blur
- backlight
- varios residuos juntos

## 13. Taxonomia inicial amplia de residuos

### Familias V1 reconocidas

| Grupo | Clases V1 |
|---|---|
| Plasticos rigidos | botella PET, botella HDPE, envase de limpieza, envase de yogur, recipiente plastico, tapa, vaso plastico |
| Plasticos flexibles | bolsa plastica, film, empaque flexible, bolsa metalizada |
| Papel/carton | hoja, cuaderno, revista, periodico, carton corrugado, caja, caja pizza |
| Metales | lata bebida, lata conserva, aluminio laminado, tapa metalica |
| Vidrio | botella vidrio, frasco vidrio |
| Multicapa | tetra pak, empaque multicapa de snack, sobre laminado |
| Desechables de comida | vaso cafe, plato desechable, cubiertos desechables, bandeja icopor, recipiente domicilio |
| Organicos | cascara de fruta, restos de fruta, restos de verdura, pan, arroz/pasta, huesos, borra de cafe |
| Sanitarios/no aprovechables | servilleta usada, papel tisue, papel sanitario, desecho muy contaminado |

### Atributos almacenados por clase

Cada clase debe tener:

- `id`
- `display_name`
- `family`
- `material_primary`
- `material_secondary`
- `default_bin_if_clean`
- `contamination_sensitive`
- `required_evidence_slots`
- `forbidden_states_for_white`
- `rules_profile`
- `notes_locale`

## 14. Tabla de reglas blanca / negra / verde

### Precedencia

1. organico claro
2. contaminacion fuerte
3. reciclable limpio y seco
4. default conservador

| Regla | Condicion | Salida |
|---|---|---|
| R1 | objeto organico evidente sin empaque dominante | verde |
| R2 | botella/envase/lata/frasco con liquido visible o restos internos claros | negra |
| R3 | papel/carton con grasa, comida, humedad fuerte o moho | negra |
| R4 | servilleta, papel tisue o papel sanitario usado | negra |
| R5 | plastico/metal/vidrio limpio, vacio y seco | blanca |
| R6 | papel/carton limpio, seco y no contaminado | blanca |
| R7 | tetra pak drenado, vacio y razonablemente limpio | blanca |
| R8 | tetra pak con liquido o residuos visibles | negra |
| R9 | icopor o recipiente de comida con salsa/grasa/comida adherida | negra |
| R10 | empaque multicapa muy contaminado o inseparable | negra |
| R11 | organico dentro de recipiente reciclable pero el recipiente no esta vacio | negra |
| R12 | evidencia insuficiente o conflicto persistente | negra con warning conservador |

### Nota importante

Algunos materiales cambian por municipio o gestor de reciclaje. Por eso las reglas deben versionarse por `locale_profile`, por ejemplo:

- `co_bogota_v1`
- `co_generico_v1`
- `campus_universidad_v1`

## 15. Como almacenar reglas y categorias

### Recomendacion

- Taxonomia y reglas base en JSON versionado dentro de `assets/`
- Seed a Room en la primera ejecucion
- Repositorio de reglas con version y perfil local

### Ejemplo conceptual

```json
{
  "class_id": "pizza_box",
  "family": "paper_cardboard",
  "required_evidence_slots": ["outer_full", "inner_view", "bottom_view"],
  "rules": [
    {"if": ["grease_heavy=true"], "bin": "BLACK"},
    {"if": ["clean=true", "dry=true", "food_residue=none"], "bin": "WHITE"}
  ]
}
```

### Ventaja

Agregar nuevas clases no obliga a reescribir la app; basta con:

1. nueva clase en taxonomia
2. dataset etiquetado
3. reentrenamiento del detector/clasificador
4. nuevas reglas y slots de evidencia

## 16. Dataset inicial propuesto

### Combinacion recomendada

1. Publico para bootstrap:
   - TACO
   - ZeroWaste
   - TrashNet
2. Dataset propio del proyecto:
   - residuos locales
   - canecas y entornos reales
   - objetos limpios vs contaminados
   - secuencias multi-vista guiada

### Subdatasets que hay que construir

| Dataset | Anotacion |
|---|---|
| `detector_dataset` | bounding boxes por familia |
| `segment_help_dataset` | puntos/mascaras para casos complejos |
| `classifier_dataset` | crops por clase/familia/material |
| `state_dataset` | etiquetas de limpio, sucio, liquido, grasa, humedad |
| `view_policy_dataset` | secuencias por tipo de objeto y vistas criticas |
| `negative_dataset` | manos, mesas, ropa, sombras, fondos, objetos no residuo |

### volumen MVP

- detector: 800 a 1500 imagenes por familia principal, combinando publico + propio
- clasificador/estado: 500 a 1000 crops por subtipo/estado importante
- vistas guiadas: al menos 50 a 100 secuencias por clase conflictiva

### Etiquetas minimas de estado

- limpio
- ligeramente sucio
- contaminado
- seco
- humedo
- liquido visible
- restos de comida
- ocluido
- borroso
- contraluz

## 17. Residuos reconocidos por la primera version

### Cobertura V1 recomendada

- botellas plasticas
- envases plasticos rigidos
- vasos desechables
- vasos de cafe
- bolsas plasticas
- tapas
- latas
- botellas y frascos de vidrio
- papel
- carton
- cajas de pizza
- servilletas
- bandejas de icopor
- recipientes de domicilio
- tetra pak
- empaques multicapa
- aluminio
- cubiertos desechables
- organicos comunes

## 18. Casos mas ambiguos del MVP

- carton con manchas leves vs grasa fuerte
- vaso de cafe con interior oscuro y reflejos
- botella transparente con pocas gotas
- envase plastico con salsa seca casi invisible
- tetra pak limpio por fuera pero con liquido dentro
- aluminio con aceite fino
- servilleta casi limpia vs usada
- icopor segun regla local de reciclaje
- organicos muy procesados o mezclados con empaque
- objetos transparentes o brillantes para segmentacion

## 19. Limitaciones reales del MVP

1. La camara no puede oler ni tocar; suciedad no visible seguira siendo dificil.
2. Transparentes, reflejos y brillos fuertes afectaran botellas, vidrio y liquidos.
3. La segmentacion interactiva no es video-instance segmentation completa.
4. No todo gestor local acepta exactamente los mismos materiales.
5. Objetos muy deformados o parcialmente ocultos seguiran siendo ambiguos.
6. Si el usuario no muestra la zona critica, la app no podra confirmar.
7. Algunos residuos mixtos requieren desarme fisico y eso no siempre se puede inferir.

## 20. On-device vs nube

### On-device en MVP

- camara
- deteccion
- tracking
- segmentacion del objeto seleccionado
- clasificacion multi-head
- reglas
- explicacion final
- historial local

### Escalable a futuro en backend

- entrenamiento y versionado de modelos
- active learning con ejemplos fallidos
- telemetria de errores
- sincronizacion de taxonomias y reglas por municipio
- teacher model pesado para auditoria offline
- panel de analisis de confusion y drift

### Regla de privacidad recomendada

Por defecto, no subir frames completos.
Si en el futuro se habilita mejora continua, subir solo con consentimiento:

- crop segmentado
- mascaras
- metadatos de error
- sin rostros ni fondo innecesario

## 21. Despliegue posterior como APK

### Camino recomendado

1. Android Studio crea `debug APK` para pruebas rapidas.
2. Se generan `release builds` firmadas.
3. Para distribucion interna se comparte APK firmado.
4. Para Play Store, preferir App Bundle y dejar que Play genere APKs optimizados.

### Criterios previos a release

- latencia aceptable en 2 o 3 gamas de dispositivos
- precision por clase y por bin
- stress test con clutter
- validacion de bateria y temperatura
- fallback si delegate GPU/NPU no esta disponible

## 22. Decisiones tecnicas justificadas

| Decision | Justificacion |
|---|---|
| Pipeline modular y no modelo unico end-to-end | mas explicable, depurable y escalable |
| Toque del usuario + segmentacion interactiva | resuelve mejor multiobjeto y fondo ruidoso |
| Multi-head classifier | separa tipo, material y estado, que son conceptos distintos |
| Reglas declarativas para caneca | hace visible por que un mismo objeto cambia de caneca |
| Evidencia temporal | evita decidir por un frame malo |
| Active vision por slots | traduce "duda interna" en accion concreta de camara |
| On-device first | privacidad, offline, latencia baja |
| Room + JSON seed | reglas extensibles y trazables |

## 23. Plan exacto para pasar a Fase 2

### Sprint 1 - Bootstrap Android

1. crear proyecto Android multi-modulo en Kotlin
2. integrar Compose, Hilt, CameraX, Room y MediaPipe/LiteRT
3. construir pantalla de camara con overlay y coordenadas correctas

### Sprint 2 - Deteccion y seleccion

1. preparar dataset V1 del detector
2. entrenar detector custom de familias
3. integrar inferencia en tiempo real
4. dibujar cajas
5. seleccionar por toque con `track_id`

### Sprint 3 - Segmentacion y crop limpio

1. integrar `Interactive Segmenter`
2. congelar frame al toque
3. extraer mascara
4. generar `masked crop`
5. sombrear fondo y aislar visualmente el objeto

### Sprint 4 - Clasificacion multi-head

1. definir taxonomia final V1
2. etiquetar dataset de estados visuales
3. entrenar modelo multi-head
4. exportar a LiteRT
5. medir latencia y memoria

### Sprint 5 - Decision e incertidumbre

1. implementar `EvidenceAccumulator`
2. implementar `UncertaintyEngine`
3. implementar `BinRuleEngine`
4. generar explicaciones cortas y auditables

### Sprint 6 - Vistas guiadas

1. definir slots por tipo de objeto
2. implementar `NextBestViewPlanner`
3. crear prompts visuales y de texto
4. reanalizar nuevas vistas hasta resolver o advertir

### Sprint 7 - Evaluacion y optimizacion

1. matriz de confusion por clase y por caneca
2. pruebas con escenas reales y clutter
3. pruebas con dispositivos de gama media
4. cuantizacion y benchmark CPU/GPU/NPU
5. ajuste de thresholds y reglas

### Entregable esperado de Fase 2

Una APK capaz de:

- abrir camara en vivo
- detectar multiples residuos
- seleccionar uno por toque
- segmentarlo
- pedir una vista critica cuando haga falta
- sugerir caneca con razon y confianza

## 24. Fuentes tecnicas consultadas

- Android CameraX ImageAnalysis:
  https://developer.android.com/media/camera/camerax/analyze
- Android app architecture:
  https://developer.android.com/topic/architecture
- Room:
  https://developer.android.com/training/data-storage/room
- MediaPipe Object Detector Android:
  https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector/android
- MediaPipe Image Segmenter Android:
  https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter/android
- MediaPipe Interactive Segmenter Android:
  https://ai.google.dev/edge/mediapipe/solutions/vision/interactive_segmenter/android
- LiteRT overview:
  https://ai.google.dev/edge/litert/
- LiteRT on-device inference:
  https://ai.google.dev/edge/litert/inference
- MediaPipe object detector customization:
  https://ai.google.dev/edge/mediapipe/solutions/customization/object_detector
- ZeroWaste official repo:
  https://github.com/dbash/zerowaste
- TrashNet official repo:
  https://github.com/garythung/trashnet
- TACO official toolkit:
  https://github.com/pedropro/TACO
