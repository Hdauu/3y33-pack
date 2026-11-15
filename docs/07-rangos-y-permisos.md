# Rangos, permisos y límites de tierras

Estado de **LuckPerms** y **Lands** leído del server el 15/11/2026. Para volver a verlo: `lp export <archivo>` (genera un `.json.gz` en `plugins/LuckPerms/`) y para las tierras los archivos de `plugins/Lands/`. **Probá siempre con una cuenta sin permisos**: los grupos con `*` no ven costos ni límites.

## 1. Grupos de LuckPerms

| Grupo | Peso | Hereda de | Qué tiene |
|---|---|---|---|
| `default` | 10 | — | Lo básico de jugador: homes, warps, `/tpa`, `/msg`, `/pay`, kit inicial (`essentials.kits.inicial`), `/rules`, `/spawn`, comandos y ajustes de Lands, tags de ModernTags propios. Tiene permisos viejos de QuickShop (ver 4) |
| `donador` | 5 | — | Color en el chat, `/nick`, `/hat` (cosmético). **No lo hereda nadie ni figura en el track**: parece un resto de una idea vieja |
| `vip` | — | `default` | Sin permisos propios. Lo que da lo resuelven **Lands** (chunks gratis, ver 3) y **3y33Core** (color del nombre y del chat, por rango). El `/nick` y `/hat` del plan los da hoy el grupo `donador`, que no lo hereda nadie |
| `vipplus` | — | `vip` | Igual que `vip`: lo que suma lo resuelven Lands y Core |
| `moderador` | 200 | `global` (**no existe**, ver 4) | CoreProtect (inspeccionar, rollback), `/ban`, `/tempban`, `/mute`, `/kick`, `/invsee`, `/vanish`, `/tp`, `/back`, ayuda de staff, Grim alertas y permisos de administración de Lands (`lands.admin.command.*`, `lands.bypass.*` de interacción, entrar y teletransporte). Más permisos viejos de `landclaim.*`, `tierras.*`, `ezrtp.*` |
| `dev` | 250 | `moderador` | `*`, `lands.admin.*`, `lands.bypass.*`, `nations.admin.*` |
| `admin` | 300 | `moderador` | `*`, `lands.admin.*`, `lands.bypass.*`, `nations.admin.*` |
| `owner` | 400 | `admin` | Lo mismo que `admin` |

**Track `rangos`:** `default → sheriff → supreme → global`. Los tres últimos son de un esquema anterior y ya no existen como grupos: el track está desactualizado.

**Usuarios al 15/11/2026:** un `owner`, un `dev`, tres con el rango por defecto, entre ellos la cuenta de prueba. Hay además una entrada de usuario **sin nombre** con permiso `*` y grupo `default`: conviene averiguar a quién pertenece y quitarle el `*` si no es del dueño. La cuenta de prueba tiene `3y33.chunk.1` y `3y33.chunk.2` de las pruebas de chunks gratis (ver 3). Varios usuarios tienen `tresyetres.estilo.argentina` (regalo de la beta, ver [`04-plugins-propios.md`](04-plugins-propios.md)).

## 2. Cómo se ve el rango en el juego

El Core lee el **grupo principal** del jugador con `%vault_rank%` y lo muestra como insignia pixel art antes del nombre (fuente por defecto del pack, caracteres privados):

| Rango | Carácter | Colores del degradado del nombre (claro/oscuro) |
|---|---|---|
| owner | U+E305 | `#A3123A` / `#F0507A` |
| admin | U+E304 | `#E02B2B` / `#FF8A8A` |
| dev | U+E303 | `#7B3FE4` / `#C09CFF` |
| moderador | U+E302 | `#1670D9` / `#6FB0FF` |
| vipplus | U+E301 | `#F2A900` / `#FFE07A` |
| vip | U+E300 | `#00C8FF` / `#B5FFFF` |
| (sin rango) | — | "Usuario" |

Los colores están en `Color.RANGOS` (3y33Core). `/color` lo pueden usar VIP, VIP+ y staff (por rango o con el permiso `tresyetres.color`); los presets especiales piden `tresyetres.estilo.<nombre>`. La insignia del trabajo (`%trabajos_insignia%`) es otra cosa y va en el chat ([`05-trabajos.md`](05-trabajos.md)).

**Para sumar un rango nuevo** hay que tocar: el grupo en LuckPerms, el carácter y la textura en el pack (`fuente/rangos.py` y `rangos.json`), el mapa de `%vault_rank%` y `Color.RANGOS` en el Core, y los cooldowns de `/rtp` en su `config.yml`.

## 3. Tierras (Lands)

