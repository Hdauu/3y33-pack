> **Documento de referencia histórico.** Guía de qué hace cada plugin instalado, qué pueden hacer jugadores y staff, comandos y permisos (incluye anexos con comandos completos de LuckPerms, EssentialsX, Lands y QuickShop). Sirve como manual de consulta; los comandos marcados con (*) no se verificaron uno por uno.
> Se escribió durante la construcción del server y **puede estar desactualizado** (por ejemplo: Lands ya no limita el tamaño de los terrenos, los grupos Sheriff/Supreme/Global ya no existen y los trabajos llegan al nivel 130). Cuando algo de acá contradiga a `docs/01` a `docs/09` o al código, mandan esos.

# Guía de plugins de 3y33 SMP

## Visión general
El server corre Paper 26.2 con unos 45 plugins, tres de ellos propios (3y33Core, 3y33Login, 3y33Tiendas). Esta guía dice qué hace cada uno, qué pueden hacer jugadores y staff, y con qué comando o menú.
**Cómo leerla.** Las secciones van por tema. Cada una tiene una tabla de funciones (qué se puede hacer, cómo y quién) y los avisos de lo que no está cerrado. Los comandos que escribí de memoria están marcados con (*): confirmalos con el `/help` o el `/<plugin> help` del plugin antes de usarlos en serio.
**Piezas clave**

| Tema | Plugins |
|---|---|
| Terrenos y clanes | Lands (pago) |
| Tiendas | QuickShop-Hikari + 3y33Tiendas |
| Economía, niveles, misiones, rangos | Vault, EssentialsX, LuckPerms, Aurora, AuroraLevels, AuroraQuests, ajLeaderboards |
| Menús, hologramas, NPCs, diálogos | DeluxeMenus, FancyHolograms, FancyNpcs, FancyDialogs, ItemJoin |
| Chat y presentación | TAB, ModernTags, LPC, MiniMOTD, DiscordSRV, PlaceholderAPI |
| Cuentas y compatibilidad | AuthMe, 3y33Login, SkinsRestorer, Geyser, Floodgate, BedrockGUI, ViaVersion |
| Seguridad y recuperación | GrimAC, Sonar, CoreProtect, InventoryRollbackPlus, WorldGuard |
| Mundos y construcción | Multiverse-Core, Multiverse-Inventories, Chunky, ChunkyBorder, FastAsyncWorldEdit, GSit |
| Diagnóstico y voz | spark, BairesPing, FancyAnalytics, bStats, Simple Voice Chat |
| Librerías (no se tocan) | packetevents, nCore, faststats |

**Mundos.** `3y33SMP` es el mundo principal (con su nether y end) y `3y33Beta` es el mundo de pruebas. Lands permite reclamar en los dos; en el resto no.
**Pendiente que encontré al armar esta guía.** Los textos de las reglas y de los anuncios automáticos del Core todavía nombran `/terrenos`, y el mensaje de bienvenida de 3y33Login nombra `/tierras`. Esos comandos eran del plugin viejo de terrenos: ahora el comando es `/lands`. Hay que revisar que estén actualizados en el server.

## Terrenos y clanes (Lands)
Lands es el plugin de terrenos, clanes y alquiler/venta de áreas. Hoy está en español (es-AR), con menús para Java y formularios para Bedrock. Se abre con `/lands`, que muestra un menú con todo lo de abajo.
**Reglas que dejé configuradas**

| Tema | Valor actual |
|---|---|
| Mundos donde se reclama | 3y33SMP y 3y33Beta |
| Crear un terreno | gratis, arranca con 1 chunk (el chunk donde estás parado) |
| Costo de cada chunk | 100, y sube 10% por cada chunk que ya tenés |
| Cambiar el nombre | $250, con espera de 1 día entre cambios |
| Teletransporte | sin cuenta regresiva; si el destino es inseguro pide confirmar |
| Tamaño máximo de un terreno | 8 chunks de base; +8 Sheriff, +8 Supreme, +16 Global; de pago: +8 VIP, +16 VIP+, +24 Donador; +5 por cada miembro de confianza |
| Terrenos por jugador | 2 propios y 2 a los que te podés unir |
| Módulos apagados | Naciones, Guerras, RTP de Lands (el /rtp del server es del 3y33Core) |
| Funciones apagadas | mantenimiento (upkeep), impuestos y relaciones entre terrenos |

**Lo que puede hacer un jugador**

| Qué | Cómo |
|---|---|
| Crear un terreno | /lands create <nombre> (*). También con el menú /lands. |
| Reclamar más chunks | /claim en el chunk contiguo. Variantes: rellenar una forma, reclamar mientras caminás, por radio, expandir hacia donde mirás (*). |
| Soltar chunks | /unclaim (también por radio o todo) (*). Devuelve parte de la plata. |
| Ver los límites en pantalla | /lands view: dibuja el borde del terreno y de los vecinos. |
| Invitar a gente | /lands trust <jugador>. Para sacar: /lands untrust. Cada uno recibe un rol (por defecto, miembro). |
| Aceptar o rechazar invitaciones | /lands invites o el menú. |
| Roles y permisos | Menú /lands > Roles: elegís qué puede hacer cada rol (construir, abrir cofres, invitar, etc.). |
| Opciones del terreno | Menú /lands > Opciones: fuego, explosiones, mobs, PvP, entradas y salidas, etc. |
| Spawn del terreno | /lands setspawn lo fija; /lands spawn <terreno> te lleva. |
| Cambiar nombre, descripción, ícono | Menú /lands > Varios, o /lands rename <nombre> (*). |
| Banco del terreno | /lands bank y elegir depositar, retirar o ver el saldo (*) |
| Alquilar o vender áreas | Subzonas: se marcan con una selección, se les pone precio y se publican con un cartel o un holograma. |
| Categorías | Etiquetas de tu terreno: «Buscando miembros» (permite aceptar solicitudes solas) y «Tienda». Sirven para filtrar la lista /lands list. |
| Salir o borrar | /lands leave (miembros) y /lands delete (dueño). El dueño también puede traspasar el terreno con /lands setowner (*). |
| Desatascarte | /lands unstuck: te saca al terreno libre más cercano. |

**Lo que puede hacer el staff**
El staff (owner, admin, dev, moderador) tiene `lands.admin.*` y `lands.bypass.*`: puede abrir cofres, construir y borrar terrenos ajenos, saltarse límites y editar cualquier terreno. Cada acción queda registrada en CoreProtect.

| Qué | Cómo |
|---|---|
| Recargar la config | /lands admin reload. Los cambios de mundos y módulos piden reinicio. |
| Backups de Lands | /lands admin database backup y restore <archivo> confirm (*). |
| Ver o editar el terreno donde estás | /lands edit (*). |
| Administrar terrenos de otros | Menú /lands con el bypass; entrar a cualquier terreno y cambiar roles, opciones, spawn. |
| Terrenos de admin (sin dueño) | Se crean con el permiso de admin y no cuentan en los límites. |

**Sobre los límites.** Los packs de límites están en `plugins/Lands/player-limits.yml` y se suman por grupo de LuckPerms (`group.sheriff`, `group.supreme`, `group.global`, `group.vip`, `group.vipplus`, `group.donador`). El staff ve «ilimitado» porque tiene el pack admin.
**Cosas que Lands no hace** y que ya vimos: no tiene selector de idioma por jugador (usa el idioma del Minecraft de cada uno), no deja «primer cambio de nombre gratis», y el mensaje de confirmación del reload sale en inglés porque está dentro del código del plugin.

## Tiendas (QuickShop + 3y33Tiendas)
QuickShop-Hikari maneja las tiendas con cofres; 3y33Tiendas le suma las reglas de Lands y un holograma propio por tienda. Un jugador monta su tienda sobre un cofre, y el resto compra o vende ahí mismo.
**Reglas del server**

- Solo se pueden crear tiendas dentro del terreno de tu propia hermandad (Lands). Fuera del terreno el plugin las rechaza con un aviso.

- El nombre de la tienda es el nombre del terreno. Cambiarlo es un beneficio de los rangos de pago (permiso `tresyetres.tienda.nombre`); el staff está exento.

- El staff (`tresyetres.staff`) puede crear tiendas donde sea.
**Lo que puede hacer un jugador**

