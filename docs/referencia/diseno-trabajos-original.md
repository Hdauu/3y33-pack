> **Documento de referencia histórico.** Diseño original del plugin 3y33Trabajos (5 trabajos, niveles a 100, prestigio, encantamientos propios, misiones y economía). **La implementación real cambió**: ver `docs/05-trabajos.md` para lo que funciona hoy.
> Se escribió durante la construcción del server y **puede estar desactualizado** (por ejemplo: Lands ya no limita el tamaño de los terrenos, los grupos Sheriff/Supreme/Global ya no existen y los trabajos llegan al nivel 130). Cuando algo de acá contradiga a `docs/01` a `docs/09` o al código, mandan esos.

# 3y33Trabajos: diseño

## Resumen
Cinco trabajos pagan por jugar de verdad: un jugador activo gana unos $450 por hora al empezar y hasta $2.700 con el prestigio máximo, y llega al nivel 100 de un trabajo en unas 57 horas.

- **Trabajos:** Minero, Leñador, Granjero, Cazador y Pescador, de a uno activo.

- **Todo propio:** plugin 3y33Trabajos, menú con el estilo de Tiendas, íconos hechos para el pack y pago por Vault.

- **Lo que nos diferencia:** prestigio de 3 niveles por trabajo, momentum con tope x3 y recompensas que tocan tiendas y tierras.

- **Los números son iniciales.** Se calibran con pruebas y con spark antes de abrir al público.

| Meta | Valor objetivo |
|---|---|
| Primeros 10 chunks de Lands | ~3 h de juego activo |
| 50 chunks | ~20 h |
| Nivel 100 de un trabajo | ~57 h |
| Trabajo completo (nivel 100 + 3 prestigios) | ~200 h |

## Trabajos y reglas generales
Cada jugador tiene un trabajo activo a la vez. El progreso de cada trabajo se guarda aunque no esté activo. Elegir el primero es gratis; dejar un trabajo tiene penalización.

- **Penalización por abandonar: 13 horas.** Al dejar un trabajo quedás sin trabajo activo durante 13 horas: no cobrás por acciones ni por tiempo, y no podés tomar otro (ni volver a ese) hasta que pasen.

- **Las misiones van atadas al trabajo activo.** Solo se avanzan y se cobran con ese trabajo puesto. Si lo abandonás, las que no cobraste se pierden, y el menú te lo avisa antes de confirmar. Así no se puede saltar de un trabajo a otro para cobrar las misiones de varios.

- **Cómo saltarse la espera:** la recompensa épica de nivel "Cambio de trabajo sin enfriamiento" y el hito del nivel 50 (un cambio gratis por semana). Esos cambios no cancelan las misiones ya cobradas, pero las pendientes igual se pierden.

| Trabajo | Qué paga (acción válida) | Pago base de ejemplo |
|---|---|---|
| Minero | Romper menas y piedra a mano | piedra $0,03 · carbón $0,60 · hierro $1,00 · oro $1,60 · diamante $6 · escombros antiguos $14 |
| Leñador | Talar troncos a mano | tronco $0,45 · hongo gigante $0,45 |
| Granjero | Cosechar cultivos maduros y criar animales | cultivo $0,35 · calabaza o sandía $0,50 · cría nueva $2 (máx. 20 por hora) |
| Cazador | Matar mobs con daño directo del jugador | mob común $2 · enderman $3,50 · blaze $4 · wither esqueleto $5 |
| Pescador | Pescar con caña en mano | pescado $0,70 · pez tropical o globo $2 · tesoro $8 · basura $0,10 |

Con esos valores, una hora de juego activo rinde cerca de $300 por trabajo antes de multiplicadores.

- **Pago por ****|****tiempo:** el extra que pediste. Cada 10 minutos con al menos una acción válida se suman $15 base, así quien juega poco igual cobra algo. Si estás quieto o AFK, no se acumula.

- **Qué no paga:** bloques colocados por jugadores, mobs de spawner, cosechas de máquinas y cualquier cosa en creativo o espectador.

