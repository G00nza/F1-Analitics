# EP-04: Qualifying Analysis

**Iteración**: 2
**Prioridad**: Media-Alta
**Dependencias**: EP-01, EP-02
**Estado**: Pendiente

## Features

### F-04.1: Resultado detallado de qualifying
Vista completa de una sesión de clasificación.

**Output por segmento:**
```
QUALIFYING — GP de Mónaco 2024

Q3 (top 10)
P1  LEC (FER)  1:10.270  —
P2  VER (RBR)  1:10.457  +0.187
P3  NOR (MCL)  1:10.622  +0.352
...

Q2 eliminations (P11-P15)
P11  SAR  1:11.124  +0.854
...

Q1 eliminations (P16-P20)
P16  ZHO  1:12.001  +1.731
```

**Criterios de aceptación:**
- [ ] Muestra tiempos de Q1/Q2/Q3 para cada piloto (los que correspondan)
- [ ] Marcar penalizaciones de grilla y su origen
- [ ] Mostrar grilla real vs grilla por mérito

---

### F-04.2: Análisis de mejora de tiempo en qualifying
¿Cómo evolucionaron los tiempos a lo largo de la clasificación?

**Métricas:**
- Tiempo de cada intento por piloto en Q3
- Cuántas vueltas rápidas hizo cada piloto
- Evolución del top-3 intento por intento
- Quién mejoró en el último intento ("últimas vueltas")

**Criterios de aceptación:**
- [ ] Timeline de tiempos por piloto a lo largo de la sesión
- [ ] Identificar el "traffic jam" final (muchos pilotos haciendo su último intento al mismo tiempo)

---

### F-04.3: Gap analysis qualifying
¿Qué tan comprimida está la parrilla? ¿Hay un dominador claro?

**Métricas:**
- Gap entre P1 y cada posición (1, 5, 10, 15, 20)
- Cuántos pilotos están dentro del 1% del pole time
- Comparativa: ¿es la parrilla más o menos comprimida que el año anterior en este circuito?

**Criterios de aceptación:**
- [ ] Cálculo de gap como porcentaje y como tiempo absoluto
- [ ] Comparativa con misma carrera año anterior (si hay datos en OpenF1)


---

## Estimación de esfuerzo
- F-04.1 Resultado qualifying: 1 día
- F-04.2 Mejora en Q: 1 día
- F-04.3 Gap analysis: 1 día
- **Total**: ~4 días