| Qué | Cómo |
|---|---|
| Crear una tienda | Cofre en tu terreno, el ítem a vender en la mano, mirar el cofre y /qs create <precio> (*). |
| Venta o compra | La tienda puede vender (los jugadores le compran) o comprar (los jugadores le venden). Se cambia desde el panel de la tienda o con /qs sell y /qs buy (*). |
| Cambiar el precio | /qs price <precio> mirando el cofre, o el botón de precio del panel. |
| Cantidad por venta | El panel tiene «cantidad»: cuántas unidades vende cada compra. |
| Reponer o vaciar | Botones del panel (refill y empty), o dejar y sacar ítems del cofre. |
| Borrar la tienda | Botón «remove» del panel o /qs remove (*). |
| Ver el historial | Botón «history» del panel: quién compró o vendió y cuánto. |
| Catálogo | /tienda abre el catálogo de todas las tiendas (/qs browse). |
| Buscar un ítem | /qs find <ítem> (*) marca las tiendas cercanas que lo venden. |
| Menú de tiendas | /mitienda, o /menu > Tienda: catálogo, tu terreno, cómo crear una tienda y las flechas del holograma. |

**El panel de control de la tienda.** Es el panel de chat que te sale al tocar tu tienda. Tiene los botones: dueño, ilimitada, congelar, modo (vender/comprar), precio, cantidad, reponer, vaciar, mostrar ítem, historial, borrar y, nuevo, **Holograma** (*).
**El holograma de cada tienda**
El ítem flotante y el texto (nombre en violeta, precio en verde) son una sola pieza que se mueve junta. Las tiendas que compran muestran «Compro $X» en dorado. Se ve en Bedrock porque usa entidades comunes (armor stands y un ítem), no entidades de texto.

| Qué | Cómo |
|---|---|
| Mover desde la tienda | Línea «Holograma» del panel: izquierda, derecha, adelante, atrás, subir, bajar, reiniciar (*). |
| Mover por comando | /holo <acción> [pasos], mirando la tienda o parado cerca de la tuya. Cada paso mueve 0,25 bloques (*). |
| Mover desde un menú (Bedrock) | /mitienda: las flechas usan la tienda que mirás o la tuya más cercana. |
| Tope de movimiento | Hasta 3 bloques a los lados y entre -0,5 y +3 de altura (*). |
| Quién | Solo el dueño de la tienda y el staff. |
| Ajustes globales | plugins/3y33Tiendas/config.yml: altura del ítem, altura del nombre y del precio, y a cuántos bloques se ve el texto. |

**Para el staff**

- Los carteles propios de QuickShop (`display-items` y `text-display`) quedaron apagados a propósito: si se vuelven a prender, aparecen duplicados.

- Cada tienda recuerda su posición del holograma en los datos de 3y33Tiendas (*).

- `/qs` tiene más funciones de administración (recargar, reparar tiendas, ver tiendas de otro jugador) (*). Revisá `/qs help` para la lista completa.

- Pendiente de probar en el juego: que el texto no choque con el ítem y que se vea bien en Bedrock.

## Economía, niveles, misiones y rangos
Todo lo que tiene que ver con plata, progreso del jugador y permisos. Los comandos marcados con (*) son los habituales del plugin pero no los verifiqué uno por uno en este server.
**Economía (Vault + EssentialsX)**
Vault es el puente: Essentials guarda los saldos y el resto de los plugins (tiendas, Lands, menús) cobran y pagan a través de él.

| Qué | Cómo |
|---|---|
| Ver tu plata | /bal o /balance (*) |
| Ver la plata de otro | /bal <jugador> (*) |
| Mandar plata | /pay <jugador> <monto> (*) |
| Ranking de ricos | /baltop (*) |
| Dar o sacar plata (staff) | /eco give <jugador> <monto>, /eco take, /eco set y /eco reset (*) |
| Homes y warps | /sethome, /home, /delhome, /setwarp, /warp (*). Los terrenos de Lands y /menu > Hogares los reemplazan en la práctica. |
| Mensajes privados | /msg <jugador> <texto> y /r para responder (*) |
| Utilidades de Essentials (staff) | /gamemode, /tp, /tphere, /vanish, /fly, /heal, /feed, /invsee, /enderchest, /speed, /god, /give, /kit (*) |

**Niveles (Aurora + AuroraLevels)**
Cada jugador tiene un nivel y XP propios que suben jugando. Sirven de base para los rangos por nivel (sheriff, supreme, global) y para los límites de Lands: por cada nivel alcanzado se suman chunks, miembros y terrenos.

- El nivel y el progreso se ven en `/menu` (perfil) y en el tablist/scoreboard por placeholders.

- El staff puede dar o sacar nivel y XP con los comandos de AuroraLevels. Probalos con `/levels help` (*).

- Las recompensas por nivel se definen en la configuración de AuroraLevels (carpeta del plugin) y suelen ser comandos de consola, por ejemplo dar el grupo de LuckPerms del nivel.
**Misiones (AuroraQuests)**
Misiones por categorías con recompensas. El jugador las abre desde el menú (`/menu` > Misiones) o con `/quests` (*). Cada misión tiene un objetivo (picar, matar, craftear, viajar, etc.) y una recompensa que se entrega sola al completarla. El staff las edita en los archivos de la carpeta de AuroraQuests y las recarga con el comando de reload del plugin (*).
**Rankings (ajLeaderboards)**
Crea tablas de posiciones a partir de cualquier placeholder numérico (plata, nivel, tiempo jugado, kills). Se muestran en hologramas, carteles o menús con placeholders del tipo `%ajlb_lb_<placeholder>_<posición>_name%` (*). Los datos se actualizan solos cada tanto.
**Rangos y permisos (LuckPerms)**
La cadena de herencia del server es: owner > admin > moderador > global > supreme > sheriff > default. Aparte van los rangos de pago: donador, vip y vipplus.

| Qué | Cómo |
|---|---|
| Ver los grupos de un jugador | /lp user <jugador> info |
| Darle un grupo | /lp user <jugador> parent add <grupo> |
| Sacarle un grupo | /lp user <jugador> parent remove <grupo> |
| Darle un permiso | /lp user <jugador> permission set <permiso> true |
| Darle un permiso a un grupo entero | /lp group <grupo> permission set <permiso> true |
| Ver qué permisos tiene un grupo | /lp group <grupo> permission info |
| Verificar un permiso puntual | /lp user <jugador> permission check <permiso> |
| Editor web (lo más cómodo) | /lp editor, abre un link con todos los grupos y permisos para editar en pantalla |
| Permisos temporales | /lp user <jugador> permission settemp <permiso> true 7d |
| Ver el historial de cambios | /lp log recent |

**Qué permisos propios hay**

- `tresyetres.staff`: salta las reglas de tiendas y habilita herramientas de staff de 3y33Core.

- `tresyetres.tienda.nombre`: permite cambiar el nombre de la tienda (rangos de pago).

- `group.<nombre>`: lo usa Lands para dar los paquetes de límites extra por rango (sheriff, supreme, global, vip, vipplus, donador).

## Menús, hologramas, NPCs y diálogos
Lo visual e interactivo del server. Lo marcado con (*) es estándar del plugin pero no lo probé uno por uno acá.
**Menús (DeluxeMenus)**
Cada menú es un archivo `.yml` en `plugins/DeluxeMenus/gui_menus/`. Los que existen: `menu_principal` (el `/menu`), `mitienda` (`/mitienda`), `hogares`, `ajustes` y otros. Los jugadores de Bedrock los ven igual (inventario) y además BedrockGUI puede ofrecer formularios nativos.

| Qué | Cómo |
|---|---|
| Abrir un menú | /menu, /mitienda o /dm open <menú> [jugador] |
| Recargar después de editar | /dm reload |
| Ver la lista de menús | /dm list (*) |
| Crear un menú nuevo | Copiar un .yml de gui_menus, editarlo, registrarlo en config.yml de DeluxeMenus bajo gui_menus: (con file: y, si querés, register_command: true) y hacer /dm reload |
| Qué puede hacer un ítem | Al hacer click: abrir otro menú ([openguimenu]), correr comandos como el jugador ([player]) o como consola ([console]), mandar mensajes ([message]), sonidos ([sound]), cerrar ([close]) y más |
| Requisitos | Cada ítem puede pedir permiso, dinero o placeholders para verse o poder usarse (view_requirement, click_requirement) |

**Hologramas (FancyHolograms)**
Textos flotantes en el mundo (carteles de bienvenida, reglas, rankings, etc.). Son distintos de los de las tiendas, que los maneja 3y33Tiendas.

| Qué | Cómo |
|---|---|
| Ver los comandos | /hologram (*) |
| Crear uno | /hologram create text <nombre> en el lugar donde estés parado |
| Editar el texto | /hologram edit <nombre> setLine 1 <texto> (*) |
| Mover o rotar | /hologram edit <nombre> moveHere y rotate (*) |
| Cambiar fondo, tamaño, alineación | /hologram edit <nombre> background, scale, textAlign (*) |
| Borrar | /hologram remove <nombre> |
| Usar placeholders | Cualquier placeholder de PlaceholderAPI sirve en las líneas, por ejemplo para mostrar rankings |

