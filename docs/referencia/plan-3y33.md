> **Documento de referencia histórico.** Plan general del servidor: visión, recorrido del jugador nuevo, qué estaba listo en ese momento, spawn, estilo visual, RTP, hologramas y NPCs, economía, rangos, moderación y orden de trabajo.
> Se escribió durante la construcción del server y **puede estar desactualizado** (por ejemplo: Lands ya no limita el tamaño de los terrenos, los grupos Sheriff/Supreme/Global ya no existen y los trabajos llegan al nivel 130). Cuando algo de acá contradiga a `docs/01` a `docs/09` o al código, mandan esos.

# Plan 3y33 SMP
Lo que le falta a 3y33 para ser un SMP completo, lindo a simple vista y fácil de entender, con plugins gratis y lo nuestro propio.

## La visión
3y33 tiene que ser el SMP argentino donde entrás y en dos minutos sabés qué hacer, todo se ve prolijo y nadie gana por pagar. Cada cosa nueva se mide contra tres pilares:

- **Simple:** todo sale de /menu o de un NPC; nada de comandos raros ni textos en inglés. Si hay que explicarlo dos veces, está mal diseñado.

- **Lindo:** un solo estilo en todo el server (colores, fuente, sonidos, menús), como si fuera un juego hecho por nosotros.

- **Justo:** lo que da ventaja se gana jugando (niveles, misiones); lo que se paga es solo visual. El staff sanciona parejo y a la vista.
**Es un SMP vanilla.** El juego es Minecraft normal; lo que agregamos solo ayuda (terrenos, RTP, reglas, menús). Al morir se pierde todo, como en vanilla. Nada de tops, tienda del server, mundos extra ni minijuegos. El comercio es entre jugadores, con tiendas de cofre en sus propios terrenos.

## Recorrido del jugador nuevo
Los primeros minutos deciden si alguien vuelve; cada paso tiene que llevar solo al siguiente, sin que tenga que preguntar.
Hoy ya andan los pasos 1, 2, 7 y 8. Faltan las reglas en pantalla, el spawn, la Guía y el portal.

## Listo hoy
Todo esto ya está andando en el server; no hace falta tocar nada.

| Qué | Cómo funciona |
|---|---|
| /reglas | Abre un libro con las 8 reglas y las sanciones. Se edita en la config de 3y33Core. |
| Anuncios | Cada 6 minutos sale uno al azar (menú, Discord, misiones, terrenos, rangos, reglas), con links clickeables. |
| /report | /report <jugador> <motivo>, con autocompletado. Avisa al staff con botones [Ir] y [Historial] (CoreProtect), y espera 60 s entre reportes. El staff ve los últimos con /reportes. |
| /help bloqueado | Los jugadores no pueden usar /help, /pl, /version, /me y similares; se los manda a /menu. Tampoco ven los comandos con prefijo (essentials:...) en el tab. |
| /helpop | Sigue andando, con 60 s de espera entre mensajes para que no lo spameen. |
| Login | AuthMe se queda: es el que da /premium. 3y33Login solo le pone los títulos en pantalla, así que no se pisan. |
| Limpieza | Saqué permisos de plugins que ya no están (GriefPrevention, Homestead, LogInto) y moví sus carpetas a _desactivados. |
| DEV | Queda con todos los permisos, como pediste. |
| Reglas en pantalla | Ventanas nativas con FancyDialogs: Siguiente, Anterior y Acepto. Los nuevos no se mueven ni hablan hasta aceptar; Bedrock ve el libro. |
| Zona del spawn | Protegida con WorldGuard (X -157 a 108, Z -180 a 27). Nadie puede crear terrenos ahí ni a menos de 48 bloques. |
| Hologramas y NPCs | FancyHolograms y FancyNpcs instalados; se ubican cuando el spawn esté construido. |

## Spawn
Un spawn chico y claro: aparecés en una plaza y desde ahí se ven las cuatro zonas, cada una con un cartel y un holograma que dice qué hay.
Todo protegido con WorldGuard: sin romper, sin PvP, sin mobs y sin fuego. El tamaño ideal es de unos 100 × 100 bloques, así se recorre en segundos y no carga lento en Bedrock. La construcción es lo único que no puedo hacer bloque por bloque: o la arma alguien del equipo, o busco un diseño gratis con licencia libre y lo pego con FAWE.

