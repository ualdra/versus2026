# Versus — Guía de estilos Angular

> Usa este documento como contexto al generar componentes Angular para el proyecto Versus. Respeta tokens, variables y patrones definidos aquí.

---

## Identidad visual

Versus es un juego de duelos. La estética es **oscura, tensa y directa**: fondos casi negros, tipografía agresiva, acentos de color intensos para el peligro y la acción. Nada de gradientes suaves ni bordes redondeados excesivos.

**Palabras clave**: arena, combate, datos reales, velocidad, riesgo.

---

## Tokens CSS

Define estas variables en `styles.scss` o en un `:root` global. Todos los componentes deben consumirlas, nunca hardcodear colores.

```scss
:root {
  // Fondos
  --vs-bg-base:       #0d0d0f;   // fondo de página
  --vs-bg-surface:    #16161a;   // cards, paneles
  --vs-bg-elevated:   #1e1e24;   // modales, dropdowns
  --vs-bg-input:      #111115;   // inputs, selects

  // Acentos
  --vs-accent-red:    #e63946;   // peligro, vida, error
  --vs-accent-gold:   #f4c542;   // puntos, ranking, destacado
  --vs-accent-blue:   #4361ee;   // acción principal, botón primario
  --vs-accent-green:  #2ec4b6;   // correcto, éxito
  --vs-accent-purple: #7b2d8b;   // modo sabotaje

  // Texto
  --vs-text-primary:  #f0f0f2;
  --vs-text-secondary:#9898a6;
  --vs-text-muted:    #56565f;

  // Bordes
  --vs-border:        #2a2a32;
  --vs-border-focus:  #4361ee;

  // Vidas (semáforo)
  --vs-life-full:     #2ec4b6;
  --vs-life-mid:      #f4c542;
  --vs-life-low:      #e63946;

  // Timing
  --vs-transition:    150ms ease;
  --vs-transition-slow: 300ms ease;

  // Radios
  --vs-radius-sm:  4px;
  --vs-radius-md:  8px;
  --vs-radius-lg:  16px;

  // Sombras
  --vs-shadow-card: 0 2px 12px rgba(0,0,0,0.5);
  --vs-shadow-glow-red:  0 0 16px rgba(230,57,70,0.35);
  --vs-shadow-glow-blue: 0 0 16px rgba(67,97,238,0.35);
  --vs-shadow-glow-gold: 0 0 16px rgba(244,197,66,0.35);
}
```

---

## Tipografía

```scss
// _typography.scss
@import url('https://fonts.googleapis.com/css2?family=Bebas+Neue&family=IBM+Plex+Mono:wght@400;600&family=Inter:wght@400;500;600&display=swap');

// Display: títulos de pantalla, modos de juego, scores grandes
.vs-display   { font-family: 'Bebas Neue', sans-serif; letter-spacing: 0.04em; }

// Mono: números, contadores, stats, respuestas numéricas
.vs-mono      { font-family: 'IBM Plex Mono', monospace; }

// Body: texto general, etiquetas, botones
body          { font-family: 'Inter', sans-serif; }
```

| Uso | Clase / tamaño | Peso |
|-----|---------------|------|
| Título pantalla | `.vs-display` / 48–64px | — |
| Score / contador | `.vs-mono` / 32–48px | 600 |
| Nombre modo | `.vs-display` / 24px | — |
| Label botón | `Inter` / 14px | 600 |
| Texto body | `Inter` / 15px | 400 |
| Texto secundario | `Inter` / 13px | 400 |

---

## Componentes base

### Botón

```scss
// Variantes: primary | danger | ghost
.btn {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  border: none;
  border-radius: var(--vs-radius-sm);
  padding: 10px 24px;
  cursor: pointer;
  transition: opacity var(--vs-transition), box-shadow var(--vs-transition);

  &:hover   { opacity: 0.85; }
  &:active  { opacity: 0.7; }
  &:disabled { opacity: 0.35; cursor: not-allowed; }

  &--primary {
    background: var(--vs-accent-blue);
    color: #fff;
    &:hover { box-shadow: var(--vs-shadow-glow-blue); }
  }

  &--danger {
    background: var(--vs-accent-red);
    color: #fff;
    &:hover { box-shadow: var(--vs-shadow-glow-red); }
  }

  &--ghost {
    background: transparent;
    color: var(--vs-text-primary);
    border: 1px solid var(--vs-border);
    &:hover { border-color: var(--vs-border-focus); }
  }
}
```

### Card

```scss
.vs-card {
  background: var(--vs-bg-surface);
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  padding: 20px;
  box-shadow: var(--vs-shadow-card);
}

// Card interactiva (modo ranking, selección de modo)
.vs-card--interactive {
  cursor: pointer;
  transition: border-color var(--vs-transition), transform var(--vs-transition);
  &:hover {
    border-color: var(--vs-border-focus);
    transform: translateY(-2px);
  }
}
```

### Input

```scss
.vs-input {
  background: var(--vs-bg-input);
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  color: var(--vs-text-primary);
  font-family: 'Inter', sans-serif;
  font-size: 15px;
  padding: 10px 14px;
  width: 100%;
  outline: none;
  transition: border-color var(--vs-transition);

  &::placeholder { color: var(--vs-text-muted); }
  &:focus { border-color: var(--vs-border-focus); }
  &.ng-invalid.ng-touched { border-color: var(--vs-accent-red); }
}

// Input numérico (modo precisión)
.vs-input--numeric {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 24px;
  text-align: center;
  letter-spacing: 0.08em;
}
```