**NPCs (FancyNpcs)**
Personajes fijos con skin propia que pueden abrir menús, correr comandos o abrir diálogos al hacerles click.

| Qué | Cómo |
|---|---|
| Ver los comandos | /npc (*) |
| Crear | /npc create <nombre> |
| Ponerle skin | /npc skin <nombre> <jugador> (*) |
| Que haga algo al click | /npc action <nombre> add left_click console <comando> (*). También existe right_click, y player en vez de console para correrlo como el jugador. Ejemplo: abrir /menu. |
| Mover a donde estás | /npc teleport <nombre> (*) |
| Que mire al jugador | /npc turn_to_player <nombre> true (*) |
| Borrar | /npc remove <nombre> |

**Diálogos (FancyDialogs)**
Ventanas de diálogo nativas del cliente de Java (con botones y campos de texto), útiles para avisos, formularios y confirmaciones. Se definen y se abren por comando desde la configuración del plugin (*), y se pueden enlazar a NPCs y menús. Bedrock no las muestra igual, para esos jugadores se usan los formularios de BedrockGUI o los menús de inventario.
**Ítems de entrada (ItemJoin)**
Entrega automáticamente ítems al entrar o respawnear, por ejemplo el ítem que abre `/menu`. Cada ítem se define en `plugins/ItemJoin/items.yml` con su nombre, lore, slot, comandos al click y en qué mundos o situaciones se da. Se recarga con `/itemjoin reload` (*). Es el ítem que los jugadores ven en el inventario para abrir el menú principal.
**Editor visual (3y33Core)**
`/editor` (alias `/editar` y `/mover`, solo staff) es el editor de hologramas y NPCs con botones de 3y33Core: elegís un holograma o NPC y lo movés, girás o editás con botones en el chat, sin escribir coordenadas. Con `/``edit``o``r ``<nombre>` abrís directo uno puntual. Hay más detalle en la sección de plugins propios.

## Chat, tags, tablist y Discord
Cómo se ve y se escucha el server. (*) = comando estándar del plugin que no verifiqué uno por uno acá.
**Chat y colores (LPC + 3y33Core)**
LPC da el formato del chat con prefijo de rango y nombre. 3y33Core le suma el color de los mensajes y los estilos de nombre.

| Qué | Cómo |
|---|---|
| Elegir el color del nombre | /color (VIP y VIP+). Los VIP tienen colores sólidos, los VIP+ y el staff degradados. |
| Elegir el color del chat | /color chat |
| Estilo «Argentina» | Regalo por haber jugado la beta (permiso tresyetres.estilo.argentina). Tiene letras finas o gruesas a elección y se apaga con beta.activa: false en la config de 3y33Core. |
| Insignias de rango | Cada rango (vip, vipplus, moderador, dev, admin, owner) tiene un ícono propio que viene en el pack de recursos. |
| Mensajes privados | /msg, /r (Essentials) |
| Ignorar a alguien | /ignore <jugador> (*) |

**Tags (ModernTags)**
Títulos que el jugador elige para mostrar junto al nombre. Se abren con `/tags` (*) y el staff los define en los archivos de ModernTags. Por decisión del server **no** se integran con Lands: no hay tags de terreno ni de hermandad.
**Tablist y nametags (TAB)**
TAB maneja la lista de jugadores (arriba y abajo), el nombre sobre la cabeza y la barra lateral si está prendida. Todo se edita en `plugins/TAB/config.yml` y se aplica con `/tab reload`.

| Qué | Cómo |
|---|---|
| Recargar | /tab reload |
| Ver el estado de un jugador | /tab debug <jugador> (*) |
| Cambiar el encabezado o pie | Sección header-footer de la config |
| Prefijos y sufijos por rango | Sección groups de la config |
| Ocultar o mostrar la barra lateral | /tab scoreboard (*) |

**MOTD de la lista de servidores (MiniMOTD)**
El texto y el ícono que se ven en la lista de servidores de Minecraft. Se edita en `plugins/MiniMOTD/main.conf` y se recarga con `/minimotd reload`. Soporta degradados y dos líneas.
**Anuncios y avisos automáticos (3y33Core)**

- Cada 15 minutos se manda un anuncio en orden: menú, terrenos, misiones, Discord y reglas. Los textos son de la sección `anuncios` de la config de 3y33Core y se recargan con `/3y33core reload`.

- `/3y33core anuncio` manda el siguiente en el momento.

- Los comandos `/help`, `/plugins`, `/version` y similares están ocultos a los jugadores; ven un aviso que los manda a `/menu`.

- Los comandos de Essentials con prefijo no aparecen en el autocompletado de los jugadores.
**Discord (DiscordSRV)**
Conecta el chat del juego con el servidor de Discord (ds.3y33.com.ar).

| Qué | Cómo |
|---|---|
| Ver el estado de la conexión | /discord debug y /discord (*) |
| Recargar | /discord reload |
| Vincular una cuenta | /discord link da un código para escribir al bot en Discord (*) |
| Qué se manda | Chat, entradas, salidas, muertes y logros, según config.yml y messages.yml de DiscordSRV |
| Cambiar canales | Sección Channels de la config |

**Placeholders (PlaceholderAPI)**
Permite usar variables como `%player_name%` o `%vault_eco_balance%` en menús, hologramas, tablist y chat.

| Qué | Cómo |
|---|---|
| Probar un placeholder | /papi parse me %player_name% |
| Ver los instalados | /papi list |
| Bajar una expansión | /papi ecloud download <nombre> y /papi reload (*) |
| Los propios del server | %3y33_insignia%, %3y33_nombre_legacy% y otros de 3y33Core (nombre con color, estado de PvP, etc.) |

## Cuentas, Bedrock y versiones
El server es no premium: cada jugador se registra con contraseña (AuthMe). Java y Bedrock juegan juntos, y se aceptan varias versiones de Java. (*) = comando estándar que no verifiqué uno por uno acá.
**Registro e inicio de sesión (AuthMe + 3y33Login)**

| Qué | Cómo |
|---|---|
| Registrarse (jugador) | /register <contraseña> <contraseña> |
| Iniciar sesión (jugador) | /login <contraseña> |
| Cambiar contraseña (jugador) | /changepassword <vieja> <nueva> |
| Registrar a alguien (staff) | /authme register <jugador> <contraseña> |
| Cambiar la contraseña de alguien | /authme password <jugador> <nueva> |
| Sacar una cuenta | /authme unregister <jugador> |
| Forzar el login de alguien | /authme forcelogin <jugador> (*) |
| Ver la IP y datos de un jugador | /authme getip <jugador> (*) |
| Recargar | /authme reload |

3y33Login es un plugin propio que agrega los títulos en pantalla: mientras el jugador no inició sesión ve «Iniciá sesión» o «Registrate» con el comando; al entrar ve la bienvenida con su nombre, un sonido y un resumen en el chat (misiones, nivel, tierras y Discord). Si entra solo (cuenta original o sesión reciente) ve un aviso de entrada automática. Los textos se editan en `plugins/3y33Login/config.yml` y se aplican reiniciando el server.
**Skins (SkinsRestorer)**
Como las cuentas no son premium, SkinsRestorer deja ponerse una skin de otro nombre.

| Qué | Cómo |
|---|---|
| Ponerte una skin | /skin set <nombre> |
| Ver el menú de skins | /skins (*) |
| Volver a la skin original | /skin clear |
| Cambiarle la skin a otro (staff) | /skin set <nombre> <jugador> (*) |
| Actualizar la skin | /skin update (*) |

**Bedrock (Geyser + Floodgate + BedrockGUI)**

- Geyser traduce el protocolo de Bedrock a Java, y Floodgate deja entrar a Bedrock sin cuenta de Java. Los jugadores de Bedrock aparecen con un UUID especial (empieza con ceros), y 3y33Core los reconoce por eso.

- BedrockGUI muestra formularios nativos de Bedrock en lugar de los menús de inventario, que se ven mal con control táctil o mando.

- Los jugadores de Bedrock aceptan las reglas con el libro, no con el diálogo de Java, y el pack de recursos se les carga aparte.

- Detalles para tener en cuenta: las entidades de texto de Java no se ven en Bedrock, por eso el holograma de tiendas usa armor stands.

| Qué | Cómo |
|---|---|
| Recargar Geyser | /geyser reload |
| Ver el estado y la versión | /geyser version y /geyser dump (*) |
| Ver los jugadores de Bedrock conectados | /geyser list (*) |
| Encontrar si alguien es Bedrock | Su nombre empieza con el prefijo que define Floodgate (en plugins/floodgate/config.yml) |
| Vincular una cuenta de Bedrock con una de Java | /linkaccount (*) |