- **Moneda:** todo va por Vault, en la misma plata que usan las tiendas.

## Pago y multiplicadores
Cada acción válida paga su valor base por el multiplicador personal y por el momentum; el techo combinado es unas 12 veces el base, y se alcanza solo con nivel 100, prestigio 3 y racha completa.

```
pago = base \times personal \times momentum
personal = nivel \times racha \times prestigio
```

| Pieza | Cómo funciona | Rango |
|---|---|---|
| Nivel del trabajo | +1% por nivel desde el nivel 1 | x1,00 a x1,99 |
| Racha de misiones | +2% por día seguido completando las dos diarias | x1,00 a x1,20 (10 días) |
| Prestigio | +25% por prestigio | x1,00 a x1,75 |
| Momentum | Sube con acciones que pagan, baja con la inactividad | x1,0 a x3,0 |
| Rareza de misión | Solo multiplica la recompensa de la misión | x1 · x1,5 · x2 · x3 |

**Momentum.** Empieza en x1,0. Cada acción que paga suma 0,02, así que con 100 acciones seguidas llega al tope x3,0. Tras 10 segundos sin acciones baja 0,01 por segundo. Es por trabajo y por jugador.
**Solo el pago usa el personal.** La XP usa el momentum pero no el multiplicador personal, para que subir de nivel no se acelere solo.
Los topes del momentum (x3) y del prestigio (+25%) son más bajos que los de otros servidores a propósito: con tiendas, tierras y chunks tienen que seguir teniendo valor.

## Lo que hace Masivo (visto en el juego)
Lo revisé jugando con tu cuenta, solo abriendo menús. Estos son los hechos, sin copiar números que no sirvan para nuestra economía.

- **Cada bloque tiene su propia rareza** (común x1, rara x1,5, épica x2, legendaria x3), y esa rareza es de cada acción, no de la misión. Por ejemplo, el mismo día la mena de cobre era épica, la de oro rara y el lapislázuli profundo legendario.

- **El reroll es automático, no un botón.** El menú dice "Tiempo restante para rerollear rarezas: 22h 1m 43s": cada 24 horas se reordenan las rarezas de todas las acciones. Así el jugador se entera a la mañana qué conviene picar hoy.

- **El multiplicador se suma, no se multiplica.** Pago final = base × (multiplicador personal + rareza). Con personal x1,7382: común x2,7 · rara x3,2 · épica x3,7 · legendaria x4,7. La XP lleva además +3 fijo (x5,7 · x6,2 · x6,7 · x7,7).

- **El personal sale de tres cosas:** misiones, logros (21 en tu cuenta) y nivel del trabajo. Con el Minero en 18 daba x1,7382 y con el Leñador en 3 daba x1,663; son unos +0,005 por nivel de trabajo.

- **Pagan muchísimos bloques, no solo menas.** Minero: 60 acciones (piedra, roca, pizarra, obsidiana, basalto, arcilla, amatista, bloques de mineral crudo, escombros antiguos y todas las menas, con las de pizarra pagando el doble). Leñador: 27 (todos los troncos pagan igual, las raíces y los tallos algo menos, las hojas casi nada). Granjero: 35 (cultivos, flor de chorus, matar vacas y cerdos, y hasta colocar calabazas y sandías).

- **Las misiones son de entregar ítems** ("Entregar objetivo"), y pagan mucho más que romper bloques: una diaria pide 27 pistones o 806 ladrillos de pizarra profunda y da entre $1.400 y $2.400.

- **Hay tres tipos:** diaria aleatoria, diaria elegida y global semanal. La diaria reinicia a una hora fija (no 24 horas desde que la tomás) y la global cada 7 días. Hay que completar las dos diarias para subir la racha.

- **La global tiene fondo compartido:** un objetivo enorme (241.800 bloques de amatista), un fondo para los colaboradores ($220.000 y 170.000 de XP) y un bono global de x1,25 en XP y dinero por 24 horas al completarla.