## Estilo visual
Estas son las reglas de diseño que ya usan /menu, /terrenos, /discord y los anuncios; todo lo nuevo las sigue para que el server se sienta de una sola pieza.

| Elemento | Regla |
|---|---|
| Colores | Rojo 3y33 #E03131 para títulos, rojo claro #FF6B6B para links y valores, blanco para texto, gris para lo secundario. Rangos: Sheriff #FFAA00, Supreme #55FFFF, Global dorado #FFD24D. |
| Prefijo | 3y33 » en rojo oscuro negrita, solo en mensajes del sistema. |
| Avisos | Dos líneas: título en negrita y una frase con el link subrayado, como /discord. Sin líneas vacías ni cajas de guiones. |
| Menús | Título Menú » Sección, fondo de vidrio negro, « Volver abajo a la izquierda y Cerrar en el medio. Nombres con degradado y descripción en líneas » Dato: valor y ▸ Click para…. |
| Sonidos | Click suave en cada botón (UI_BUTTON_CLICK 0.5/1.2); campanita en anuncios; sonido de nivel solo para logros. Nunca sonido al abrir un menú. |
| Fuente | Press Start 2P (pixel) para títulos grandes y carteles de terreno, desde el resource pack. |
| Iconos | Stickers propios de 8×8 en el pack; un ícono por sección del menú, siempre el mismo. |
| Idioma | Español argentino con voseo en todo, incluidos los mensajes de los plugins. |

## Reglas en pantalla
Ya está andando desde el 22/10 con FancyDialogs (ventanas nativas de Minecraft) y 3y33Core, que bloquea al jugador hasta que acepta.

- **Portada:** banner de 3y33 y “Antes de jugar, leé las reglas”. Botón Empezar.

- **Una o dos reglas por pantalla:** ícono, título en rojo, explicación corta, botones ◀ Anterior y Siguiente ▶ y el contador “2 / 7”.

- **Sanciones:** qué pasa si se rompen.

- **Final:** banner grande y dos botones. Acepto cierra con sonido y título de bienvenida; No acepto lo saca del server con un mensaje amable.
La primera vez es obligatoria: aparece después del login, no se cierra con Escape y no se puede jugar hasta aceptar. Queda guardado quién aceptó; si cambian las reglas se vuelven a pedir una vez. /reglas abre lo mismo sin el botón de aceptar. En Bedrock se muestra con los formularios de Bedrock (a confirmar). Falta el logo del server en PNG con fondo transparente.

## Salir a explorar: RTP
Salir lejos del spawn es lo primero que busca un jugador nuevo de SMP. Ya está andando: un /rtp simple en 3y33Core que solo funciona en el mundo normal (ni Nether ni End). Probamos EzRTP y Simple RTP y no convencieron: uno era demasiado complejo y el otro fallaba en 26.2

| Qué | Cómo |
|---|---|
| Cómo se usa | Portal en la zona Portales del spawn, /rtp y botón en /menu. En Bedrock, igual. |
| Dónde cae | En el mundo principal, entre 500 y 9.500 bloques del centro (el borde está en 10.000 y ya está generado), y en el Nether hasta 2.400. |
| Lugar seguro | Nunca en agua, lava, océano ni arriba de árboles, y nunca en el spawn (WorldGuard). Puede caer cerca de un terreno, pero el terreno sigue protegido. Busca el lugar sin trabar el server. |
| Espera | 3 segundos quieto, con cuenta regresiva en pantalla y título al llegar con las coordenadas. |
| Cooldown por rango | Default 10 min, Sheriff 7, Supreme 5, Global 3. Staff sin espera. |
| Costo | Gratis el primero de cada día; después $100, para que la plata tenga uso. |
| Primer ingreso | El NPC Guía manda a los nuevos con un RTP gratis y un aviso: “Protegé tu lugar con /terrenos”. |

## Hologramas y NPCs
Propuesta: **FancyHolograms** y **FancyNpcs**. Son gratis, del mismo autor, están en Modrinth y Hangar, se actualizan rápido a cada versión nueva y funcionan con Bedrock. Antes de instalarlos confirmo que tengan versión para 26.2; si no, uso DecentHolograms y Citizens.
Cada NPC es una sola tarea, con nombre y holograma arriba, y abre un menú o ejecuta un comando:

| NPC | Qué hace al tocarlo |
|---|---|
| Guía | Abre /menu y, la primera vez, un recorrido corto: terrenos, misiones, niveles. |
| Terrenos | Abre /terrenos. |
| Misiones | Abre /misiones. |
| Niveles y rangos | Abre /nivel. |
| Reglas | Abre las reglas en pantalla. |
| Explorar | Te manda con RTP a un lugar al azar del mundo. |

Hologramas fijos, pocos: bienvenida en el punto de aparición, uno arriba de cada NPC con lo que hace, y el link de Discord. Sin tops.

## Tiendas
Una sola forma de comerciar: cada jugador arma su tienda con cofres en su terreno y vende o compra con los demás. El server no compra ni vende; `/sell` ya no está.
**Tienda de jugadores: QuickShop-Hikari** (gratis, en Modrinth). Ponés un cofre, le pegás con el ítem y queda armada: el ítem flota arriba del cofre y un cartel muestra precio y stock. Se compra y vende tocando el cofre, con menú en chat y compatible con Bedrock.

- Solo se pueden poner tiendas dentro del propio terreno (o en uno donde te dieron permiso).

- Límite de tiendas por rango: Default 30, Sheriff 40, Supreme 50, Global 60 (alto a propósito; solo frena abusos).

- Para encontrar tiendas: `/qs find <ítem>` te dice dónde venden algo, y cada dueño puede poner un punto de teletransporte en su terreno.

## Economía
La plata tiene que costar ganarla y tener en qué gastarla; si entra más de lo que sale, en un mes todos son millonarios y nada vale nada.

| Entra plata por | Sale plata por |
|---|---|
| Subir de nivel (ya está: $300 a $2.750 por nivel) | Comprar chunks extra (ya está: $500 + $250 por cada uno) |
| Misiones diarias y semanales (ya está) | Comprar a otros jugadores en sus tiendas |
| Vender a otros jugadores en tu tienda | RTP después del primero gratis del día |
| Recompensa diaria | Impuesto chico en las ventas entre jugadores (2 %) |

A las dos semanas reviso /baltop y la plata total del server y ajusto precios en la config, sin reiniciar.