**Versiones de Java (ViaVersion)**
Deja entrar con clientes más nuevos que el server. Para versiones viejas se necesita ViaBackwards, que no está en la lista de plugins (*). Se ve qué versión usa cada uno con `/viaversion list` y se revisa todo con `/viaversion dump`.
**Pack de recursos del server**
Se carga una sola vez al entrar (3y33Core 1.13.3 ya no lo vuelve a mandar). Lo usan las insignias de rango, la fuente del chat y otros íconos del menú. Si cambiás el pack hay que cambiar el hash en `server.properties` y reiniciar.

## Seguridad, moderación y recuperación
Herramientas para cuidar el server, sancionar y deshacer daños. (*) = comando estándar que no verifiqué uno por uno acá.
**Reportes y sanciones del día a día**

| Qué | Cómo |
|---|---|
| Reportar a un jugador (todos) | /report <jugador> [motivo]. Hay motivos rápidos: hacks, insultos, spam, robo, grief, otro. Espera 60 segundos entre reportes. |
| Ver los últimos reportes (staff) | /reportes |
| Pedir ayuda al staff | /helpop <mensaje> (*) |
| Hablar en el chat del staff | /ac <mensaje> y /amsg (*) |
| Silenciar | /mute <jugador> [tiempo] [motivo] (*) |
| Echar | /kick <jugador> [motivo] |
| Banear | /ban <jugador> [motivo] o /tempban <jugador> <tiempo> [motivo] (*) |
| Desbanear | /unban <jugador> |
| Ver al jugador sin que lo note | /vanish y /spectator en modo espectador (*) |
| Mirar su inventario o cofre de ender | /invsee <jugador> y /enderchest <jugador> (*) |
| Reglas | /reglas abre el libro. Los jugadores nuevos deben aceptarlas en pantalla antes de poder moverse o hablar. |

**Anticheat (GrimAC)**
Detecta vuelo, velocidad, kill aura, x-ray de movimiento y otros trucos. Avisa al staff en el chat cuando alguien da alertas.

| Qué | Cómo |
|---|---|
| Ver o apagar las alertas | /grim alerts |
| Ver el perfil de un jugador | /grim profile <jugador> (*) |
| Ver quiénes tienen alertas | /grim verbose y /grim history <jugador> (*) |
| Recargar | /grim reload |

Si un jugador se queja de que lo patea o lo traba sin razones, revisá los logs de Grim antes de sancionar: con mala conexión o desde Bedrock puede dar falsos positivos.
**Anti-bots (Sonar)**
Protege contra ataques de bots antes de que lleguen al juego: manda a los que entran por primera vez a un mini mundo de prueba, revisa que se muevan como un jugador real y pone las conexiones en cola. No usa captchas. Se maneja con `/sonar` (lista de comandos con `/sonar` en la consola). Si un jugador legítimo no puede entrar, mirar los avisos de Sonar en la consola. Más detalle en el anexo.
**Registro de acciones (CoreProtect)**
Guarda quién rompió, puso, abrió o mató qué, y dónde. Es la herramienta principal contra grief y robos.

| Qué | Cómo |
|---|---|
| Activar el modo inspector | /co inspect (o /co i), después clic izquierdo o derecho en bloques |
| Ver quién tocó la zona | /co lookup radius:10 time:3d (*) |
| Buscar lo de un jugador | /co lookup user:<jugador> time:1d (*) |
| Deshacer lo que hizo alguien | /co rollback user:<jugador> time:3h radius:20 |
| Rehacer un rollback | /co restore user:<jugador> time:3h radius:20 |
| Ver el último resultado | /co near (*) |
| Estado del plugin | /co status |

Siempre hacer primero `/co lookup` con los mismos parámetros para ver qué se va a revertir.
**Devolver inventarios (InventoryRollbackPlus)**
Guarda copias del inventario de cada jugador cuando muere, entra, sale o cambia de mundo. Con `/i``r restore` (*) el staff elige un jugador, ve las copias por fecha y restaura inventario, cofre de ender, vida o hambre. Es la forma de devolver lo perdido por una muerte con bug o por un hacker.
**Protección de zonas (WorldGuard)**
Las bases de los jugadores las protege Lands. WorldGuard queda para zonas del staff, como el spawn.

| Qué | Cómo |
|---|---|
| Seleccionar una zona | Varita de WorldEdit //wand y marcar dos puntos |
| Crear la región | /rg define <nombre> |
| Ver las banderas | /rg info <nombre> |
| Prohibir algo | /rg flag <nombre> pvp deny, build deny, mob-spawning deny, creeper-explosion deny (*) |
| Ver las regiones en tu posición | /rg info |
| Borrar | /rg remove <nombre> |
| Saltar las protecciones (staff) | /rgbypass o /wg bypass (*) |

## Mundos, construcción y comodidades
Los mundos del server son 3y33SMP (el principal) y 3y33Beta, y las bases se reclaman en esos dos. (*) = comando estándar que no verifiqué uno por uno acá.
**Mundos (Multiverse-Core + Multiverse-Inventories)**

| Qué | Cómo |
|---|---|
| Ver los mundos | /mv list |
| Ir a un mundo | /mv tp <mundo> |
| Crear un mundo | /mv create <nombre> normal (también nether o end) (*), con -g para generador y -s para semilla |
| Importar uno existente | /mv import <nombre> <tipo> (*) |
| Borrar o descargar | /mv unload <mundo> y /mv delete <mundo> (*). Borrar es definitivo: hacer backup antes. |
| Info de un mundo | /mv info <mundo> |
| Cambiar una regla del mundo | /mv modify set <propiedad> <valor> <mundo> (*), por ejemplo gamemode, pvp, difficulty, animals, monsters |
| Recargar | /mv reload |

Multiverse-Inventories separa inventarios, vida, experiencia o logros entre grupos de mundos. Se maneja con `/mvinv` (*); por ejemplo, para que la beta tenga inventario propio y no se mezcle con el principal.
**Pregenerar y limitar el mundo (Chunky + ChunkyBorder)**
Chunky genera los chunks por adelantado para que explorar no lagee. ChunkyBorder muestra y hace cumplir el límite del mundo.

| Qué | Cómo |
|---|---|
| Elegir el mundo | /chunky world <mundo> |
| Centro y radio | /chunky center y /chunky radius <bloques> |
| Empezar | /chunky start |
| Ver el avance | /chunky progress |
| Pausar o seguir | /chunky pause y /chunky continue |
| Cancelar | /chunky cancel |
| Poner el borde | /chunkyborder (*), o /chunky worldborder para copiar el radio actual |

`/rtp` (3y33Core) manda a los jugadores a un lugar al azar entre 500 y 9500 bloques del centro del mundo principal. Tiene 3 segundos de espera y un tiempo entre usos que baja con el rango: 10 minutos para todos, 7 para sheriff, 5 para supreme y 3 para global; el staff no espera. Si cambiás el borde, ajustá el radio máximo del `/rtp` en la config.
**Construcción masiva (FastAsyncWorldEdit)**
Es WorldEdit, pero rápido y sin lag, para obras grandes. Es herramienta de staff y de admins de eventos. Los permisos de WorldEdit hay que darlos con cuidado porque se puede romper mucho en segundos.

| Qué | Cómo |
|---|---|
| Varita de selección | //wand |
| Seleccionar con comandos | //pos1, //pos2 y //expand |
| Rellenar | //set <bloque> |
| Reemplazar | //replace <de> <a> |
| Copiar y pegar | //copy y //paste (con -a para ignorar aire) |
| Deshacer o rehacer | //undo y //redo |
| Hacer formas | //sphere, //cyl, //hsphere |
| Pinceles | /brush sphere <bloque> <radio> (*) |
| Guardar una construcción | //schem save <nombre> y //schem load <nombre> |
| Limpiar entidades | /butcher y //removenear (*) |

Las selecciones también sirven para crear regiones de WorldGuard.
**Sentarse (GSit)**
Los jugadores pueden sentarse, acostarse o hacerse el muerto para decorar el spawn y las bases.

| Qué | Cómo |
|---|---|
| Sentarse | /sit o clic derecho en escaleras y losas con la mano vacía (*) |
| Acostarse | /lay (*) |
| Sentarse sobre otro jugador | /gsit y clic derecho sobre él (*) |
| Girar la cabeza | Se hace sola al mirar para otro lado |

**Lo cotidiano de los jugadores**

- `/rtp` para explorar, `/spawn` y `/menu` para volver al centro y ver todo.

- `/pvp [on|off]` activa o apaga el PvP propio. Después del último golpe estás 20 segundos «bajo combate»; si te desconectás peleando, morís y soltás todo. Entre un cambio y otro hay 10 segundos de espera.