- **Otros menús del trabajo:** clasificaciones (ranking por trabajo), recompensas por nivel en un camino con nodos (28 en el Minero, con hitos), y un menú de bonificadores de XP y dinero (incluye un x1,25 por PvP fuera de tierras).

- **Abandonar** es un botón rojo en el menú del trabajo. Con clic izquierdo se abre el menú de un trabajo y con clic derecho se elige.

## Niveles, XP y recompensas por nivel
Cada trabajo tiene 100 niveles y llegar al 100 lleva unas 57 horas de juego activo; los primeros niveles vuelan para enganchar y los últimos piden constancia.

```
xp(n) = 200 + 40n + 0{,}9n^2
```
La XP de cada acción es 15 veces su pago base, multiplicada por el momentum.

| Nivel | XP acumulada | Tiempo de juego activo |
|---|---|---|
| 10 | ~3.900 | ~0,5 h |
| 30 | ~30.900 | ~3,5 h |
| 50 | ~95.000 | ~10,5 h |
| 75 | ~250.000 | ~28 h |
| 100 | ~513.000 | ~57 h |

**Cada nivel da a elegir 1 de 3 recompensas** sacadas al azar de este pozo, con peso por rareza:

| Rareza | Recompensa |
|---|---|
| Común | Caja de dinero: nivel × $20 · Impulso de XP x1,5 por 30 min |
| Rara | Impulso de pago x1,25 por 30 min · Salvavidas de racha (saltás un día sin perder la racha) |
| Épica | Cambio de trabajo sin enfriamiento · Cambiar una misión por otra |
| Legendaria | Cosmético del trabajo (marco o partícula) |

**Hitos fijos**, iguales para todos:

| Nivel | Hito |
|---|---|
| 10 | Caja de $5.000 |
| 25 | Título del trabajo junto al nombre |
| 40 | Impulso de pago x1,25 por 1 hora |
| 50 | Un cambio de trabajo gratis por semana |
| 60 | Marco de plata en el ícono del trabajo |
| 75 | Partícula exclusiva del trabajo |
| 100 | Libro del encantamiento, marco dorado, título Maestro y prestigio disponible |

Las tiendas por tierra no tienen límite para nadie: es un sistema propio del servidor para comerciar, y ningún rango ni compra da más espacio. Los hitos dan plata, impulsos y cosméticos, nunca ventajas por pagar.

## Misiones
Cada trabajo tiene sus propias misiones en cuatro formatos, y las dos diarias dan la racha que sube el multiplicador personal.

| Misión | Reinicio | Cómo funciona | Recompensa base |
|---|---|---|---|
| Diaria aleatoria | 24 h | El sistema te asigna una, con rareza al azar | $150 común · $225 rara · $300 épica · $450 legendaria |
| Diaria elegida | 24 h | Elegís 1 de 4 misiones, una por rareza | $200 · $300 · $450 · $700 según la que elijas |
| Semanal personal | Lunes 0:00 | Una por trabajo, con objetivo grande | $1.500 × rareza |
| Global semanal | Lunes 0:00 | Todos los jugadores del trabajo aportan al mismo objetivo | Bono para todos al llegar al 100% |

La recompensa de cada misión se multiplica por el multiplicador personal, como el pago de las acciones.
**Racha.** Para sumar un día a la racha hay que completar las dos diarias (la aleatoria y la elegida). Perder un día la reinicia, salvo que uses un salvavidas.
**Global semanal.** El objetivo se ajusta cada lunes: es el mayor entre un mínimo fijo y el 60% del total de la semana pasada. Aporta quien haga al menos 100 acciones del trabajo. Al llegar al 100%:

- Todos los que aportaron cobran $600.

- Todos los jugadores de ese trabajo tienen +20% de pago por 48 horas.

- El objetivo sigue aceptando aportes hasta el reinicio.
Ejemplos de objetivos: Minero rompe menas, Leñador tala troncos, Granjero cosecha cultivos, Cazador mata mobs hostiles y Pescador pesca con caña. Cada uno con su número propio.

