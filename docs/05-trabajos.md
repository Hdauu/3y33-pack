# 3y33Trabajos (los trabajos)

El plugin más grande. Versión instalada **1.11.2**. Código en `plugins/3y33Trabajos/`, paquete `ar.com.tresyetres.trabajos`. Diseño original (más largo, algunas cosas cambiaron): [`referencia/diseno-trabajos-original.md`](referencia/diseno-trabajos-original.md).

## 1. Idea general

Cada jugador tiene **un trabajo activo** a la vez: Minero, Leñador, Granjero, Cazador o Pescador. Lo que hace en ese trabajo (picar, talar, cosechar, matar, pescar, criar) **paga plata (Vault) y XP**. El progreso de cada trabajo se guarda aparte; si lo abandonás, conservás el nivel pero no podés tomar otro hasta que pasen **13 horas** (`abandonar-horas`). Nivel máximo: **130**.

Regla de diseño del dueño: **lo ganado en un trabajo pertenece a ese trabajo** (ver sección 7). Los trabajos no suben nivel de cuenta ni se muestran en el TAB ni en el scoreboard; solo se ven como **insignia en el chat**.

## 2. Cómo se paga una acción

```
pago = base * (multiplicador personal + bono de rareza) * momentum * (1 + bono global) * multiplicador de plata (boost)
```

- **Base:** `acciones.yml` (pago en pesos por bloque, mob, pez, cría). XP de la acción = pago base × `xp-por-peso` (15), salvo que `acciones.yml` diga otra cosa.
- **Multiplicador personal** = `1 + bono de nivel + 0,25 × prestigio + racha`. Bono de nivel: +1 % por nivel hasta el 100 y +0,5 % del 100 al 130. Racha: +2 % por día seguido de misiones, máximo 10 días. **El prestigio no está implementado** (el campo existe pero nada lo sube).
- **Rareza diaria por acción:** a las 6:00 (hora de Buenos Aires, `reroll-hora`) se sortea la rareza de cada acción, igual para todos, con pesos común 55 / rara 28 / épica 13 / legendaria 4. Suma al multiplicador: +0 / +0,5 / +1 / +2. Se guarda en `estado.yml` para que un reinicio no la cambie. `/trabajosadmin reroll` fuerza el sorteo.
- **Momentum:** sube 0,02 por acción que paga (máximo ×3,0). Si te quedás quieto 10 s empieza a bajar 0,01 por segundo.
- **Pago por tiempo:** $15 cada 10 minutos, si hubo al menos una acción que paga (también multiplicado por el personal).
- **AFK:** a los 60 s sin moverte ni mirar, el momentum vuelve a ×1 y se corta el pago por tiempo.
- **Boosts** (de recompensas): multiplicadores de XP o de plata con duración; pueden andar dos a la vez (uno de cada tipo). El tiempo corre mientras estás conectado con ese trabajo puesto.

### Anti-trampa (`Escucha`, `Colocados`)

- Los bloques que **puso un jugador** no pagan al romperlos (se marcan en el chunk; `Colocados`).
- Mobs de **spawner o de huevo** no pagan.
- Tope de **120 mobs por hora por tipo** (`topes.mobs-por-hora`) y **20 crías por hora** (`crias-por-hora`).
- Solo paga en modo **supervivencia o aventura**.
- Solo en los mundos de `mundos` (prefijo: `3y33SMP` incluye nether y end). Hoy figura también `3y33Beta`: **sacarlo antes de abrir**.

## 3. Niveles

La curva sale de `config.yml › niveles`: para llegar al 130 un jugador que juega 1,5 h por día y hace las misiones necesita unas **300 horas** (`horas-objetivo`), con exponente 1,7 (cada nivel cuesta más que el anterior). El XP total depende de `xp-por-hora` de cada oficio (minero 4500, leñador 15500, granjero 25000, cazador 13500, pescador 4000).

**Esos números son una estimación.** Para calibrarlos hay que usar `/trabajosadmin balance`, que muestra cuánta plata y XP crean los trabajos por hora activa (guarda las últimas 72 horas en `balance.yml`) y corregir `xp-por-hora` y los pagos de `acciones.yml`. Es una tarea pendiente importante antes de abrir ([`09-pendientes.md`](09-pendientes.md)).

## 4. Misiones (`misiones.yml`, `entregas.yml`)

Todas son de **entregar ítems "limpios"** (sin nombre ni encantamientos) en el NPC del trabajo (`solo-en-npc: true`, a menos de 8 bloques). Van atadas al trabajo activo: si abandonás, las que no cobraste se pierden.