- Los comandos de Essentials más usados son `/home`, `/sethome`, `/warp`, `/tpa <jugador>`, `/tpaccept` y `/back` (*).

## Rendimiento, diagnóstico y voz
Para saber cómo anda el server y encontrar qué lo hace lagear. (*) = comando estándar que no verifiqué uno por uno acá.
**Perfilador (spark)**
La herramienta principal para diagnosticar lag. Mide TPS, uso de CPU y memoria, y qué plugin o tarea consume más.

| Qué | Cómo |
|---|---|
| Ver TPS, CPU y memoria al instante | /spark tps y /spark healthreport |
| Perfilar unos minutos | /spark profiler start, esperar con el lag y /spark profiler stop. Devuelve un link con el análisis. |
| Perfilar solo cuando haya lag | /spark profiler start --only-ticks-over 100 (*) |
| Ver el uso de memoria | /spark heapsummary (*) |
| Ver el estado de los GC | /spark gc (*) |
| Ver la lista de plugins que pesan | En el informe del profiler, pestaña de plugins |

**Ping y consulta de latencia (BairesPing)**
Plugin del hosting (Baires Host) para ver el ping de los jugadores y el estado de su red. Se ve con `/ping` (*) o por placeholder en el tablist. Si un jugador se queja de lag y su ping es alto, el problema es su conexión y no el server.
**Panel y consola del hosting**
El server está en Baires Host (panel.baires.host). Desde el panel se hace lo siguiente:

- Ver la consola y mandar comandos.

- Prender, apagar, reiniciar o matar el server.

- Ver el uso de CPU, RAM, disco y red.

- Administrar archivos (subir, editar, mover, descomprimir) y hacer backups.

- Programar tareas, por ejemplo reinicios automáticos.
Antes de reiniciar es buena práctica avisar con `/say` para que nadie pierda progreso.
**Estadísticas de uso (bStats + FancyAnalytics)**
bStats y FancyAnalytics mandan estadísticas anónimas de uso a los desarrolladores de cada plugin (versión, cantidad de jugadores y similares). No cambian nada del juego. Si querés desactivarlas, hay que cambiar `enabled` a `false` en la carpeta `plugins/bStats/config.yml` y en la config de FancyAnalytics.
**Chat de voz (Simple Voice Chat)**
Deja hablar en el juego con micrófono por proximidad. Los jugadores necesitan tener el mod instalado en Java (en Bedrock no funciona).

| Qué | Cómo |
|---|---|
| Abrir el menú de voz (jugador) | Tecla V por defecto (se cambia en controles) |
| Hablar con un grupo | Crear un grupo desde el menú de voz |
| Ver cuántos lo usan | /voicechat (*) |
| Puerto | UDP 24454 por defecto. Debe estar abierto en el hosting para que funcione (*) |
| Distancia de voz | max_voice_distance en plugins/voicechat/voicechat-server.properties |
| Recargar | /voicechat reload (*) |

**Librería interna (packetevents, nCore, faststats)**
Son dependencias que usan otros plugins (GrimAC, Sonar, FancyHolograms y más). No tienen comandos propios y no hay que tocarlas, pero no se pueden borrar sin romper lo que dependa de ellas.

## Plugins propios de 3y33
Tres plugins hechos a medida para el server. Esta sección está sacada del código y de las configs reales.
**3y33Core (versión 1.13.3)**
El plugin «navaja suiza»: reglas, anuncios, reportes, PvP opcional, `/rtp`, colores y el editor de hologramas.

| Comando | Quién | Qué hace |
|---|---|---|
| /reglas (alias /rules, /normas) | Todos | Abre las reglas como libro. Los jugadores de Java nuevos las ven como diálogo en pantalla y no pueden moverse ni hablar hasta aceptarlas. |
| /report <jugador> [motivo] (alias /reportar, /reporte) | Todos | Manda un reporte al staff. Cooldown de 60 segundos. |
| /rtp (alias /wild, /explorar) | Todos | Teletransporte al azar en el mundo principal, entre 500 y 9500 bloques del centro. Espera 3 segundos. El tiempo entre usos baja con el rango. |
| /pvp [on o off] | Todos | Activa o apaga tu PvP. Hay un tiempo de combate de 20 segundos y 10 de espera entre cambios. Si te desconectás peleando, morís y soltás todo. |
| /color (alias /colornombre) | VIP y VIP+ | Menú para elegir el color del nombre. Con /color chat se elige el del chat. |
| /editor [nombre] (alias /editar, /mover) | Staff | Editor de hologramas y NPCs con botones. |
| /reportes | Staff | Muestra los últimos reportes. |
| /3y33core reload | Admin | Recarga la config. |
| /3y33core anuncio | Admin | Manda el siguiente anuncio ahora. |
| /3y33core holograma <plantilla> [nombre] | Admin | Crea un holograma prearmado donde estés y abre el editor para acomodarlo. |
| /3y33core spawn [mundo] | Staff | Arma los hologramas de bienvenida alrededor del spawn de Essentials. |
| /3y33core orientar [extra] | Staff | Reorienta los carteles de spawn y beta. |
| /3y33core aceptar o rechazar <jugador> | Admin | Acepta o rechaza las reglas por un jugador. |

**Plantillas de hologramas.** Son `bienvenida`, `terrenos`, `misiones`, `niveles`, `reglas`, `explorar` y `discord`, todas con degradado y fondo oscuro. Los carteles de spawn son clickeables: mirarlos y hacer clic corre el comando de la sección (por ejemplo abre las reglas) o manda el link de Discord.
**Lo que hace solo, sin comandos**

- Anuncios cada 15 minutos, en orden, con link clickeable (menú, terrenos, misiones, Discord y reglas).

- Oculta `/help`, `/plugins`, `/version`, `/about`, `/me`, `/tm` y `/trigger` a los jugadores y los manda a `/menu`; esconde del autocompletado los comandos con prefijo.

- Cooldowns de 60 segundos para `/helpop`, `/ac` y `/amsg` (el staff no tiene).

- Regalo de la beta: el estilo de nombre «Argentina» para todos los que jugaron antes y para los que entren mientras `beta.activa` sea `true`.

- Lleva la cuenta de quién tiene cargado el pack de recursos del server (por eso Bedrock queda aparte).

- Insignias de rango y degradados en el chat, el tablist y los tags.
**Placeholders propios** (para menús, tablist, hologramas): `%3y33_pvp%`, `%3y33_pvp_estado%`, `%3y33_pvp_rojo%`, `%3y33_pvp_verde%`, `%3y33_rango%`, `%3y33_insignia%`, `%3y33_nombre_legacy%`, `%3y33_grad%`, `%3y33_color_a%` y `%3y33_color_b%`.
**Config:** `plugins/3y33Core/config.yml`. Se edita desde el panel y se aplica con `/3y33core reload`. Tiene las secciones `prefijo`, `reglas`, `anuncios`, `reportes`, `comandos-bloqueados`, `cooldowns`, `reglas-dialogo`, `rtp`, `pvp`, `beta` y `pack`. Si cambiás las reglas del diálogo, subí el número de `reglas-dialogo.version` y se las vuelve a pedir a todos una vez.
**3y33Login (versión 1.0.2)**
Títulos y mensajes de bienvenida para AuthMe. No tiene comandos. Se edita en `plugins/3y33Login/config.yml` y se aplica reiniciando.

- Mientras no inició sesión, el jugador ve un título fijo con el comando que le toca (`/login` o `/register`).

- Al loguearse ve «¡Bienvenido!» con su insignia y nombre, un sonido y un resumen en el chat.

- Con cuenta original o sesión reciente ve un aviso de entrada automática.

- El resumen del chat todavía menciona `/tierras` y `/nivel`, y los anuncios de 3y33Core mencionan `/terrenos` y `/misiones`. Conviene confirmar que esos comandos existan tal cual en el server (la parte de terrenos pasa por Lands) y, si no, cambiarlos por el correcto.
**3y33Tiendas (versión 1.1.1)**
Reglas de tiendas y holograma por tienda. Todo está explicado en la sección de Tiendas: creación solo en tu terreno, nombre = nombre del terreno, panel con línea «Holograma», `/holo` y `/mitienda`. Necesita QuickShop-Hikari y Lands para arrancar.
**Mantenerlos al día**
El código está en el repositorio Hdauu/3y33-pack. Al subir un cambio, GitHub Actions compila y publica el jar, que se sube a la carpeta `plugins` del server y se reinicia.

## Chuleta del staff: tareas frecuentes
Lo que más se necesita, en una sola tabla. (*) = comando estándar que no verifiqué uno por uno acá.