## Prestigio
Cada trabajo se puede prestigiar 3 veces al llegar al nivel 100: el trabajo vuelve al nivel 1 y el jugador gana un bono permanente a cambio de pagar plata.

| Prestigio | Costo | Pago | XP | Marco del ícono |
|---|---|---|---|---|
| 1 | $20.000 | +25% | +10% | Bronce |
| 2 | $60.000 | +50% | +20% | Plata |
| 3 | $150.000 | +75% | +30% | Oro |

- **Se conserva:** el libro del encantamiento y su desbloqueo, los títulos, los cosméticos y los hitos de tiendas ya ganados.

- **Se reinicia:** nivel, XP y los hitos que dependen del nivel (para volver a ganar recompensas por nivel).

- **Es opcional.** Se confirma con un menú que explica qué se pierde y qué se gana.

- **Sumidero de plata.** Los $230.000 por trabajo sacan plata de circulación y le dan un destino a quien ya lo tiene todo.

- Cada prestigio sube un nivel el máximo del encantamiento (ver más abajo).

## Encantamientos propios
Cada trabajo termina en un encantamiento propio que se gana al llegar al nivel 100 y mejora con cada prestigio, hasta el nivel IV.

| Trabajo | Encantamiento | Qué hace (nivel I → IV) | Herramienta |
|---|---|---|---|
| Minero | Veta | Rompe la veta de mena conectada: 6 → 10 → 14 → 20 bloques. Solo menas | Pico |
| Leñador | Talar | Tala el árbol entero: 40 → 80 → 120 → 160 troncos | Hacha |
| Granjero | Siembra | Cosecha cultivos maduros y vuelve a plantar: área 3x3 → 3x3 → 5x5 → 7x7 | Azada |
| Cazador | Presa | Marca al mob que golpeás primero: recibe +10% → +20% → +30% → +40% de daño y suelta más botín y XP | Espada o hacha |
| Pescador | Red | Cada captura tiene 10% → 15% → 20% → 25% de traer una captura extra del mismo tipo | Caña de pescar |

- **Se activa agachado** (Veta, Talar, Siembra) para que no se dispare sin querer.

- **Cuesta durabilidad por cada bloque extra** roto, así no es gratis.

- **Los bloques ****o capturas ****extra pagan 50%** de su valor base, y no suben el momentum.

- **Para usarlo** hay que haber llegado al nivel 100 en ese trabajo alguna vez. El libro es personal, sin comercio, así que regalarlo no salta la progresión.

- **El nivel del encantamiento** está limitado por el prestigio: sin prestigio solo funciona el nivel I; con prestigio 3, hasta el IV.

- **Se ve en el lore** del ítem (compatible con Bedrock). El efecto lo hace el plugin; no depende de Paper ni de datapacks.
Los nombres y los números son una propuesta; los efectos son distintos a los de otros servidores a propósito.

## Economía: ingresos contra costos
Con estos números un jugador activo pasa de unos $450 por hora a unos $2.760, y los chunks siguen siendo un gasto que se siente sin ser un muro, siempre que el costo de Lands crezca de forma lineal.

| Etapa de un trabajo | Personal | Momentum medio | Ingreso por acciones | Tiempo acumulado |
|---|---|---|---|---|
| Nivel 1 | x1,00 | x1,5 | ~$450 / h | 0 h |
| Nivel 30 | x1,42 | x1,8 | ~$770 / h | ~3,5 h |
| Nivel 50 | x1,64 | x2,0 | ~$985 / h | ~10,5 h |
| Nivel 75 | x2,00 | x2,1 | ~$1.260 / h | ~28 h |
| Nivel 100 | x2,39 | x2,2 | ~$1.575 / h | ~57 h |
| Nivel 100, prestigio 3 | x4,18 | x2,2 | ~$2.760 / h | ~200 h |

