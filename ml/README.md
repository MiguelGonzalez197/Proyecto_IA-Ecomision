# EcoSort ML Pipeline

Modulo de datos, entrenamiento, evaluacion y exportacion para los modelos de
vision por computador de EcoSort.

## Objetivo

Este modulo prepara y entrena tres capacidades principales:

1. detector de residuos en escena
2. clasificador multitarea del residuo seleccionado
3. logica de incertidumbre + fusion multivista + exportacion a Android

## Arquitectura de entrenamiento recomendada

### Opcion elegida

Pipeline de varios modelos:

1. `Detector`:
   - modelo rapido on-device
   - detecta residuos potenciales en imagen completa
   - produce `bounding boxes`

2. `Crop/segment helper`:
   - en V1 se usa crop por caja
   - en V2/V3 se reemplaza por segmentacion del objeto seleccionado

3. `State classifier multitarea`:
   - recibe el crop del objeto seleccionado
   - predice:
     - clase del objeto
     - material principal
     - limpieza
     - humedad
     - liquido
     - comida/restos
     - deformacion
     - caneca objetivo inicial

4. `Uncertainty + Active Vision`:
   - detecta falta de evidencia
   - pide una vista faltante concreta
   - fusiona varias vistas

5. `Rule engine`:
   - convierte predicciones y estados en decision final de caneca

### Justificacion

Para movil conviene esta separacion porque:

- el detector corre sobre la escena completa y debe ser muy rapido
- el clasificador puede ser mas fino y operar solo sobre un objeto
- la explicabilidad mejora mucho
- la incertidumbre puede basarse en slots de evidencia faltantes
- permite iterar el clasificador sin reentrenar el detector

## Estructura del dataset

El dataset canonico se guarda como `manifest JSONL`.

Cada linea representa una imagen capturada y contiene:

- metadatos de escena
- ruta de imagen
- contexto de iluminacion/fondo
- lista de instancias anotadas

Cada instancia contiene:

- `object_id`
- `bbox_xyxy`
- `polygon` opcional
- `object_class`
- `material_primary`
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
- `target_bin`
- `ambiguity`
- `view_slot`

## Flujo recomendado

1. capturar imagenes y videos
2. anotar en formato JSONL comun
3. validar contra la taxonomia
4. hacer split por `object_id` para evitar leakage
5. exportar:
   - dataset YOLO del detector
   - dataset de crops para clasificador multitarea
6. entrenar
7. evaluar
8. exportar modelos a movil
9. generar assets Android desde la misma taxonomia y reglas

## Scripts principales

- `scripts/init_dataset_manifest.py`
- `scripts/validate_dataset.py`
- `scripts/make_splits.py`
- `scripts/build_detector_dataset.py`
- `scripts/build_classifier_dataset.py`
- `scripts/train_detector.py`
- `scripts/train_state_classifier.py`
- `scripts/evaluate_classifier.py`
- `scripts/evaluate_active_vision.py`
- `scripts/export_models.py`
- `scripts/export_android_assets.py`

## Ejecucion sugerida

### 1. Inicializar carpetas y manifest ejemplo

```powershell
python ml/scripts/init_dataset_manifest.py --with-sample
```

### 2. Validar anotaciones

```powershell
python ml/scripts/validate_dataset.py `
  --manifest ml/data/raw/master_manifest.jsonl `
  --taxonomy ml/configs/taxonomy/waste_taxonomy.v1.yaml `
  --report-json ml/reports/validation_report.json
```

### 3. Crear splits sin leakage

```powershell
python ml/scripts/make_splits.py `
  --manifest ml/data/raw/master_manifest.jsonl `
  --out-map ml/data/interim/split_map.json `
  --out-summary ml/reports/split_summary.json
```

### 4. Generar dataset YOLO del detector

```powershell
python ml/scripts/build_detector_dataset.py `
  --manifest ml/data/raw/master_manifest.jsonl `
  --split-map ml/data/interim/split_map.json `
  --taxonomy ml/configs/taxonomy/waste_taxonomy.v1.yaml `
  --output-dir ml/data/processed/detector_v1
```

### 5. Generar crops del clasificador

```powershell
python ml/scripts/build_classifier_dataset.py `
  --manifest ml/data/raw/master_manifest.jsonl `
  --split-map ml/data/interim/split_map.json `
  --output-dir ml/data/processed/classifier_v1
```

### 6. Entrenar detector

```powershell
python ml/scripts/train_detector.py `
  --config ml/configs/experiments/detector_v1.yaml
```

### 7. Entrenar clasificador multitarea

```powershell
python ml/scripts/train_state_classifier.py `
  --config ml/configs/experiments/state_classifier_v1.yaml `
  --taxonomy ml/configs/taxonomy/waste_taxonomy.v1.yaml
```

### 8. Evaluar clasificador

```powershell
python ml/scripts/evaluate_classifier.py `
  --model ml/reports/models/state_classifier_v1/best_model.keras `
  --csv ml/data/processed/classifier_v1/test.csv `
  --taxonomy ml/configs/taxonomy/waste_taxonomy.v1.yaml `
  --report-dir ml/reports/eval_classifier_v1
```

### 9. Evaluar active vision

```powershell
python ml/scripts/evaluate_active_vision.py `
  --predictions-jsonl ml/reports/eval_classifier_v1/instance_predictions.jsonl `
  --metadata-csv ml/data/processed/classifier_v1/test.csv `
  --taxonomy ml/configs/taxonomy/waste_taxonomy.v1.yaml `
  --report-json ml/reports/eval_active_vision_v1.json
```

### 10. Exportar modelos y assets Android

```powershell
python ml/scripts/export_models.py `
  --config ml/configs/export/android_export_v1.yaml

python ml/scripts/export_android_assets.py `
  --export-config ml/configs/export/android_export_v1.yaml `
  --taxonomy ml/configs/taxonomy/waste_taxonomy.v1.yaml `
  --rules ml/configs/rules/bin_rules.co_generic_v1.yaml
```

## Carpetas

```text
ml/
├── configs/
├── data/
│   ├── raw/
│   ├── interim/
│   └── processed/
├── exports/
├── reports/
├── scripts/
└── src/ecosort_ml/
```

## Recomendacion de entorno

Para entrenamiento real:

- Python 3.11
- CUDA si hay GPU NVIDIA

Este repositorio puede editarse con otras versiones de Python, pero el
entrenamiento pesado se recomienda ejecutarlo en un entorno virtual dedicado.

## Dependencias recomendadas

Ver:

- `ml/requirements.txt`
- `ml/pyproject.toml`

## Artefactos esperados

### Detector

- checkpoint `.pt`
- export `.tflite`
- reporte de mAP

### Clasificador multitarea

- modelo `.keras`
- export `.tflite`
- reporte por heads

### Export para Android

- `detector.tflite`
- `state_classifier.tflite`
- `taxonomy.android.json`
- `bin_rules.android.json`
