# Pendientes

Todo lo que falta, ordenado por prioridad. Estado al **15/11/2026**. Marcá lo que hagas y dejá la fecha. Antes de empezar algo grande, hablalo con el dueño ([`08-decisiones.md`](08-decisiones.md)).

Referencias: [`01-traspaso.md`](01-traspaso.md) (riesgos), [`05-trabajos.md`](05-trabajos.md), [`07-rangos-y-permisos.md`](07-rangos-y-permisos.md).

## A. Antes de abrir al público (bloquean la apertura)

- [ ] **Backups fuera del host.** Hoy hay un único backup diario (05:00, ~12 GB) en el mismo hosting. Armar copia externa y rotación (el plan pedía 7 diarios, 4 semanales, 1 mensual) y **probar una restauración**.
- [ ] **Reinicio diario programado** de madrugada, con aviso por `say` (hoy solo corre el backup). Se hace con una tarea programada del panel.
- [ ] **Sacar `3y33Beta`** de `mundos` en `3y33Trabajos/config.yml` y de la lista de mundos de Lands.
- [ ] **Retirar Aurora, AuroraLevels y AuroraQuests** (decisión del dueño: sin niveles de cuenta). Antes: auditar qué los usa — TAB, formato del chat, menús, `/nivel`, `/misiones` (anuncios del Core y chat del Login) — y reemplazar o sacar esos textos. Mover los jars y configs a `/respaldo-prebeta/`, no borrarlos.
- [ ] **Limpiar LuckPerms** (lista completa en [`07-rangos-y-permisos.md`](07-rangos-y-permisos.md) sección 4): herencia a `global` inexistente, track `rangos`, permisos de `landclaim`/`tierras`/`quickshop`/`ezrtp`, grupo `donador`, usuario con `*` sin nombre, `3y33.chunk.1/2` de la cuenta de prueba. Hacer `lp export` y guardarlo en `/respaldo-prebeta/` primero.
- [ ] **Limpiar los cooldowns de `/rtp`** del Core que nombran rangos viejos.
- [ ] **Decidir los chunks gratis de VIP/VIP+** (hoy 25/50 configurado, sin confirmar) y, si se mantienen, probar que Lands toma el permiso `3y33.chunk.N` sin relogueo.
- [ ] **Calibrar el balance de los trabajos** con `/trabajosadmin balance` (curva de niveles, `xp-por-hora`, pagos) con varios jugadores reales jugando.
- [ ] **NPC de los trabajos:** registrar y ubicar los 4 definitivos; borrar el maniquí de prueba con el tag `3y33test`.
- [ ] **Decidir** si el resource pack pasa a ser obligatorio (`require-resource-pack`).
- [ ] **Regenerar el pack de Bedrock** (versión del 30/10: sin insignias, íconos nuevos ni mochilas).
- [ ] Dejar la cuenta de prueba sin permisos de prueba y revisar la whitelist.

## B. Funcionalidad que falta

### Trabajos
- [ ] **Penalización por abandonar** un trabajo sin completarlo (diseño en [`05-trabajos.md`](05-trabajos.md) sección 7). **Confirmar el porcentaje con el dueño** (se propuso 20 % del XP total del trabajo). El menú de confirmación debe mostrar la pérdida; los hitos ya reclamados no se vuelven a ofrecer.
- [ ] **Encantamientos de los hitos 100 / 115 / 130:** Veta (Minero), Talar (Leñador), Siembra (Granjero). Para Cazador ("Rastro") y Pescador ("Red") hay versiones de comodidad **pendientes de confirmar**. Hay que implementarlos aplicando la regla "pertenecen al trabajo": solo funcionan con ese trabajo activo.
- [ ] **Prestigio** (+25 % de multiplicador y marco de ícono): el campo existe, nada lo sube.
- [ ] **Chunks gratis como recompensa** (`chunks.activo: true`) cuando Lands esté resuelto.
- [ ] (A decidir) rareza de las cartas de recompensa: asegurar al menos una rara o mejor, o bajar el peso de las comunes (hoy 65/24/8/2,5, mega 0,1 %).
- [ ] (Idea) **Mochila 3D en la espalda** del jugador, visible para los demás.

### Logros y cosméticos
- [ ] **Sistema de logros propio** (`/logros`), con "muchísimos" logros, para reemplazar lo que dejaba AuroraQuests. El dueño lo quiere y espera el visto bueno del diseño antes de empezar (hoy solo existe la idea).
- [ ] **Cosméticos de hitos y de logros**, para la beta (los hitos hoy solo dan plata/boosts).

### Tiendas
- [ ] (A decidir) pasar `ventana-banner` de `staff` a `todos`.
- [ ] Sacar la clave `maximo-tiendas-por-tierra` del plugin si se confirma que no hay límite.
- [ ] Revisar las dos tiendas de prueba que quedaron **suspendidas** porque se borraron sus tierras (comportamiento normal de `Integridad`).

## C. Orden y mantenimiento

- [ ] Mover `/plugins/LuckPerms/doc-export.json.gz` (export de relevamiento) a `/respaldo-prebeta/`.
- [ ] Borrar `respaldo-landclaim/` **solo cuando el dueño lo autorice**.
- [ ] **Borrar la rama `respaldo-historial-claude`** de GitHub (copia del historial anterior a la reescritura del 10/11): Settings del repo → Branches → ícono de papelera al lado de esa rama. No tocar `main`.
- [ ] Revisar jars de terceros viejos en `plugins/terceros/` (Geyser y Multiverse): confirmar que son las versiones instaladas.
- [ ] Documentación de `referencia/`: son manuales viejos; ir actualizándolos o descartándolos.
- [ ] **Aviso de Windows `photos.exe`** que le salía al dueño en su PC cada tanto: no se resolvió (falta el texto o una captura del cartel). No es del server.

## D. Hecho (para tener el contexto)

- [x] **Mochilas con páginas y mejora en la mesa de crafteo** (Trabajos 1.11.0 a 1.11.2), con la regla del nivel 65 (15/11).
- [x] Hitos del camino de recompensas (10 a 130), con dinero, pack de boosts, mochila IV y encantamientos (solo se guardan).
- [x] Insignias de chat por trabajo y rareza (niveles 30, 65, 100 y 130), con arte propio.
- [x] Costo de chunks en Lands: +25 % por chunk (era 10 %).
- [x] Reescritura del historial de `main` para quitar coautorías de asistentes (10/11) y reanclado del resource pack a un commit nuevo.
- [x] Esta documentación (15/11).
