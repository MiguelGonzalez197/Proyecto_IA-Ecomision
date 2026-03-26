# Fase 3 - Datos, entrenamiento, evaluacion y despliegue del modelo

## 1. Arquitectura de entrenamiento recomendada

### Arquitectura elegida

Pipeline de varios modelos y una capa final de decision:

1. detector de residuos en escena
2. crop o segmentacion del objeto seleccionado
3. clasificador multitarea del objeto
4. fusion multivista
5. incertidumbre + active vision
6. motor de reglas final

### Por que no un solo modelo end-to-end

No conviene un unico modelo para todo porque:

- el detector y el clasificador tienen escalas distintas
- la decision final depende de estado visual y vistas faltantes
- el active vision necesita saber que slot falta, no solo una clase final
- en movil es mas facil optimizar modelos pequenos especializados

### Recomendacion concreta

- detector:
  - YOLO nano/small exportable a TFLite
- clasificador multitarea:
  - MobileNetV3-Small
- decision final:
  - reglas + señales del clasificador

## 2. Esquema del dataset

### Unidad primaria

La unidad primaria del dataset no es solo la imagen. Es:

- `split_group`
- `capture_session_id`
- `object_id`
- `view_slot`

Esto permite:

- evitar leakage
- agrupar vistas del mismo objeto
- evaluar secuencias guiadas

### Campos principales

#### Nivel imagen

- `image_id`
- `image_path`
- `width`
- `height`
- `capture_session_id`
- `scene_id`
- `location_id`
- `device_id`
- `lighting`
- `distance`
- `background_complexity`
- `hand_present`
- `split_group`
- `tags`

#### Nivel instancia

- `instance_id`
- `object_id`
- `bbox_xyxy`
- `polygon` opcional
- `object_class`
- `material_primary`
- `material_secondary`
- `state_cleanliness`
- `state_wetness`
- `state_food_residue`
- `state_liquid`
- `deformation`
- `visible_part`
- `interior_visible`
- `food_present`
- `liquid_present`
- `background_complexity`
- `occlusion`
- `hand_present`
- `target_bin`
- `ambiguity`
- `view_slot`

## 3. Clases iniciales del sistema

### Clases V1

- plastic_bottle
- plastic_container
- yogurt_cup
- ice_cream_tub
- plastic_cup
- coffee_cup
- glass_bottle
- glass_jar
- can
- cardboard
- cardboard_box
- pizza_box
- paper_sheet
- notebook
- newspaper
- napkin_clean
- napkin_used
- foam_tray
- delivery_container
- tetra_pak
- plastic_bag
- snack_bag
- multilayer_package
- aluminum_foil
- disposable_cutlery
- lid
- fruit_peel
- vegetable_scraps
- mixed_organic
- bone
- coffee_filter

## 4. Etiquetas auxiliares

- material_primary
- state_cleanliness
- state_wetness
- state_food_residue
- state_liquid
- deformation
- visible_part
- background_complexity
- occlusion
- ambiguity
- target_bin

## 5. Estrategia para recolectar datos

### Fuentes externas para bootstrap

- TACO
- ZeroWaste
- TrashNet

### Datos propios obligatorios

- campus o casas reales
- varias mesas, fondos, paredes y canecas
- diferentes celulares
- tomas con manos
- residuos limpios
- residuos contaminados
- vistas interiores y exteriores
- secuencias guiadas del mismo objeto

### Protocolo de captura recomendado

Por cada objeto fisico:

1. `outer_full`
2. `inner_view` si aplica
3. `opening_neck` si aplica
4. `bottom_view` si aplica
5. `back_side` o `both_faces`
6. un caso con fondo complejo
7. un caso con mano

### Casos ambiguos que deben sobrerrepresentarse

- vaso de cafe con espuma
- botella transparente con gotas
- pizza box con grasa leve
- Tetra Pak cerrado con liquido
- aluminio con aceite
- servilleta casi limpia
- bolsa plastica con residuos adheridos
- organico mezclado con empaque

## 6. Sesgos a evitar

### Atajos tontos que el modelo podria aprender

- fondo de cocina = organico
- mano = residuo
- mesa de madera = carton
- color oscuro = negra
- botella transparente = limpia

### Contramedidas

- balancear fondos por clase
- capturar todas las clases en varios entornos
- incluir negativos con manos sin residuo
- incluir residuos limpios y sucios en el mismo tipo de mesa
- incluir liquidos transparentes y envases limpios transparentes
- usar crops y no solo imagen completa para clasificador
- medir accuracy por `background_complexity`, `hand_present` y `occlusion`
- limitar la contribucion del contexto en el clasificador

## 7. Herramientas de anotacion recomendadas

- CVAT:
  - bounding boxes
  - polygons
  - atributos por instancia
- Label Studio:
  - anotacion flexible de atributos
- Roboflow:
  - rapido para bootstrap y conversiones

### Recomendacion practica

Usar CVAT como fuente maestra y exportar a `manifest JSONL` propio.

## 8. Balanceo y split

### Split recomendado

- train: 70%
- val: 15%
- test: 15%

