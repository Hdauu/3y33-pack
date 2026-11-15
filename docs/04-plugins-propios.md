# Plugins propios

Los cuatro plugins que **están instalados** (Core, Login, Shops, Trabajos) y los dos **históricos** (Tierras, Tiendas). El de Trabajos, por ser el más grande, tiene su propio documento: [`05-trabajos.md`](05-trabajos.md). Todos usan la API de Paper (`api-version: 1.21`), el paquete `ar.com.tresyetres.<plugin>` y se compilan con Maven (menos Login). Cómo compilar y desplegar: [`03-operacion.md`](03-operacion.md).

| Plugin | Versión | Compila con | Dependencias en el server |
|---|---|---|---|
| 3y33Core | 1.14.3 | Java 21 | Opcionales: PlaceholderAPI, AuthMe, floodgate, FancyDialogs, FancyHolograms, FancyNpcs. Necesita la expansión **vault** de PlaceholderAPI (`%vault_rank%`) para reconocer el rango |
| 3y33Login | 1.0.2 | `javac` a mano (Java 21) | AuthMe |
| 3y33Shops | 2.12.1 | Java 25 | Obligatorias: Vault, Lands. Opcional: floodgate |
| 3y33Trabajos | 1.11.2 | Java 25 | Obligatoria: Vault. Opcionales: Essentials, PlaceholderAPI |

---

## 3y33Core

**Para qué sirve:** todo lo transversal del server que no es tiendas ni trabajos.

**Comandos**

| Comando | Quién | Qué hace |
|---|---|---|
| `/reglas` (`/rules`, `/normas`) | todos | Abre el libro de reglas. Las reglas están en `config.yml` (`reglas.paginas`, formato MiniMessage) |
| `/report <jugador> <motivo>` | todos | Avisa al staff con botones **[Ir]** (teletransporte) e **[Historial]** (consulta de CoreProtect de la última hora). Espera configurable |
| `/reportes` | staff | Los últimos reportes (`plugins/3y33Core/reportes.yml`) |
| `/rtp` (`/wild`, `/explorar`) | todos | Viaje al azar solo en el mundo normal (`rtp.mundo`), con espera de 3 s, radio entre 500 y 9500 bloques desde el centro, 20 intentos para hallar un lugar seguro y **cooldown por rango** (`rtp.cooldown`) |
| `/pvp [on\|off]` | todos | PvP **opcional por jugador**, con etiqueta de combate: si te desconectás peleando, morís y soltás todo (`pvp.combate-segundos`, `pvp.espera-cambio-segundos`) |
| `/color` (`/colornombre`) | VIP, VIP+ y staff | Elegir el color/degradado del nombre y estilos de chat. Los presets que piden permiso (por ejemplo `argentina`) se otorgan con `tresyetres.estilo.argentina` |
| `/editor` (`/editar`, `/mover`) | staff | Panel de botones para mover hologramas (FancyHolograms) y NPC (FancyNpcs) leyendo sus posiciones en vivo |
| `/3y33core reload\|anuncio\|aceptar\|rechazar\|holograma <plantilla> [nombre]\|spawn` | admin | Administración (recarga, anuncio manual, plantillas de hologramas, armar los hologramas del spawn) |

**Permisos propios:** `tresyetres.staff` y `tresyetres.admin` (por defecto solo op), `tresyetres.color` (acceso al color sin ser VIP), `tresyetres.estilo.argentina`.

**Piezas del código**

| Clase | Hace |
|---|---|
| `Reglas` | Reglas **obligatorias en pantalla** con FancyDialogs: el jugador nuevo no puede moverse, hablar ni usar comandos hasta aceptar. Subí `reglas-dialogo.version` para volver a pedirlas a todos. En Bedrock se muestra el libro. Aceptaciones en `reglas-aceptadas.yml` |
| `Core` | Registro de comandos, anuncios cada `anuncios.intervalo-minutos` (15 hoy, con links clickeables), `/report`, bloqueo de comandos (`comandos-bloqueados`: `help`, `pl`, `version`, etc. mandan a `/menu`) y ocultamiento de los comandos con prefijo (`essentials:…`) |
| `Nombre`, `Color` | El **nombre global** del jugador: insignia de rango + degradado de su color. Se fija como *display name*, así lo usan Essentials y los mensajes de muerte. El rango se lee del **grupo principal de LuckPerms** vía `%vault_rank%`: `vip`→U+E300, `vipplus`→U+E301, `moderador`→U+E302, `dev`→U+E303, `admin`→U+E304, `owner`→U+E305 (caracteres de la fuente del pack, ver `06-resource-pack.md`). Los colores por defecto de cada rango están en `Color.RANGOS` |
| `ChatEstilo` | Color de los mensajes: VIP con colores sólidos, VIP+ y staff con degradados, y el estilo **Argentina** (letras con las tres franjas celeste, blanco y celeste, hechas con las fuentes `3y33:bandera` y `3y33:bandera_g` del pack). Un renderer reemplaza el texto visible del mensaje sin tocar los eventos *hover* |
| `Beta` | Regalo por jugar la beta: el estilo Argentina. Se apaga con `beta.activa: false` |
| `Pvp`, `PvpExpansion` | PvP opcional y los placeholders `%3y33_pvp%`, `%3y33_insignia%`, `%3y33_nombre_legacy%` y `%3y33_n_<color>%` |
| `Rtp` | `/rtp` simple y seguro |
| `Spawn`, `Plantillas`, `Editor`, `HologramaClics` | Hologramas prediseñados del spawn (degradados, fondo oscuro, miran al jugador), editor con botones, y carteles que se abren con click |