| Quiero... | Hago esto |
|---|---|
| Dar un rango a alguien | /lp user <jugador> parent add <grupo> |
| Sacarle un rango | /lp user <jugador> parent remove <grupo> |
| Darle un permiso suelto | /lp user <jugador> permission set <permiso> true |
| Ver por qué no puede hacer algo | /lp user <jugador> permission check <permiso> y /lp verbose on (*) |
| Cambiar la contraseña de alguien | /authme password <jugador> <nueva> |
| Ver quién rompió o robó algo | /co inspect y clic en el bloque o cofre |
| Deshacer un grief | /co lookup primero y después /co rollback user:<jugador> time:3h radius:20 |
| Devolver un inventario perdido | /ir restore (*) |
| Dar o sacar plata | /eco give <jugador> <monto> o /eco take <jugador> <monto> (*) |
| Teletransportarme a alguien | /tp <jugador> (*) |
| Traer a alguien | /tphere <jugador> (*) |
| Ir invisible | /vanish (*) |
| Ver el inventario de otro | /invsee <jugador> (*) |
| Silenciar, echar o banear | /mute, /kick, /ban o /tempban (*) |
| Ver los reportes | /reportes |
| Ver las alertas del anticheat | /grim alerts |
| Saber por qué laguea | /spark tps y después /spark profiler start |
| Crear o mover un cartel | /hologram create text <nombre> y /editor <nombre> |
| Crear un NPC | /npc create <nombre> y /editor <nombre> |
| Armar los carteles de spawn | /3y33core spawn |
| Editar un menú | Cambiar el .yml en plugins/DeluxeMenus/gui_menus/ y /dm reload |
| Cambiar el MOTD | plugins/MiniMOTD/main.conf y /minimotd reload |
| Cambiar el tablist | plugins/TAB/config.yml y /tab reload |
| Cambiar los anuncios o las reglas | plugins/3y33Core/config.yml y /3y33core reload |
| Ver o cambiar límites de Lands | plugins/Lands/player-limits.yml y /lands admin reload |
| Entrar a un terreno ajeno | Comandos de admin de Lands (*); ver /lands admin |
| Ir a un mundo | /mv tp <mundo> |
| Pregenerar el mundo | /chunky world <mundo>, /chunky radius <bloques> y /chunky start |
| Arreglar el pack que no carga | Revisar el hash y la URL en server.properties, reiniciar y volver a entrar |
| Reiniciar el server | Panel de Baires Host. Avisar antes con /say. |
| Ver si todo cargó bien | Consola al arrancar; /plugins muestra en verde los que andan (*) |

**Antes de tocar algo grande**

- Hacé un backup desde el panel (archivos o backups del hosting).

- Si es un archivo de config, guardá una copia con extensión `.orig` al lado, como se hizo con Lands y QuickShop.

- Probá con una cuenta de prueba sin permisos de staff, por ejemplo la de wzii, para ver lo que ve un jugador común.

- Después de reiniciar, mirá la consola buscando errores en rojo.
**Cosas que conviene verificar en el juego**

- El holograma de tiendas se ve en Java y en Bedrock, y el texto no choca con el ítem.

- Aparece la línea «Holograma» en el panel de la tienda y las flechas de `/mitienda` mueven la tienda correcta.

- Un jugador sin rango puede reclamar desde 1 chunk, usar sus comandos de Lands y teletransportarse sin cuenta regresiva.

- Renombrar un terreno avisa que cuesta $250.

- Los textos viejos con `/terrenos` y `/tierras` apuntan a un comando que existe.

# Anexo: referencia completa de comandos
Esta parte es la referencia larga. Cada plugin tiene sus comandos con la sintaxis, qué hace y el permiso que pide, sacados de la documentación oficial de cada uno y de los datos del propio plugin. Cuándo uses un comando: `<algo>` es obligatorio, `[algo]` es opcional.
La referencia está hecha sobre las versiones más nuevas de cada documentación. Si en tu server un comando no existe o se llama distinto, mirá el `/help` o el `/<plugin> help` del propio plugin: las versiones de tu server pueden ser algo más viejas. Lo que viene sin documentación oficial a mano está marcado como tal.

## LuckPerms: comandos completos
Todo es `/lp` (alias `/luckperms`). En la mayoría de los comandos se puede sumar un contexto al final (por ejemplo `world=3y33SMP` o `server=survival`) para que el permiso valga solo ahí, y una duración (`7d`, `2h`) para que sea temporal. Un permiso en verdadero lo da; en falso lo niega explícitamente; con `unset` se saca del todo.
**General y diagnóstico**

| Comando | Qué hace |
|---|---|
| /lp info | Versión, tipo de almacenamiento y cantidad de jugadores y grupos |
| /lp sync | Vuelve a leer los datos desde el almacenamiento |
| /lp editor | Link a un editor web con toda la configuración (grupos, permisos, prefijos). Lo más cómodo para cambios grandes |
| /lp editor group <grupo> y /lp editor user <jugador> | Abre el editor solo con ese grupo o jugador |
| /lp applyedits <código> | Aplica los cambios guardados en el editor web |
| /lp verbose on y /lp verbose off | Muestra en vivo cada chequeo de permiso (quién, qué permiso, resultado). Sirve para encontrar por qué algo no anda |
| /lp verbose record | Graba esos chequeos para verlos después en el editor web |
| /lp export <archivo> e /lp import <archivo> | Backup y restauración de todo el sistema de permisos |
| /lp log recent | Cambios hechos hace poco, con quién los hizo |
| /lp search <permiso> | Busca quiénes y qué grupos tienen ese permiso |

**Grupos**

| Comando | Qué hace |
|---|---|
| /lp listgroups | Lista de grupos |
| /lp creategroup <grupo> y /lp deletegroup <grupo> | Crear y borrar |
| /lp group <grupo> info | Padres, peso y permisos del grupo |
| /lp group <grupo> permission set <permiso> true o false | Da o niega un permiso al grupo |
| /lp group <grupo> permission unset <permiso> | Lo saca |
| /lp group <grupo> permission info | Todos los permisos del grupo |
| /lp group <grupo> parent add <otro grupo> | Hace que herede de otro grupo |
| /lp group <grupo> parent set <otro grupo> | Reemplaza todos sus padres por uno |
| /lp group <grupo> parent remove <otro grupo> | Saca un padre |
| /lp group <grupo> meta setweight <número> | Peso: el grupo de mayor peso gana al elegir prefijo y otros datos |
| /lp group <grupo> meta setprefix <prioridad> "<texto>" | Prefijo del grupo (acepta colores) |
| /lp group <grupo> meta setsuffix <prioridad> "<texto>" | Sufijo |
| /lp group <grupo> rename <nombre> y /lp group <grupo> clone <nombre> | Renombrar y duplicar |

**Jugadores**

| Comando | Qué hace |
|---|---|
| /lp user <jugador> info | Grupo principal, prefijos, permisos y contextos |
| /lp user <jugador> permission set <permiso> true o false | Da o niega un permiso |
| /lp user <jugador> permission settemp <permiso> true <duración> | Permiso temporal (por ejemplo 7d) |
| /lp user <jugador> permission unset <permiso> | Lo saca |
| /lp user <jugador> permission check <permiso> | Dice si lo tiene y de dónde viene (qué grupo) |
| /lp user <jugador> permission info | Todos sus permisos |
| /lp user <jugador> parent add <grupo> [duración] | Lo mete en un grupo sin sacarle los demás |
| /lp user <jugador> parent set <grupo> | Lo deja solo en ese grupo |
| /lp user <jugador> parent remove <grupo> | Lo saca de un grupo |
| /lp user <jugador> parent addtemp <grupo> <duración> | Grupo temporal (para rangos de pago por tiempo) |
| /lp user <jugador> meta addprefix <prioridad> "<texto>" | Prefijo propio |
| /lp user <jugador> clear | Borra todos sus datos de permisos |
| /lp user <jugador> promote <track> y /lp user <jugador> demote <track> | Sube o baja un escalón en una cadena de rangos |

**Cadenas de rangos (tracks)**

| Comando | Qué hace |
|---|---|
| /lp createtrack <track> | Crea una cadena |
| /lp track <track> append <grupo> | Agrega un grupo al final |
| /lp track <track> insert <grupo> <posición> | Lo mete en una posición |
| /lp track <track> info | Muestra los grupos en orden |