## Rangos pagos y cosméticos
Lo que se vende tiene que ser cosmético o de comodidad. Las reglas de uso comercial de Mojang no permiten vender ventajas de juego: ítems, kits fuertes, /fly en supervivencia ni comandos que den recursos ([resumen de las reglas](https://space-node.net/blog/minecraft-eula-server-monetization-rules-2026)). Por eso los rangos por nivel (Sheriff, Supreme, Global) siguen siendo gratis, y los pagos van en una escalera aparte, solo visual.

| Rango pago | Qué incluye | Precio sugerido |
|---|---|---|
| ✦ VIP | Prefijo propio, color de chat, /nick con colores, /hat, mensaje de entrada, 1 partícula, rol en Discord | ARS 2.000 por mes |
| ✦ VIP+ | Todo lo de VIP, 5 partículas, tags extra en /tags, mascota cosmética, sentarse y acostarse (GSit) | ARS 4.000 por mes |
| ✦ Leyenda | Todo lo anterior, todas las partículas, efecto al morir y matar, nombre con degradado, tag único | ARS 8.000 por mes o ARS 25.000 de por vida |

Los precios son una referencia para arrancar; se ajustan con lo que pague la comunidad.
**Cosméticos sueltos:** tags de chat (ModernTags ya está instalado), colores de nombre, partículas, sombreros y stickers de terreno exclusivos. También se pueden ganar jugando (misiones, eventos), así los que no pagan tienen algo para conseguir.
**Cobro:** Tebex tiene plan gratis, entrega el rango solo cuando se paga y acepta jugadores no premium por nombre. Hay que ver si conviene Tebex o una tienda propia con Mercado Pago, que cobra menos comisión en Argentina pero hay que armarla y entregar los rangos a mano o con un bot.
**Lo que no vender****í****a:** más chunks, más terrenos, más homes, /fly, /feed, /heal, kits con ítems ni plata del juego. Eso queda para los rangos por nivel.

## Comunidad y moderación
Lo que hace que la gente vuelva todos los días, y lo que necesita el staff para cuidar el server.

| Qué | Para qué | Cómo |
|---|---|---|
| Recompensa diaria | Que entren todos los días | Menú con racha de 7 días: plata y XP de nivel, con NDailyRewards. |
| Votos | Atraer gente nueva desde las listas de servers | VotingPlugin y NuVotifier: premio chico por voto. |
| Eventos | Juntar a la comunidad | Drop party, torneo de pesca, construcción por tema, apertura del End. Premios cosméticos y plata. |
| Sanciones | Que el staff sancione parejo | Tabla fija (spam = mute 10 min, insulto = mute 1 h, hacks = ban 7 días, reincidencia = permanente) cargada como plantillas en LibertyBans, con historial por jugador. |
| Chat de staff y registros | Coordinar sin que lo vean los jugadores | /sc para el staff y aviso de reportes y bans en un canal de Discord. |
| DiscordSRV | Unir el chat del juego con Discord | Falta crear el bot y pasarme el token. |
| Backups | No perder el mundo nunca | Copia diaria del mundo y de la base de datos, guardando las últimas 7, fuera del disco del server si se puede. |

## TAB, chat y nombres
Es lo que el jugador ve todo el tiempo, así que es lo primero que hace que el server se vea profesional. Todo esto se hace con TAB y LPC, que ya están instalados.

| Dónde | Propuesta |
|---|---|
| Lista de jugadores (Tab) | Arriba el banner de 3y33 y “Jugadores: 12”; abajo ping, link de Discord y /menu. Ordenada por staff, rangos pagos, rangos por nivel. |
| Nombre sobre la cabeza | Color del rango y el nivel al lado (“Steve · 23”). Staff con su etiqueta. |
| Chat | [Rango] Nombre » mensaje. Pasando el mouse por el nombre: nivel, rango, tiempo jugado y “click para mandar mensaje”. |
| Entradas y salidas | Solo para jugadores con rango; el primer ingreso de alguien nuevo se anuncia con su número (“Bienvenido Steve, jugador n.º 214”). |
| Barra lateral | Ninguna fija: tapa la pantalla. Un /stats que la muestre unos segundos con plata, nivel y misiones. |
| Chat limpio | Filtro de malas palabras y de links, y freno al spam (mismo mensaje seguido o muy rápido). |

## Plugins
Regla: si existe un plugin gratis, conocido y actualizado, se usa ese y lo traducimos y pintamos con nuestro estilo. Programamos solo lo que ningún plugin hace bien. Revisé cada uno en Modrinth y Spigot el 22/10: versión más nueva y si soporta 26.2.

| Para qué | Plugin | 26.2 | Estado |
|---|---|---|---|
| Reglas en pantalla | FancyDialogs | Sí | Instalado |
| Hologramas | FancyHolograms | Sí | Instalado |
| NPCs | FancyNpcs | Sí | Instalado |
| RTP | EzRTP (descartado; usamos /rtp propio) | Sí | Próximo |
| Tiendas en los terrenos | QuickShop-Hikari | Sí | Próximo |
| Recompensa diaria | NDailyRewards | Sí | Próximo |
| Sanciones | LibertyBans | Sí | Después |
| Votos | VotingPlugin + NuVotifier | A probar | Después |
| Partículas (rangos pagos) | PlayerParticles | Sí | Después |
| Backups | DriveBackupV2 | A probar (última: 26.1.2) | Falta el Drive |
| Estadísticas (solo staff) | Plan | Sí | Después |
| Discord | DiscordSRV (ya instalado, apagado) | Sí | Falta el bot |

Fuera del plan por no ser vanilla: tops (ajLeaderboards, ya desinstalado), tienda del server para comprar, mundo de recursos y minijuegos.
Lo que programamos nosotros es poco: las reglas en pantalla con Acepto (no hay un plugin maduro que use las ventanas nuevas), el filtro de chat y antispam (los gratis son flojos o están abandonados), y lo que ya está hecho: terrenos, /discord, anuncios y /report. Los menús siguen con DeluxeMenus.
Para revisar: ItemJoin (si el ítem del menú lo da 3y33Core, sobra) y DiscordSRV, que vuelve cuando haya bot.

## Lo técnico
Lo que no se ve pero decide si el server aguanta, no se cae y no pierde nada. Revisé la configuración actual el 22/10.

| Área | Cómo está | Qué hacer |
|---|---|---|
| Backups | No hay ninguno | Diarios a Google Drive, guardando 7. Es lo más urgente. |
| Anti x-ray | Activo (modo 2 de Paper) | Nada, está bien. |
| Bots y cuentas | Sonar y AuthMe con /premium | Limitar cuentas por IP a 2. |
| Hacks | GrimAC con alertas al staff | Mandar las alertas también a Discord cuando haya bot. |
| Mundo | Borde a 10.000 del centro (20.000 de lado), generado; Nether a 2.500 | Cerrar el End hasta abrirlo con un evento, así el dragón no lo mata el primero que llega. |
| Rendimiento | Vista 10, simulación 8 | Bien para 20; si pasa de 40 jugadores, vista 8. Medir con spark en horas pico. |
| Granjas | Valores normales de Minecraft, sin límite propio | Límite de animales por chunk y aviso si una granja laguea. Va en las reglas. |
| Cupo | Máximo 20 jugadores | Subir a 50 antes de abrir. |
| Dormir | Tienen que dormir todos (100 %) | Bajar a 30 %: con mucha gente nunca se hace de día. |
| Bedrock | El resource pack es solo para Java | Pasar el pack a Bedrock con Geyser para que vean la fuente y los stickers. |
| Configuraciones | Viven solo en el server | Copia en un repositorio privado de GitHub, para volver atrás si algo se rompe. El repo del pack es público y no sirve para esto. |
| Avisos de caída | Nadie se entera si se cae | Aviso a Discord cuando el server se cae o vuelve. |

## Lanzamiento y crecimiento
Un server sin gente no sirve aunque esté perfecto, y la primera impresión se da una sola vez: conviene abrir con todo listo y una fecha anunciada.

- **Prueba cerrada (1 semana):** amigos y staff juegan normal y anotan errores en un canal de Discord.

- **Fecha de apertura:** anunciada con una cuenta regresiva en Discord y redes; el primer día con evento y premios.

- **Listas de servers:** publicar en las listas argentinas y latinas con banner y votos activados.

- **Videos cortos:** clips del spawn, de los terrenos y de eventos para TikTok, Shorts y Reels.

- **Invitá a un amigo:** premio para los dos cuando el invitado llega a nivel 5.

- **Discord ordenado:** anuncios, reglas, soporte con tickets, sugerencias, clips y el chat del juego conectado.

- **Temporadas:** cada 4 a 6 meses, evento grande o mundo nuevo para que vuelvan los que se fueron.
Con Plan medimos cuántos vuelven al día siguiente y a la semana; si se van rápido, el problema está en los primeros minutos (el recorrido de arriba).

## Orden de trabajo
Primero lo que protege el server y lo que ve todo el que entra; después lo que da ganas de quedarse; al final lo que se vende.

- Backups diarios a Google Drive.

- Recompensa diaria; cupo de 50 y End cerrado (dormir se ve más adelante). RTP y tiendas ya están.

- TAB y chat con el estilo nuevo.

- Spawn: construirlo (zona y protecciones ya hechas) y poner el punto de aparición mirando al frente.

- NPCs y hologramas del spawn, con la Guía para los nuevos.

- Sanciones con LibertyBans, filtro de chat y votos.

- Rangos pagos y cosméticos, con la tienda de cobro.

- Bot de Discord.

- Prueba cerrada de una semana y apertura con fecha.

## Decisiones pendientes

- Spawn: ¿lo construye alguien del equipo o busco un diseño gratis (schematic con licencia libre) y lo pego con FAWE? ¿Qué estilo y tamaño?

- Backups: ¿cuánto espacio queda en el plan de Baires Host y si hay un Google Drive o similar para guardar copias afuera?

- Rangos pagos: ¿nombres, precios y si cobramos con Tebex o Mercado Pago?

- Economía: ¿cuánta plata da el server por hora jugada? Define los precios de la tienda.

- Mundo de recursos: ¿lo queremos? Ocupa disco.

- Votos: ¿en qué listas de servers lo publicamos?

- Logo o banner del server en PNG con fondo transparente, para las reglas, el TAB y los hologramas.

- Fecha tentativa de apertura.