**Datos en el server:** `config.yml` (editable), `chat.yml`, `colores.yml`, `pvp.yml`, `reglas-aceptadas.yml`, `reportes.yml`.

**Cosas a revisar:** los cooldowns de `rtp.cooldown` nombran rangos que ya no existen (`sheriff`, `supreme`, `global`); los anuncios de misiones nombran `/misiones` (de Aurora). Ver `09-pendientes.md`.

---

## 3y33Login

Plugin mínimo (una clase, `TresLogin`) que le pone **títulos en pantalla** al login de AuthMe, sin reemplazarlo: AuthMe sigue dando `/login`, `/register` y `/premium`. Configuración en `plugins/3y33Login/config.yml` (colores con `&c` o `&#RRGGBB`, se aplica al reiniciar): título fijo mientras no iniciaste sesión, título de bienvenida y líneas de chat al loguearse.

No tiene `pom.xml`: se compila con `javac` contra clases mínimas de Paper y AuthMe (carpeta `stubs/`, que no va en el jar). Pasos en `plugins/3y33Login/README.md`.

**Cosas a revisar:** el chat de bienvenida nombra `/misiones` y `/nivel` (de Aurora) y usa los placeholders `%3y33_insignia%` y `%3y33_nombre_legacy%` (del Core).

---

## 3y33Shops

**Para qué sirve:** tiendas de cofre con **motor propio** (reemplazó a QuickShop). Cada tienda es un cofre (o cofre doble) con **un solo ítem** a la venta o compra, y solo puede existir **dentro de una tierra de Lands** donde el dueño es miembro.

**Comandos:** `/tienda crear|eliminar|buscar [ítem]|mostrar vacias|bajostock|punto` (alias `/tiendas`) para jugadores; `/tiendaadmin lista|info|borrar|rotar|revisar|recargar` para staff.

**Cómo se usa:** clic derecho a la tienda abre la ventana **TIENDA** (comprar, vender, cantidad personalizada, almacenamiento). El dueño tiene ajustes: precios, bloqueos, administradores, historial, avisos y un mensaje en el holograma. `/tienda buscar` lista las tierras con tiendas y lleva al **punto de viaje** de la tierra (uno por tierra, compartido por todas sus tiendas; se arma en dos pasos y tiene que estar dentro de la tierra).

**Piezas del código**

| Clase | Hace |
|---|---|
| `ShopsPlugin` | Arranque y comandos |
| `Almacen` | Guarda las tiendas en **SQLite** (`tiendas.db`) y las mantiene en memoria; todo el acceso a la base va por un solo hilo |
| `Operaciones` | Compras y ventas en el hilo principal, de una sola vez: verifica todo antes, mueve plata e ítems y **revierte** si algo falla |
| `Economia` | Plata por Vault; los montos entran en **centavos** |
| `Integridad` | Cada `integridad.revisar-cada-segundos` (120) comprueba que cada tienda siga dentro de una tierra donde el dueño es miembro. Si no, la **suspende** (no se usa ni aparece en el buscador) y borra los puntos de viaje que quedaron fuera de su tierra. Así nadie usa tiendas o puntos como teletransportes gratis |
| `LandsPuente` | Habla con la API de Lands **por reflexión** (compila sin el jar de Lands) |
| `Holograma` | Ítem flotante, nombre, precios, "tienda vacía" y mensaje del dueño. Java ve un `ItemDisplay` y Bedrock el ítem común; el texto son armor stands invisibles (Geyser los traduce bien). Solo existe mientras hay jugadores cerca |
| `VentanaTienda`, `Banner`, `Ajustes` | Ventanas. `ventana-banner` (`staff` / `todos` / `no`) decide quién ve el fondo del resource pack; los demás ven la ventana común |
| `Proteccion` | Las mismas protecciones que daba QuickShop (explosiones, tolvas, droppers, pistones, fuego, cofres que se juntan, etc.) |
| `Buscador`, `Puntos`, `Mostrar`, `Resumen` | Buscador de tiendas, punto de viaje, resalte de tiendas vacías o con poco stock, y el resumen que recibe el dueño al entrar |
| `Cabezas`, `Entrada`, `EntradaBedrock` | Cabezas con la skin guardada (el server no es premium y no puede pedirla a Mojang) y entrada de texto por chat o por formulario de Bedrock |

**Configuración (`config.yml`):** `maximo-tiendas-por-tierra` (30, hoy sin uso real: Francisco decidió que **no hay límite** para nadie; falta confirmarlo y sacarlo si corresponde), `precio-maximo`, `cantidad-maxima-personalizada`, ajustes del holograma, `buscar-espera-segundos`, `integridad`, `ventana-banner`.

**Pendiente de decidir:** pasar `ventana-banner` de `staff` a `todos`.

---

## 3y33Trabajos

Ver [`05-trabajos.md`](05-trabajos.md).

---

## Históricos (no instalados)

Quedan en el repo por el código y por si sirven de referencia. **No se compilan en uso real**, pero sus workflows siguen en `.github/workflows/`.

- **3y33Tierras (1.12.1):** extras para el plugin de tierras anterior (*LandClaimPlugin*): compra de chunks con costo creciente, mobs, cooldown de teletransportes, **hermandades**, menús de terrenos, stickers de pixel art (`:corazon:` y similares) y un panel de tienda. Lo reemplazó **Lands**. La copia de seguridad del plugin viejo está en `respaldo-landclaim/` del server.
- **3y33Tiendas (1.3.1):** reglas para **QuickShop-Hikari** sobre tierras de Lands y hologramas propios por tienda. Lo reemplazó **3y33Shops**.

Si se quiere reutilizar alguna idea (por ejemplo las hermandades), mirar `Hermandades.java` y `Stickers.java`.