**Cambios masivos:** `/lp bulkupdate` permite cambiar o borrar muchos permisos de una sola vez con condiciones. Es poderoso y peligroso: hacer `/lp export` antes.
**Receta: darle a un grupo un permiso nuevo.** `/lp group default permission set lands.player.setting.pvp true`.
**Receta: ver por qué un jugador no puede hacer algo.** 1) `/lp user <jugador> permission check <permiso>`; 2) si dice que no, `/lp verbose on`, que el jugador lo intente y mirar el chat; 3) `/lp verbose off`.
**Receta: rango temporal.** `/lp user <jugador> parent addtemp vip 30d`. Al vencer, vuelve solo al estado anterior.
En este server la cadena de herencia es owner, admin, moderador, global, supreme, sheriff y default; donador, vip y vipplus quedan aparte. Los nombres de permisos con `*` (todos) conviene usarlos solo en los grupos de staff.

## EssentialsX: comandos por categoría
La lista oficial de EssentialsX está en un sitio que no pude abrir (essinfo.xeya.me), así que esta es la lista habitual y la que usa el server. Escribí `/essentials` o `/help <comando>` para ver los de tu versión. Casi todos los comandos usan permisos de la forma `essentials.<comando>` y, para actuar sobre otro jugador, `essentials.<comando>.others`.
**Casas, warps y spawn**

| Comando | Qué hace |
|---|---|
| /sethome [nombre], /home [nombre], /delhome <nombre>, /homes | Poner, ir, borrar y listar tus casas |
| /setwarp <nombre>, /warp <nombre>, /delwarp <nombre>, /warps | Warps compartidos |
| /setspawn [grupo] y /spawn | Fijar el spawn (opcionalmente por grupo) e ir |
| /setfirstspawn, /newbies (*) | Spawn de jugadores nuevos |
| /back | Volver al lugar anterior |

**Teletransportes**

| Comando | Qué hace |
|---|---|
| /tpa <jugador>, /tpahere <jugador> | Pedir ir hasta alguien o que venga hasta vos |
| /tpaccept, /tpdeny, /tpacancel | Aceptar, rechazar y cancelar pedidos |
| /tptoggle | No recibir pedidos de teletransporte |
| /tp <jugador> y /tphere <jugador> | Directo, para staff |
| /tppos <x> <y> <z> y /tpall | A coordenadas y traer a todos (staff) |
| /top | A la superficie sobre vos |
| /jump | A donde mirás |

**Mensajes y chat**

| Comando | Qué hace |
|---|---|
| /msg <jugador> <texto> y /r <texto> | Mensaje privado y responder |
| /mail send <jugador> <texto> y /mail read | Correo para jugadores desconectados |
| /ignore <jugador> | Ignorar a alguien |
| /nick <nombre> y /realname <nombre> | Apodo y su nombre real |
| /me, /broadcast <texto> (/bc), /helpop (*) | Acción, anuncio a todos (staff) y pedido de ayuda |
| /seen <jugador> y /whois <jugador> | Última conexión y datos de un jugador |

**Economía**

| Comando | Qué hace |
|---|---|
| /balance [jugador] (/bal) | Ver saldo |
| /pay <jugador> <monto> | Pagar |
| /baltop | Ranking de ricos |
| /eco give, take, set, reset <jugador> <monto> | Control de plata (staff) |
| /worth y /sell (*) | Valor de ítems y venta, si están configurados |

**Moderación**

| Comando | Qué hace |
|---|---|
| /kick <jugador> [motivo] | Echar |
| /ban <jugador> [motivo], /tempban <jugador> <tiempo> [motivo], /unban <jugador> | Banear, banear por un tiempo y desbanear |
| /banip <ip> y /unbanip <ip> | Banear por IP |
| /mute <jugador> [tiempo] [motivo] | Silenciar (y volver a usar para quitarlo) |
| /jail <jugador> <cárcel>, /setjail, /unjail | Cárcel |
| /vanish (/v) | Invisibilidad de staff |
| /invsee <jugador> y /enderchest <jugador> | Ver y tocar inventario y cofre de ender |
| /socialspy | Ver los mensajes privados de todos |
| /clearinventory [jugador] (/ci) | Vaciar inventario |
| /checkban <jugador> (*) | Ver si está baneado |

**Staff, utilidades y cheats**

| Comando | Qué hace |
|---|---|
| /gamemode <modo> [jugador] (/gm, /gmc, /gms, /gma, /gmsp) | Cambiar de modo |
| /fly [jugador] y /speed <1 a 10> | Volar y velocidad |
| /god | Invulnerable |
| /heal y /feed | Curar y alimentar |
| /give <jugador> <ítem> [cantidad] y /item <ítem> | Dar ítems |
| /kit [nombre] y /createkit, /delkit (*) | Kits |
| /repair [all] | Reparar lo que tenés en mano o todo |
| /enchant <encantamiento> <nivel> | Encantar el ítem en mano |
| /time, /weather | Hora y clima |
| /spawnmob <mob> [cantidad] y /spawner <mob> | Mobs y generadores |
| /kill <jugador> y /killall | Matar |
| /powertool <comando> | Asignar un comando a un ítem |
| /getpos y /near | Coordenadas y jugadores cerca |
| /list | Jugadores conectados |
| /gc | TPS, memoria y mundos cargados |
| /workbench, /anvil, /enderchest, /grindstone, /stonecutter, /loom, /smithingtable, /cartographytable | Abrir mesas desde cualquier lado |
| /essentials reload | Recarga la config sin reiniciar |

En este server los jugadores usan `/menu` para casi todo (hogares, tienda, misiones). Por eso 3y33Core oculta `/help`, `/plugins`, `/version` y similares y manda a `/menu`. Los kits, la economía y los warps de Essentials se configuran en `plugins/Essentials/config.yml`, `kits.yml` y `warps/`.

## Lands: comandos de admin y permisos (referencia)
Los comandos de jugador de Lands (reclamar, confiar, spawn, banco, naciones y guerras) están en la sección «Terrenos y clanes (Lands)» más arriba. Acá va todo lo de staff y los permisos, de la documentación oficial de Lands.
**Comandos de admin**
`<tierra o *>` significa que con `*` el comando se aplica a todas las tierras donde se pueda.

| Comando | Qué hace | Permiso |
|---|---|---|
| /lands admin menu | Abre el menú de admin | lands.admin.command.menu |
| /lands admin reload | Recarga config, roles, niveles, eventos y caché de jugadores | lands.admin.command.reload |
| /lands admin deleteinactive | Corre la expiración de jugadores y tierras inactivos | lands.admin.command.deleteinactive |
| /lands admin limits | Herramientas de los paquetes de límites | lands.admin.command.limits |
| /lands admin limits refresh | Refresca los paquetes de límites de los jugadores | lands.admin.command.limits.refresh |
| /lands admin limits migrate confirm | Pasa los permisos numéricos viejos a player-limits.yml | lands.admin.command.limits.migrate |
| /lands admin player <jugador> limits | Muestra todos los límites que aplican a un jugador | lands.admin.command.player.limits |
| /lands admin player <jugador> menu | Abre el menú de un jugador | lands.admin.command.player.menu |
| /lands admin player <jugador> delete confirm | Borra los datos de Lands de un jugador | lands.admin.command.player.delete |
| /lands admin land <tierra o *> limits | Muestra los límites de una tierra | lands.admin.command.land.limits |
| /lands admin land <tierra> menu | Abre el menú de una tierra | lands.admin.command.land.menu |
| /lands admin land <tierra o *> addrole <rol> confirm | Agrega un rol propio de roles.yml | lands.admin.command.land.addrole |
| /lands admin land <tierra o *> setflag <flag> <estado> <visitante> | Cambia una bandera de rol o natural | lands.admin.command.land.setflag |
| /lands admin land <tierra o *> resetflag <flag o all> | Devuelve una o todas las banderas a los valores de la config | lands.admin.command.land.resetflag |
| /lands admin land <tierra o *> removespawn | Saca el spawn de la tierra | lands.admin.command.land.removespawn |
| /lands admin land <tierra o *> shield set <tiempo> | Fija el escudo de guerra | lands.admin.command.land.shield.set |
| /lands admin land <tierra o *> shield modify <tiempo> | Suma o resta tiempo de escudo | lands.admin.command.land.shield.modify |
| /lands admin land <tierra o *> bank set <monto> | Fija el saldo del banco de la tierra | lands.admin.command.land.bank.set |
| /lands admin land <tierra o *> bank modify <monto> | Suma o resta saldo del banco | lands.admin.command.land.bank.modify |
| /lands admin resetworld <mundo> confirm | Borra todos los claims y spawns de un mundo | lands.admin.command.resetworld |
| /lands admin dismiss | Descarta los avisos de banderas nuevas | lands.admin.command.dismiss |
| /lands admin chatspy [tierra, nación o *] | Activa el chat espía de una tierra, nación o todas. Sin argumento lo apaga. | lands.admin.command.chatspy |
| /lands admin debug y /lands admin debug tasks | Información de depuración | lands.admin.command.debug y .debug.tasks |
| /lands admin import <plugin> | Importa claims de otro plugin de terrenos | lands.admin.command.import |
| /lands admin hologram create <contexto> <orden> | Crea un holograma de ranking donde estés | lands.admin.command.hologram.create |
| /lands admin hologram list y delete | Lista o borra los hologramas de ranking | lands.admin.command.hologram.list y .delete |
| /lands admin player <jugador> give claimblock <radio> <cantidad> [silent] | Le da ítems de bloque de claim | lands.admin.command.give.claimblock |
| /lands admin player <jugador> give camp <radio> <cantidad> [silent] | Le da ítems de campamento | lands.admin.command.give.camp |
| /lands admin player <jugador> give first-join-items | Le da los ítems de primera entrada | lands.admin.command.give.first-join-items |
| /lands admin listperms <jugador> | Lista los permisos que Lands detecta en un jugador | lands.admin.command.listperms |
| /lands admin database backup | Hace un backup de la base de datos al momento | lands.admin.command.database.backup |
| /lands admin database restore <backup> confirm | Restaura un backup | lands.admin.command.database.restore |
| /lands admin database migrate <tipo> confirm | Pasa los datos a otro tipo de base | lands.admin.command.database.migrate |
| /lands menu <jugador> | Abre el menú de Lands de otro jugador | lands.admin.command.menu |
| /lands rtp <mundo> <jugador> [skip-cooldown] | Teletransporta al azar a otro jugador | lands.admin.command.wild |