A eso se suman las misiones: unos $300 a $700 por día en el inicio, que crecen con la racha.
**Costo de los chunks.** Hoy Lands cobra $100 el primero y sube un 10% por chunk. No sé si ese 10% se suma sobre el precio base o se acumula, y la diferencia es enorme:

| Chunks reclamados | Suma lineal (+$10 por chunk) | Acumulado (x1,1 por chunk) |
|---|---|---|
| 10 | ~$1.450 | ~$1.590 |
| 20 | ~$3.900 | ~$5.730 |
| 50 | ~$17.250 | ~$116.000 |

Si es lineal, 50 chunks cuestan unas 20 horas de juego y el sistema ya cierra. Si es acumulado, hay que ponerle tope al aumento por chunk (la opción `cost-increase.max` de Lands). Esto se confirma con un claim de prueba antes de fijar nada.
**Sumideros de plata** que ya tenemos o proponemos: chunks, tiendas y sus puntos de viaje, y el costo de los prestigios ($230.000 por trabajo).

## Anti-trampa
El pago solo premia acciones hechas a mano y con movimiento real; todo lo automático paga cero o cae rápido por el momentum.

- **Bloques puestos por jugadores no pagan.** Se marcan al colocarlos, así no sirve romper y volver a poner.

- **Cultivos****, troncos y pesca**** solo a mano.** Lo que rompe una máquina, un pistón o un dispenser no cuenta. Los cultivos tienen que estar maduros y la pesca tiene que ser con caña en mano: los pescadores automáticos no cuentan.

- **Mobs: daño directo del jugador.** Los de spawner pagan cero. Cada tipo de mob tiene un tope de 120 muertes por hora.

- **AFK y repetición.** Si el jugador casi no se mueve ni mira durante 60 segundos, el momentum vuelve a x1,0 y el pago por tiempo se corta.

- **Tope blando por hora.** Pasado cierto ingreso por hora (el doble del esperado para su etapa), el pago baja a la mitad. Así una granja rota no vacía la economía.

- **Alerta al staff.** Si alguien supera el triple de lo esperado, queda en el log y se avisa por Discord.

- **Creativo, espectador y vanish no pagan.**
Los topes y el "esperado" salen de la tabla de economía de arriba y se calibran con datos reales.

## Etapas y preguntas abiertas
Se construye en siete etapas, y cada una se prueba con vos antes de seguir; la prueba de carga va antes de la primera.

- **Núcleo:** los cinco trabajos, XP, pago por Vault, momentum, anti-trampa y el comando `/trabajos` (con `/jobs` de alias) con un menú básico.

- **Misiones:** aleatoria, elegida, semanal personal, global semanal y racha.

- **Niveles y recompensas:** elegir 1 de 3, hitos y bono de tiendas por tierra.

- **Prestigio:** costos, bonos y reinicio.

- **Arte:** cartas de cada trabajo, marcos de bronce, plata y oro, y banner del menú en el pack.

- **Encantamientos:** Veta, Talar, Siembra y Presa.

- **Balance:** ajustar números con datos reales y spark.
**A definir antes de empezar:**

- ¿Las misiones las lleva el plugin nuevo o AuroraQuests? Recomiendo el plugin nuevo, porque necesita rareza, racha y globales por trabajo.

- ¿El encantamiento se gana al nivel 100 (propuesto) o antes, por ejemplo al 60?

- Decidido: un solo trabajo activo, y abandonarlo tiene una penalización de 13 horas sin trabajo.

- ¿Incluimos logros en el multiplicador personal o los dejamos para después?

- Decidido: las tiendas por tierra no tienen límite. Falta sacar el tope de 30 de la configuración de Tiendas.

- Verificar con un claim de prueba cómo crece el costo de los chunks en Lands.

- ¿Los pagos de las misiones y de los eventos son en plata o también en cosméticos?

- Rarezas de bloques y rerolls: ya lo vimos en el juego (ver la sección de Masivo): rareza por bloque y reroll automático cada 24 horas. Queda elegir si lo copiamos tal cual o a nuestra manera.
