# Fase 4 - Diseno Visual y UX

## Branding
- Nombre oficial: `Ecomisión - Clasificador`
- Firma: `by fourwenteee`
- Integracion: splash, onboarding, encabezado principal, panel de resultado y pantalla About

## Sistema visual
- Estilo: minimalista, premium, tecnologico y ecologico
- Base: `Material 3` custom con tokens propios
- Paleta principal:
  - `EcoGreen` para acciones y estados positivos
  - `EcoGreenLight` para realces suaves
  - `EcoGreenDark` para profundidad y splash
  - neutros suaves para fondos y paneles
  - `WarningAmber` para incertidumbre
  - `ErrorRed` para error minimo

## Tipografia
- Familia: sans-serif moderna
- Titulos: `Bold`
- Subtitulos: `Medium`
- Texto base: `Regular`
- Firma: `Light`

## Componentes reutilizables
- `EcoWordmark`
- `EcoPanel`
- `EcoPrimaryButton`
- `EcoSecondaryButton`
- `EcoChip`
- `EcoConfidenceMeter`
- `EcoLoader`
- `EcoSectionCard`
- `ResultPanel`
- `DetectionOverlay`

## Pantallas implementadas
- Splash con degradado verde y entrada suave
- Onboarding de 3 pasos
- Camara principal redisenada
- Estados visuales de deteccion, seleccion, analizando, incertidumbre, escaneo guiado y resultado
- Historial
- About

## Guardrails de rendimiento
- Sin blur pesado
- Sin overlays complejos con recortes dinamicos
- Animaciones contenidas entre `200ms` y `620ms` solo en elementos pequenos
- Loader ligero solo cuando `isAnalyzing == true`
- Overlay de deteccion con mapeo memorizado
- Atenuacion simple de cajas no seleccionadas en lugar de efectos costosos
- Analisis de camara conservado fuera del hilo principal
- Frecuencia de analisis ya limitada por `CameraFrameAnalyzer`

## Verificacion ejecutada
- `:app:compileDebugKotlin`
- `:app:assembleDebug`
- `:app:lintDebug`

## Nota
La validacion real de FPS, termica y consumo en CPU/GPU requiere prueba en dispositivo fisico o emulador con `Profile GPU Rendering`, `JankStats` o `Macrobenchmark`. En este entorno se dejaron listas las decisiones de UI para favorecer estabilidad y minimizar carga.