**Comandos de admin de guerras** (`/lands admin war ...`): `end <tierra o nación>` termina una guerra a la fuerza, y `koth create`, `setflag`, `setspawn <atacante o defensor>`, `remove` y `list` arman las arenas KoTH. Permisos: `lands.admin.command.war.end` y `lands.admin.command.war.koth.*`.
**Tip.** Con `lands.admin.command.edit` el staff puede manejar cualquier tierra o nación con los comandos normales, apuntando con `/lands edit <tierra>`, sin ser dueño ni miembro.
**Permisos importantes**

| Permiso | Para qué sirve |
|---|---|
| lands.command.* | Todos los comandos de jugador de /lands |
| nations.command.* | Todos los comandos de jugador de /nations |
| lands.admin.command.* | Todos los comandos de /lands admin (solo staff) |
| lands.teleport.chunk | Teletransportarse a chunks reclamados desde la lista |
| lands.teleport.land_spawn | Ir al spawn de una tierra |
| lands.teleport.random_teleport | Usar /lands rtp |
| lands.teleport.unstuck | Usar /lands unstuck |
| lands.teleport.sub_area y .rentable y .war | Ir a subáreas, áreas en alquiler y spawn de guerra |
| lands.setting.<flag> | Cambiar una bandera natural |
| lands.role.setting.<flag> | Cambiar una bandera de rol |
| lands.player.setting.<flag> | Cambiar un ajuste personal |
| lands.bypass.* | Saltarse todo lo de Lands (solo staff) |
| lands.bypass.selection, .land_claim_border, .shape, .encirclement | Saltarse reglas de selección, distancia entre tierras, forma y encierro |
| lands.bypass.cooldown.wild, .rename_land, .teleport, .spawn, .unstuck | Saltarse el tiempo de espera de cada acción |
| lands.bypass.teleport.delay | Teletransportarse sin demora |
| lands.bypass.expiration | Que nunca cuenten a ese jugador como inactivo |
| lands.admin.disabled-features | Usar funciones desactivadas para jugadores comunes |
| lands.admin.command.edit | Manejar cualquier tierra sin ser miembro |

Los permisos de teletransporte vienen en verdadero por defecto. Para bloquear uno, ponelo en falso en LuckPerms. Los demás permisos de jugador hay que darlos al grupo `default` con LuckPerms, como se hizo en este server.

## QuickShop-Hikari: comandos y permisos (referencia)
Todo se maneja con `/quickshop` (alias `/qs`). Los permisos salen del archivo `plugin.yml` oficial del plugin; los comandos, de lo que dice cada permiso. Los permisos `quickshop.player` (jugadores) y `quickshop.moderator` (staff) agrupan casi todos los de abajo.
**Crear y administrar una tienda propia**

| Comando | Qué hace | Permiso |
|---|---|---|
| /qs create [precio] | Crea una tienda en el cofre que mirás con el ítem de la mano | quickshop.create.cmd |
| /qs price <precio> | Cambia el precio | quickshop.create.changeprice |
| /qs amount <cantidad> | Cambia cuántas unidades se venden por compra | quickshop.create.changeamount |
| /qs item | Cambia el ítem de la tienda por el que tenés en la mano | quickshop.create.changeitem |
| /qs sell y /qs buy | Pasa la tienda a modo vender (los jugadores le compran) o comprar (los jugadores le venden) | quickshop.create.sell y quickshop.create.buy |
| /qs freeze | Congela la tienda: nadie puede comerciar con ella | quickshop.togglefreeze |
| /qs name <nombre> | Le pone nombre a la tienda | quickshop.shopnaming |
| /qs sign | Cambia el tipo de cartel | quickshop.sign |
| /qs currency | Cambia la moneda de la tienda | quickshop.currency |
| /qs staff | Agrega o saca ayudantes de la tienda | quickshop.staff |
| /qs permission | Maneja los permisos de cada ayudante | quickshop.permission |
| /qs benefit | Reparte las ganancias con otros jugadores | quickshop.benefit |
| /qs history | Historial de compras y ventas de la tienda | quickshop.history |
| /qs toggledisplay | Muestra u oculta el ítem flotante de esa tienda | quickshop.toggledisplay |
| /qs transferownership <jugador> | Le pasa una tienda a otro jugador | quickshop.transferownership |
| /qs transferall <jugador> | Le pasa todas tus tiendas a otro jugador | quickshop.transferall |
| /qs removeall | Borra todas tus tiendas | quickshop.removeall.self |
| /qs suggestprice | Te sugiere un precio recomendado | quickshop.suggestprice |
| /qs preview | Mira el ítem en una vista previa | quickshop.preview |

**Para comprar y buscar**

| Comando | Qué hace | Permiso |
|---|---|---|
| Clic derecho en la tienda y escribir la cantidad en el chat | Comprar o vender | quickshop.use |
| /qs find <ítem> | Busca la tienda más cercana que vende ese ítem | quickshop.find |
| /qs browse | Catálogo de tiendas con teletransporte (el /tienda del server) | quickshop.browse y quickshop.browse.teleport |
| /qs favorite y /qs favorite list | Guarda tiendas como favoritas | quickshop.favorite |
| /qs watch y /qs watch list | Te avisa de cambios en una tienda | quickshop.watch |
| /qs avoid y /qs avoid list | Oculta tiendas que no querés ver | quickshop.avoid |
| /qs tag add, delete, list, shops, tagged, clear, clearall, purge | Etiquetas para ordenar tus tiendas | quickshop.tag.* |

**Para el staff**

| Comando | Qué hace | Permiso |
|---|---|---|
| /qs supercreate | Crea una tienda saltándose las protecciones | quickshop.create.admin |
| /qs unlimited | Hace que una tienda tenga stock infinito | quickshop.unlimited |
| /qs refill <cantidad> | Repone el ítem de la tienda que mirás | quickshop.refill |
| /qs empty | Vacía la tienda que mirás | quickshop.empty |
| /qs setowner <jugador> | Cambia el dueño de una tienda | quickshop.setowner |
| /qs removeall <jugador> | Borra todas las tiendas de un jugador | quickshop.removeall.other |
| /qs removeworld <mundo> | Borra todas las tiendas de un mundo | quickshop.removeworld |
| /qs reload | Recarga la configuración | quickshop.reload |
| /qs clean y /qs cleanghost | Limpian tiendas rotas o fantasma | quickshop.clean y quickshop.cleanghost |
| /qs purge | Limpieza masiva de tiendas | quickshop.purge |
| /qs lookup | Busca en los datos de tiendas | quickshop.lookup |
| /qs inventory | Revisa el inventario de una tienda | quickshop.inventory |
| /qs export | Exporta la base de datos a un archivo | quickshop.export |
| /qs database | Herramientas de base de datos | quickshop.database |
| /qs recovery | Recupera datos. Peligroso: no usar sin saber qué se hace | quickshop.recovery |
| /qs reset | Reinicia valores del plugin | quickshop.reset |
| /qs toggledisplayall | Muestra u oculta los ítems flotantes de todas tus tiendas | quickshop.toggledisplayall |
| /qs paste | Sube la información del server a un sitio de pegado. Puede mostrar datos sensibles. | quickshop.paste |