### Regla critica

No dividir por imagen suelta.
Dividir por `split_group`, que puede representar:

- el mismo objeto fisico
- la misma sesion de captura
- la misma escena critica

### Por que

Si la vista 1 de una botella cae en train y la vista 2 del mismo objeto cae en test,
la evaluacion se infla artificialmente.

## 9. Estrategia de entrenamiento

### Detector

- dataset: imagen completa + bounding boxes
- objetivo:
  - localizar residuos candidatos
- metrica principal:
  - `mAP50-95`

### Clasificador multitarea

- dataset: crops del objeto
- heads:
  - `object_class`
  - `material_primary`
  - `state_cleanliness`
  - `state_wetness`
  - `state_food_residue`
  - `state_liquid`
  - `target_bin`

### Augmentations recomendadas

- cambio de brillo y contraste
- ruido leve
- desenfoque moderado
- pequenas rotaciones
- flips horizontales cuando no rompan semantica
- crops con padding variable
- fondos complejos

### Augmentations a evitar

- deformaciones irreales del objeto
- color jitter extremo que simule suciedad falsa
- recortes que borren la parte critica

## 10. Estrategia de incertidumbre

### Señales base

- confianza maxima de la caneca
- margen entre top-1 y top-2
- entropia normalizada
- desacuerdo entre vistas
- slots requeridos faltantes

### Regla operativa

Pedir nueva vista si pasa cualquiera:

- `confidence < 0.80`
- `margin < 0.22`
- `entropy > 0.58`
- `disagreement > 0.30`
- faltan `required_view_slots`

## 11. Mapeo incertidumbre -> vista faltante

### Ejemplos

- plastic_bottle:
  - falta `opening_neck` -> pedir abertura
- coffee_cup:
  - falta `inner_view` -> pedir interior
- pizza_box:
  - falta `inner_view` -> pedir parte interna
- plastic_bag:
  - falta `unfolded_view` -> pedir extender bolsa
- fruit_peel:
  - falta `close_texture` -> pedir textura

### Priorizacion

1. arreglar blur u oclusion
2. completar slot critico
3. pedir vista de mayor ganancia de informacion

## 12. Fusion multivista

### Recomendacion

- para heads probabilisticos:
  - media geometrica ponderada
- para señales de contaminacion:
  - maximo
- para calidad:
  - promedio

### Beneficio

Si una vista confirma liquido o restos, la decision final no debe diluirse.

## 13. Evaluacion rigurosa

### Detector

- `mAP50`
- `mAP50-95`
- precision y recall por clase
- error en escenas con clutter

### Clasificador

- accuracy por head
- macro-F1 por head
- confusion matrix por head
- accuracy por clase
- accuracy por `ambiguity`
- accuracy por `background_complexity`
- accuracy por `occlusion`

### Active vision

- accuracy de caneca con una sola vista
- accuracy de caneca tras vistas guiadas
- tasa de solicitud de vistas
- tasa de solicitud justificada
- overconfident error rate

### Metricas de negocio

- bin accuracy final
- false white rate
- false green rate
- ambiguity calibration

## 14. Exportacion a movil

### Formato recomendado

- detector: TFLite
- clasificador: TFLite

### Cuantizacion

- detector:
  - INT8 para CPU
- clasificador:
  - dynamic range o INT8 segun precision

### Objetivo de tamaño

- detector: 8 a 15 MB
- clasificador: 5 a 12 MB

### Objetivo de latencia

- detector:
  - 20 a 45 ms por frame en gama media
- clasificador:
  - 10 a 25 ms por crop

## 15. Integracion con Android

### Flujo recomendado

1. CameraX obtiene frame
2. detector TFLite genera cajas
3. usuario toca un objeto
4. crop o mascara del objeto
5. clasificador TFLite multitarea
6. fusion con vistas previas
7. incertidumbre y active vision
8. motor de reglas

### Contrato de integracion

- taxonomia exportada desde `ml/`
- reglas exportadas desde `ml/`
- modelos en `app/src/main/assets/models/`

## 16. Limitaciones reales

- la suciedad no visible seguira siendo dificil
- transparentes y reflejos siguen costando
- algunas reglas dependen del municipio
- el modelo puede seguir dudando en mixtos complejos
- la calidad de segmentacion impacta mucho la clasificacion

## 17. Riesgos del modelo

- sesgo de fondo
- sesgo por dispositivo
- sesgo por iluminacion
- clases raras con pocos ejemplos
- desalineacion entre detector y clasificador
- etiquetas inconsistentes de “limpio” vs “ligeramente sucio”

## 18. Roadmap por versiones

### Version 1

- detector basico
- clasificador multitarea reducido
- pocas clases
- reglas simples
- sin segmentacion avanzada

### Version 2

- mas clases
- mejor head de contaminacion
- mejores vistas interiores
- calibracion de incertidumbre
- dataset mas grande de casos sucios

### Version 3

- multivista mas inteligente
- segmentacion del objeto seleccionado
- mejor manejo de mezclas y residuos parcialmente visibles
- mas robustez a clutter
- active learning desde errores reales