| Tipo | Detalle |
|---|---|
| Diaria aleatoria | Una por día, con rareza. Plata `aleatoria: [150,225,300,450]` (por rareza) y XP en "minutos de trabajo" |
| Diaria elegida | Elegís 1 de 4. Plata `[200,300,450,700]` |
| Semanal personal | Pide `semanal-factor` (8) veces la cantidad de una diaria. Plata base 1500 × multiplicador de rareza |
| Global | Cada `ciclo-dias` (3) cada trabajo sortea **una** misión colectiva. Cantidad según el esfuerzo del trabajo y el valor del ítem, y se acomoda sola a la actividad del server. Quien aporta al menos `participacion-minima` cobra según su %. Al completarse da un bono de pago/XP a todo el trabajo por `bono-horas` (48) |
| Racha | +2 % por día seguido, hasta 10 días |

`entregas.yml` define qué ítems se piden, por trabajo y grupo, con un **valor de dificultad** por ítem (admite comodines `*_ORE` y `{M}_DOOR` para todas las maderas; `MATERIAL: 0` saca un ítem). Se recarga con `/trabajosadmin recargar`.

## 5. Recompensas por nivel (`recompensas.yml`)

Cada **5 niveles** aparece un nodo con **3 opciones**: boost de XP, boost de plata o XP instantánea, cada una con rareza sorteada (65/24/8/2,5 %; 0,1 % de chance de **Mega**). Se elige una (con doble clic para confirmar) y las otras se pierden; el nodo espera hasta que lo elijas. Se ve en `/trabajos recompensas <trabajo>`.

**Hitos** (niveles especiales, mismo archivo):

| Nivel | Qué da |
|---|---|
| 10, 20, 30, 40, 45, 60, 70, 75 | Elegir entre dinero (nivel × 40 × (1 + 0,5 × rareza)) o un pack de boosts (2 de XP y 2 de plata) |
| 50, 90 | Lo mismo, más la opción **chunk gratis** (hoy apagada: `chunks.activo: false`) |
| 65 | **Mochila IV** (ver sección 7). Cuando ya la reclamó antes con otro trabajo, da dinero |
| 100, 115, 130 | Encantamientos propios del trabajo (`enc1`, `enc2`, `enc3`). **El hito se reclama pero el encantamiento todavía no hace nada** ([`09-pendientes.md`](09-pendientes.md)) |

Los chunks gratis, cuando se activen, ejecutan `lp user {jugador} permission set 3y33.chunk.{n} true`, que Lands convierte en un chunk gratis (tope 25 por cuenta). Ver [`07-rangos-y-permisos.md`](07-rangos-y-permisos.md).

## 6. Mochilas (`Mochilas.java`)

Ítem `PAPER` con modelo `3y33:trabajos/mochila_N` (1 a 4). Se abren con **clic derecho** en la mano. El contenido se guarda en `plugins/3y33Trabajos/mochilas.yml` (`m.<id>.s<índice>` con el ítem en Base64) y se identifica por una clave en el ítem (`mochila_id`, `mochila_nivel`, `mochila_ligada`, etc.), así que **no se pierde al mejorarla**.

- **Espacios:** `mochilas.espacios: [45, 90, 135, 180]` (múltiplos de 45, entre 45 y 360). Se muestran en **páginas de 45** con fila de navegación (anterior, libro con la página, siguiente) cuando hay más de una.
- **Mochila I:** receta en la mesa de crafteo, 8 cueros alrededor de un cofre (`LLL / LCL / LLL`).
- **Mejoras II, III, IV:** en la mesa de crafteo, la mochila anterior + materiales (`mochilas.mejoras`, formato `MATERIAL:cantidad`, hasta 8 ítems en total): II = 4 cuero + 4 lingotes de hierro; III = 4 diamantes + 4 lingotes de oro; IV = 2 netherita + 3 diamantes + 3 perlas de ender. La mochila conserva su contenido y su id.
- **Recetas:** claves `mochila_base`, `mochila_mejora_2..4`, registradas al cargar el plugin y quitadas al apagarlo. `PrepareItemCraftEvent` arma el resultado; `CraftItemEvent` bloquea el *shift-click*.
- **Admin:** `/trabajosadmin mochila dar <jugador> <1-4>` (requiere `tresyetres.staff`).
- Idea para más adelante: mostrarla **en la espalda** del jugador con un modelo 3D visible para los demás.

## 7. Regla "las recompensas pertenecen al trabajo" (importante)

Pedido del dueño: no se puede farmear un trabajo, saltar a otro y seguir usando todo lo del primero.

**Ya implementado para la Mochila IV (recompensa del nivel 65):**

1. La Mochila IV se puede **craftear una sola vez por cuenta**, y solo con el trabajo activo en **nivel 65 o más** (`tieneNivel65`). El hito del 65 entrega los materiales la primera vez (desbloqueo global `mochila4`); las otras veces da dinero.
2. Una mochila IV ya armada **solo se abre mientras el trabajo activo del jugador esté en nivel 65+**. Si no, el contenido queda guardado y se puede abrir cuando vuelva a cumplirlo.

