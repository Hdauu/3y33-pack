# Resource pack

El server manda un resource pack a los jugadores de **Java** (**no es obligatorio**: `require-resource-pack=false`). El de **Bedrock** es aparte y hoy está desactualizado. Todo el arte es **original del proyecto** (se dibuja con scripts de Python), salvo las banderas y la fuente, que tienen licencia abierta (ver créditos en el [`README`](../README.md)).

## 1. Archivos

| Ruta | Qué es |
|---|---|
| `java/3y33-pack.zip` | El pack de Java ya armado. **Es el que se despliega** |
| `bedrock/3y33-bedrock.mcpack` | Pack de Bedrock (Geyser). Versión del 30/10/2026: **no tiene** las insignias, los íconos ni las mochilas nuevas |
| `fuente/` | Scripts de Python que generan las texturas, fuentes y modelos |
| `fuente/fuentes/` | `PressStart2P.ttf` y `OFL.txt` (licencia). `ascii.png`, `accented.png` y `default_font.json` están en `.gitignore`: son las letras del Minecraft original, hay que sacarlas de tu propio juego |
| `fuente/insignias_externas/` | Arte de insignias que se hizo aparte, con un `LEEME.txt` que explica cómo se usan las 4 piezas de cada insignia |
| `fuente/taberna_externa/` | PNG de las ventanas de tienda con tema "taberna" |
| `fuente/texturas/` | Textura de menú de apoyo |

`pack.mcmeta`: `pack_format` 75, con `supported_formats` 15 a 999 (funciona en versiones viejas y nuevas del cliente).

## 2. Cómo está armado el zip

Todo bajo `assets/3y33/` salvo lo indicado:

| Carpeta | Contenido |
|---|---|
| `font/` | Fuentes JSON: `bandera` y `bandera_g` (chat con la bandera argentina, fina y gruesa), `sticker` y `sticker_g` (emojis pixel art de 8×8), `tienda` (fondos de las ventanas), `titulo` y `texto` (Press Start 2P grande y normal) |
| `textures/font/` | Imágenes de esas fuentes: fondos de ventana (`tienda_gui`, `ajustes_gui`, `admins_gui`, `historial_gui`, `trabajos_gui`, `trabajo_gui`, `acciones_gui`, `misiones_gui`, `confirmar_gui`, `elegir_gui`, `camino_gui`), banderas (`flags`), stickers, rangos (`rango_*.png`) y `bandera*.png` |
| `items/trabajos/` y `models/item/trabajos/` | Definición y modelo de cada ítem de menú (225 de cada uno) |
| `textures/item/trabajos/` | Texturas de esos ítems (165): cartas de los trabajos, íconos con marco, cartas de misiones, flechas, libro, boosts de recompensas, **mochilas I a IV** |
| `textures/npc/` | Skins de los 5 NPC (una por trabajo) |
| `assets/insignias/font/chat.json` y `assets/insignias/textures/font/` | Fuente **`insignias:chat`**: marcos por rareza (`marcos.png`) y emblemas de los trabajos |
| `assets/minecraft/font/default.json` | Reemplaza la fuente por defecto para agregar los caracteres privados `U+E300` a `U+E305` (tags de rango) |
| `licencias/`, `CREDITS.txt` | Licencias de terceros (Press Start 2P, flag-icons) |

**Rangos:** las insignias de rango del chat son un solo carácter de la fuente por defecto: `U+E300` vip, `E301` vipplus, `E302` moderador, `E303` dev, `E304` admin, `E305` owner. El pack también trae texturas de rangos que ya no existen (`rango_sheriff`, `rango_supreme`, `rango_global`, `rango_plata`): no molestan, se pueden sacar.

**Modelos de ítems:** los menús usan `item_model` (no `custom_model_data`) con ids `3y33:trabajos/<nombre>`; las cartas grandes son ítems *oversized* donde cada píxel de la textura es un píxel de la pantalla. Los íconos de algunos ítems (por ejemplo las insignias de minero) **apuntan a la textura de Minecraft** en lugar de copiarla.

## 3. Los scripts (`fuente/`)

Cada script lleva su explicación en el comentario del principio. Resumen:

| Script | Qué hace |
|---|---|
| `titulos.py` | Fuentes `3y33:titulo` y `3y33:texto` desde Press Start 2P |
| `bandera.py` | Fuentes de la bandera argentina (tres franjas por letra). Necesita `ascii.png` y `accented.png` |
| `rangos.py` + `rangos.json` | Tags de rango en pixel art, un carácter cada uno |
| `stickers.py` | Stickers 8×8 propios; también escribe el listado para 3y33Tierras |
| `tienda_gui.py`, `tienda_taberna.py` | Fondos de las ventanas de tienda (versión lisa y versión "taberna", la que se usa) |
| `camino_gui.py`, `camino_gui_taberna.py` | Fondo de la ventana "Camino de recompensas" (en versión pergamino y en versión taberna) |
| `npc_skins.py`, `npc_gorros.py`, `vista3d.py`, `capturar_skin.py` | Skins y gorros 3D de los NPC, vista previa 3D para revisar sin entrar al juego, y recuperar una skin desde una captura |
| `cabezas.py` | Vista previa de gorros de los NPC (es un script de revisión y tiene rutas de desarrollo fijas) |
| `build.py` | **Generador original de banderas** (flag-icons → `flags.png`). Tiene rutas fijas de la máquina donde se hizo; hay que adaptarlas |
| `icon.py` | Ícono 16×16 de menú |
| `trabajos/cartas32.py`, `cartas48.py` | Cartas grandes de los trabajos (32×48 y 48×72) |
| `trabajos/iconos.py` | Marcos con imagen: 5 trabajos × 4 marcos de prestigio × normal/activo |
| `trabajos/estados.py`, `pixel.py`, `pixel3.py` | Estados (normal, activo, bloqueado) y utilidades de dibujo |
| `trabajos/misiones_cartas.py` | Cartas de las misiones (34×54) |
| `trabajos/recompensas.py` | Íconos de boosts (XP y plata por rareza) con flechas de mejora |
| `trabajos/flechas.py`, `libro.py` | Flechas « » para paginar y libro del botón "Qué se entrega" |
| `trabajos/mochilas.py` | Arte de las 4 mochilas (cuero con solapa y hebilla; I verde, II violeta, III dorado, IV rojo vino) |

**Importante:** la mayoría de los scripts **editan `java/3y33-pack.zip` en el lugar** (reemplazan los archivos dentro del zip). Antes de correr uno, hacé una copia del zip. Varios tienen rutas absolutas de la máquina original: leé el principio del script y ajustalas. Necesitás Python 3, **Pillow** y, para banderas, **cairosvg**.

## 4. Cómo cambiar el pack y desplegarlo

1. Corré el script que corresponde (o editá a mano dentro del zip) y mirá el resultado.
2. Commit y push de `java/3y33-pack.zip`.
3. Averiguá el **hash del commit** que tocó el zip: `git log -1 --format=%H -- java/3y33-pack.zip`.
4. En el server, en `server.properties`, cambiá la URL de `resource-pack` (que apunta a `raw.githubusercontent.com` **fijada a ese commit**, no a `main`, para que el cliente no use una copia vieja guardada) y el valor de `resource-pack-sha1` por el SHA-1 del zip.
5. Anunciá con `say`, esperá 10 s y reiniciá.

Detalle paso a paso y cómo verificar el SHA-1: [`03-operacion.md`](03-operacion.md).

**Atención con el historial:** como la URL lleva el hash del commit, **si se reescribe el historial de `main` el hash cambia** y hay que volver a fijarlo (pasó el 10/11/2026). Pin al cierre: commit `60e478ac0100652a2527fb3b76e2d53bc297b3dc`, SHA-1 `8e812f79023bb1e123f81402f6eff2de7acc6a6e` (v46).

## 5. Bedrock

El pack de Bedrock lo reparte Geyser. Hay que **regenerarlo** a partir del pack de Java para que los jugadores de Bedrock vean lo nuevo (insignias, íconos de menús, mochilas). Mientras tanto, en Bedrock los menús usan los ítems comunes del juego (el plugin ya lo contempla: `iconos-pack` y la detección de Floodgate). No hay un script en el repo que lo genere: hay que armarlo de nuevo (por ejemplo con el conversor de packs de Geyser) y reemplazar el `.mcpack`.

## 6. Reglas de arte

- Todo original, en paleta **roja y dorada** (la misma de los menús).
- No copiar arte, íconos ni números de otros servers; las referencias sirvieron para entender qué hace cada cosa.
- Los ítems de Minecraft que aparecen en las insignias se referencian, no se copian.
- Con el pack puesto, los ítems de menú se ven con arte propio; sin el pack se ven los ítems comunes (si se abre al público conviene decidir si pasa a ser obligatorio).