Lands es un plugin de pago con módulos propios en `plugins/Lands/Modules`. Se limita por **packs** de `plugins/Lands/player-limits.yml` (`stack: true`: los packs se suman):

| Pack | Se activa con | Límites propios |
|---|---|---|
| `admin` | `lands.admin.no_limits` | Todo ilimitado |
| `default` | todos | 2 tierras propias (`player_lands_owned`), 2 como invitado de confianza, **chunks ilimitados pero pagos** (`player_chunks: -1`), 20 miembros, 20 áreas, 10 aliados |
| `rango_vip` | grupo `vip` | **25 chunks gratis** |
| `rango_vipplus` | grupo `vipplus` | **25 chunks gratis** más |
| `chunk_1` … `chunk_25` | permiso `3y33.chunk.1` … `.25` | **1 chunk gratis** cada uno |

- **VIP+ suma 50 chunks gratis** porque el grupo `vipplus` hereda de `vip` y los dos packs se acumulan.
- **Chunks gratis de los trabajos:** cuando se active (hoy apagado), el plugin de Trabajos da `3y33.chunk.N` con `lp user <jugador> permission set 3y33.chunk.N true`, hasta 25 por cuenta. Ver [`05-trabajos.md`](05-trabajos.md).
- **Costos** (en `config.yml` de Lands): el primer chunk cuesta **100** y **cada chunk siguiente sube un 25 %** (`chunk.cost-increase`, modo porcentaje, sin tope; antes era 10 %, backup `Lands-config.yml.pre-costo25` en `/respaldo-prebeta/`). Al soltar un chunk se devuelve el **15 %** (`cashback`). Crear una tierra cuesta **0**, renombrar **250**, traspasar el dueño **5000**. Los chunks gratis no pagan ese costo. El mantenimiento de tierras (*upkeep*) está **apagado**. Las rentas y los teleports no cuestan.
- Roles, ajustes y niveles: `roles.yml`, `levels.yml`, `categories.yml` y la carpeta `Data/` (los datos de las tierras). **Respaldá `Data/` antes de cualquier cambio grande.**
- **Tiendas:** solo valen dentro de una tierra donde el dueño es miembro ([`04-plugins-propios.md`](04-plugins-propios.md)).
- **Mundos:** hoy Lands acepta `3y33Beta` además del mundo principal: sacarlo antes de abrir.

## 4. Restos para limpiar (no rompen nada, confunden)

1. `moderador` hereda de `global`, un grupo que ya no existe. Quitar esa herencia, o crear el grupo si la idea era tener un rango intermedio.
2. El track `rangos` lista `sheriff`, `supreme` y `global`. Reemplazarlo por `default → vip → vipplus` o borrarlo.
3. Permisos de plugins que ya no están: `landclaim.*` (plugin de tierras viejo), `tierras.*` (3y33Tierras), `quickshop.*` (QuickShop, reemplazado por 3y33Shops) y `ezrtp.*` (EzRTP, reemplazado por el `/rtp` del Core). Se pueden sacar con `lp group <grupo> permission unset <permiso>`.
4. Permisos propios del `default` que ya no hacen falta: `quickshop.browse`, `quickshop.create.*`, `quickshop.player`, `quickshop.togglefreeze`. En su lugar las tiendas de 3y33Shops no piden permisos especiales (lo que limita es Lands).
5. El grupo `donador` no se usa. Decidir si se borra o se vuelve parte del esquema VIP.
6. Los cooldowns de `/rtp` en `3y33Core/config.yml` nombran los rangos viejos.
7. `3y33.chunk.1` y `.2` de la cuenta de prueba y la entrada de usuario con `*` sin nombre (ver 1).

Todo esto va también en [`09-pendientes.md`](09-pendientes.md). **Antes de limpiar nada**: `lp export` a un archivo y moverlo a `/respaldo-prebeta/` (regla de la casa: no se borra, se respalda).

## 5. Qué dan los rangos pagos

La idea (plan original, [`referencia/plan-3y33.md`](referencia/plan-3y33.md)) es que **VIP y VIP+ sean cosméticos**: prefijo propio, color de chat, `/nick`, `/hat`, partículas, tags extra, mascota cosmética. Eso es lo que figura en el plan. Lo que hoy está configurado en el server, además de lo cosmético, son **chunks gratis** (25 y 50). **Esa parte no quedó confirmada con el dueño** ("¿confirmado?" en sus notas del 08/11): mientras no la confirme, considerarlo una decisión abierta, porque roza la regla "nada pay-to-win". Ver [`08-decisiones.md`](08-decisiones.md).