**Diseño acordado pero SIN código (penalización por abandonar):** si abandonás un trabajo **sin haberlo completado** (nivel 130), perdés un porcentaje del XP de ese trabajo; si lo completaste, no hay penalidad. El menú de confirmación tiene que mostrar cuánto se pierde, y los hitos ya reclamados no se vuelven a ofrecer. Se propuso un **20 % del XP total del trabajo**; **el dueño todavía no confirmó el porcentaje**. Lo mismo vale para los encantamientos de los hitos 100/115/130: el ítem con ese encantamiento solo debe servir mientras el trabajo activo sea el que lo dio.

## 8. Insignias y placeholder

Insignia en el chat: marco por rareza (se desbloquea a los niveles **30, 65, 100 y 130**) + emblema del trabajo, dibujada con la fuente `insignias:chat` del resource pack ([`06-resource-pack.md`](06-resource-pack.md)). Son 4 caracteres (marco, espacio negativo, emblema, espacio) y tienen que ir en **blanco** para que no se tiñan. `/insignia` abre el menú para elegir cuál mostrar. `/trabajosadmin insignias` las muestra todas en el chat.

Placeholder de PlaceholderAPI: **`%trabajos_insignia%`** (devuelve MiniMessage con la insignia y un espacio, o vacío). Lo usa el formato de chat.

## 9. NPC y hologramas

Un NPC por trabajo (más un aldeano "general" en el spawn): son **maniquíes** (`Mannequin`) con skin del pack (`3y33:npc/<trabajo>`) y una etiqueta (`3y33npc`). Sirven para entregar misiones y abrir el menú. Comandos de staff: `/trabajosadmin npc crear <trabajo|general>` (parado donde va) y `npc quitar` (a menos de 4 bloques); `/trabajosadmin holo crear <titulo|panel> <trabajo>`, `holo mover`, `holo quitar`, `holo lista`. Los hologramas se guardan en `hologramas.yml`. **Pendiente:** registrar/ubicar los 4 NPC definitivos y borrar un maniquí de prueba suelto con el tag `3y33test`.

## 10. Comandos

**Jugadores:** `/trabajos` (alias `/jobs`, `/trabajo`, `/job`): sin argumentos abre el menú; `unirse <trabajo>`, `abandonar`, `recompensas [trabajo]`, `<trabajo>` (abre el menú de ese trabajo). `/insignia`.

**Staff** (`tresyetres.staff`), `/trabajosadmin`: `reroll`, `nivel` y `xp` (poner nivel o XP), `espera` (poner o sacar la espera de abandono), `tomar` y `quitar` (poner o sacar trabajo), `misiones [jugador]` (regenerar), `global <trabajo> <cantidad>`, `mision <trabajo> [ítem]`, `npc`, `holo`, `info <jugador>`, `balance`, `ofrecer <jugador> <trabajo>` (deja pendientes los nodos ya alcanzados, para probar), `insignias`, `mochila dar`, `recargar`. Las sintaxis exactas están en `Comandos.java`.

## 11. Archivos de datos y configuración (en el server)

| Archivo (`plugins/3y33Trabajos/`) | Qué es | Editable |
|---|---|---|
| `config.yml` | Reglas generales, curva, momentum, topes, mochilas | Sí |
| `acciones.yml` | Pago base y XP por acción de cada trabajo | Sí |
| `misiones.yml`, `entregas.yml` | Misiones (recompensas, rachas, globales) y catálogo de ítems | Sí |
| `recompensas.yml` | Nodos, rarezas, hitos, chunks | Sí |
| `jugadores/<uuid>.yml` | Perfil de cada jugador (se escribe atómico) | No a mano |
| `estado.yml` | Rarezas del día | No |
| `misiones-globales.yml` | Estado de las misiones globales | No |
| `mochilas.yml` | Contenido de todas las mochilas | **No tocar** |
| `balance.yml` | Medición de plata y XP por hora | No |
| `hologramas.yml` | Hologramas de los NPC | Por comandos |

`/trabajosadmin recargar` vuelve a leer `config.yml` (incluida la curva de niveles), `acciones.yml`, las rarezas, las misiones (con `entregas.yml`) y `recompensas.yml`. **La sección `mochilas` se lee al arrancar**: para cambiar espacios o materiales de mejora hay que reiniciar (detalle en [`03-operacion.md`](03-operacion.md)). Al actualizar el plugin, **el `config.yml` del server no se pisa**: si el jar agrega claves nuevas hay que copiarlas a mano (así se hizo con `mochilas`).

## 12. Qué NO está hecho

- **Prestigio** (+25 % por prestigio y marcos de ícono por prestigio: el código los lee, nada los sube).
- **Encantamientos de los hitos 100/115/130:** Veta (Minero), Talar (Leñador), Siembra (Granjero). Para Cazador ("Rastro") y Pescador ("Red") hay versiones de comodidad **pendientes de confirmar con el dueño**.
- **Penalización por abandonar** (sección 7).
- **Chunks gratis** (apagados).
- **Calibración de balance** (sección 3).
- **Logros propios** (`/logros`): ver [`09-pendientes.md`](09-pendientes.md).