### Barra de vida

```scss
.vs-lifebar {
  display: flex;
  gap: 6px;

  &__heart {
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--vs-life-full);
    transition: background var(--vs-transition-slow), transform var(--vs-transition);

    &--lost {
      background: var(--vs-border);
      transform: scale(0.85);
    }
  }

  // Ajuste de color según vidas restantes: maneja en el componente
  // 3 vidas → --vs-life-full (teal)
  // 2 vidas → --vs-life-mid (gold)
  // 1 vida  → --vs-life-low (red) + pulso CSS en el último corazón
}

@keyframes vs-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(230,57,70,0.6); }
  50%       { box-shadow: 0 0 0 6px rgba(230,57,70,0); }
}
```

### Badge de modo

```scss
// Colores por modo
$mode-colors: (
  survival:       var(--vs-accent-red),
  precision:      var(--vs-accent-blue),
  binary-duel:    var(--vs-accent-gold),
  precision-duel: var(--vs-accent-green),
  sabotage:       var(--vs-accent-purple),
);

.vs-badge {
  display: inline-block;
  font-family: 'Bebas Neue', sans-serif;
  font-size: 12px;
  letter-spacing: 0.08em;
  padding: 3px 10px;
  border-radius: var(--vs-radius-sm);
  border: 1px solid currentColor;
  text-transform: uppercase;
}

// Uso: <span class="vs-badge" [style.color]="modeColor">SABOTAJE</span>
```

### Temporizador

```scss
.vs-timer {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 48px;
  font-weight: 600;
  color: var(--vs-text-primary);
  transition: color var(--vs-transition);

  &--warning { color: var(--vs-accent-gold); }
  &--critical {
    color: var(--vs-accent-red);
    animation: vs-pulse 0.8s ease-in-out infinite;
  }
}
```

### Divider

```scss
.vs-divider {
  border: none;
  border-top: 1px solid var(--vs-border);
  margin: 24px 0;

  &--accent {
    border-color: var(--vs-accent-blue);
    opacity: 0.4;
  }
}
```

---

## Layout

```scss
// Contenedor máximo de contenido
.vs-container {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 24px;
}

// Pantalla de juego (centrado vertical)
.vs-screen {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--vs-bg-base);
  padding: 32px 16px;
}

// Grid de dos jugadores (duelos)
.vs-duel-grid {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 24px;
  align-items: start;
  width: 100%;
  max-width: 800px;

  &__vs {
    font-family: 'Bebas Neue', sans-serif;
    font-size: 32px;
    color: var(--vs-accent-red);
    padding-top: 16px;
  }
}
```

---

## Animaciones

```scss
// Entrada de pantalla
@keyframes vs-fade-in {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

// Respuesta correcta
@keyframes vs-correct {
  0%, 100% { border-color: var(--vs-border); }
  40%       { border-color: var(--vs-accent-green); box-shadow: 0 0 20px rgba(46,196,182,0.4); }
}

// Respuesta incorrecta (shake)
@keyframes vs-wrong {
  0%, 100% { transform: translateX(0); }
  20%       { transform: translateX(-8px); }
  60%       { transform: translateX(8px); }
}

// Daño recibido (sabotaje / vida perdida)
@keyframes vs-hit {
  0%   { background: var(--vs-bg-surface); }
  30%  { background: rgba(230,57,70,0.15); }
  100% { background: var(--vs-bg-surface); }
}

// Uso
.animate-in     { animation: vs-fade-in 250ms ease forwards; }
.animate-correct { animation: vs-correct 600ms ease; }
.animate-wrong   { animation: vs-wrong 350ms ease; }
.animate-hit     { animation: vs-hit 600ms ease; }
```

---

## Convenciones Angular

### Estructura de componente

```
components/
  game/
    question-card/
      question-card.component.ts
      question-card.component.html
      question-card.component.scss   ← solo estilos del componente
```

- Los estilos globales (tokens, tipografía, reset) van en `styles.scss`.
- Los componentes usan `encapsulation: ViewEncapsulation.None` solo si necesitan sobreescribir librerías externas. Por defecto, usar la encapsulación nativa de Angular.
- Las clases de animación se aplican con `[class]` binding desde el componente: `[class.animate-wrong]="answered && !isCorrect"`.

### Nombrado de clases

Prefijo `vs-` en todas las clases compartidas para evitar colisiones. Dentro de un componente scoped, se puede omitir el prefijo.

### Signals y estado de juego

El estado de partida (vidas, puntuación, temporizador) debe vivir en un servicio o store. Los componentes de UI solo reciben `@Input()` y emiten `@Output()`. No lógica de negocio en plantillas.

---

## Paleta rápida

| Token | Hex | Cuándo usarlo |
|-------|-----|---------------|
| `--vs-accent-red` | `#e63946` | Vidas, errores, peligro, modo supervivencia |
| `--vs-accent-gold` | `#f4c542` | Puntos, ranking, advertencia |
| `--vs-accent-blue` | `#4361ee` | Acción principal, botón CTA |
| `--vs-accent-green` | `#2ec4b6` | Correcto, éxito, vida llena |
| `--vs-accent-purple` | `#7b2d8b` | Modo sabotaje exclusivamente |
| `--vs-text-secondary` | `#9898a6` | Labels secundarios, metadatos |
| `--vs-border` | `#2a2a32` | Separadores, bordes de card |