# Arquitectura

Cómo está armado el server: hosting, software, mundos, plugins instalados, dónde vive cada dato y cómo se conectan las piezas. Datos relevados del server real el 15/11/2026.

## 1. Hosting y software base

| Tema | Valor |
|---|---|
| Hosting | **Baires Host** (panel Pterodactyl en `panel.baires.host`, nodo `gs-04`) |
| Recursos del plan | 8 GB de RAM, 30 GB de disco, 300 % de CPU, 1 base de datos, 1 backup, 5 puertos |
| Software | **Paper 26.2** (se baja de papermc.io; es el `server.jar` de la raíz) |
| Java | **25** (obligatorio desde Minecraft 26.1) |
| Arranque | JVM con G1GC y flags tipo Aikar; la memoria máxima se calcula como 3/4 del límite del contenedor |
| Bedrock | Geyser + Floodgate |
| Otras versiones de Java | ViaVersion (sin ViaBackwards) |
| Modo de cuentas | `online-mode=false` (acepta no premium); inicio de sesión con **AuthMe** |
| Whitelist | activada (`white-list=true`) mientras dura la beta |
| Jugadores | `max-players=100`, `view-distance=8`, `simulation-distance=6`, `difficulty=hard` |
| Resource pack | enviado por el server (URL de GitHub fijada a un commit), **no obligatorio** (`require-resource-pack=false`) |
| Spawn protection | 0 (la protección del spawn la dan WorldGuard/Lands) |

Proxy (Velocity): descartado hasta que haya un segundo server.

## 2. Mundos

- **`3y33SMP`**: mundo principal con su Nether y su End (en el formato de carpetas de Paper 26: `3y33SMP/dimensions/…`).
- **`3y33Beta`**: mundo de pruebas (Multiverse-Core + Multiverse-Inventories). Lands y Trabajos lo permiten hoy; **hay que sacarlo antes de abrir** (ver `01-traspaso.md`).
- Plan original: pregenerar con Chunky dentro de un world border (10.000 × 10.000 en el overworld y 5.000 × 5.000 en el Nether; el End se abre después como evento), con anti-xray y semillas de features configurados **antes** de pregenerar.

## 3. Plugins instalados

### 3.1 Propios (código en este repo)

| Plugin | Versión | Qué hace | Doc |
|---|---|---|---|
| **3y33Core** | 1.14.3 | Reglas en pantalla, anuncios, `/report`, `/rtp`, PvP opcional, estilos de chat y de nombre, `/color`, editor de hologramas/NPC | [`04`](04-plugins-propios.md) |
| **3y33Login** | 1.0.2 | Títulos en pantalla del login de AuthMe | [`04`](04-plugins-propios.md) |
| **3y33Shops** | 2.12.1 | Tiendas de cofre con motor propio, ligadas a Lands | [`04`](04-plugins-propios.md) |
| **3y33Trabajos** | 1.11.2 | Trabajos, XP, misiones, recompensas, mochilas, insignias, NPCs | [`05`](05-trabajos.md) |

Históricos, **no instalados** (se conservan por el código): `3y33Tierras` (extras para un plugin de tierras anterior) y `3y33Tiendas` (reglas para QuickShop). Ver `04-plugins-propios.md`.

### 3.2 De terceros

| Grupo | Plugins (versión instalada) |
|---|---|
| Núcleo | LuckPerms 5.5.71 · Vault 1.7.3 · PlaceholderAPI 2.12.3 · EssentialsX 2.22.0 + EssentialsXSpawn · spark · Chunky + ChunkyBorder |
| Terrenos | **Lands** (de pago) con módulos propios en `plugins/Lands/Modules` |
| Cuentas y compatibilidad | AuthMe 6.0.1 · SkinsRestorer 15.12.6 · Geyser-Spigot · floodgate · BedrockGUI 2.0.11 · ViaVersion 5.12.2 · packetevents 2.14.0 |
| Seguridad y recuperación | GrimAC 2.3.75 (anticheat) · Sonar (antibot) · CoreProtect CE 24.1 · InventoryRollbackPlus 1.8.5 · WorldGuard 7.0.19 |
| Menús, hologramas y NPC | DeluxeMenus 1.14.1 · FancyHolograms 2.12.0 · FancyNpcs 2.12.1 · FancyDialogs 1.4.0 · ItemJoin 6.1.8 |
| Chat y presentación | TAB 6.2.1 · ModernTags 2.2.0 · LPC 4.4.0 · MiniMOTD 2.2.5 |
| Mundos y construcción | Multiverse-Core 5.8.2 · Multiverse-Inventories 5.3.6 · FastAsyncWorldEdit 2.16.2 · GSit 3.7.0 |
| Voz y diagnóstico | Simple Voice Chat 2.6.24 · BairesPing (del hosting) · FancyAnalytics · bStats |
| **A retirar** | **Aurora 2.6.0, AuroraLevels 2.1.0, AuroraQuests 2.6.0** (niveles de cuenta y misiones; el dueño decidió sacar los niveles. Falta la auditoría de dependencias, ver `09-pendientes.md`) |
| Librerías (no tocar) | nCore, faststats, `.incredibleplugins` |

### 3.3 Desactivados

Los plugins que ya no se usan **se mueven** (nunca se borran) a `plugins/_desactivados/`: BedrockGUI-HomesteadAddon, EzRTP, Homestead, SimpleClaimSystem, Simple_RTP, ajLeaderboards y DiscordSRV (este último quedó sin configurar: nunca se creó el bot). Algunos conservan su carpeta de datos dentro de `plugins/`.

En la raíz del server hay además `respaldo-prebeta/` (todos los respaldos de configuraciones y jars viejos, con sufijo que indica de qué momento son) y `respaldo-landclaim/` (copia del plugin de tierras anterior; se borra solo cuando el dueño lo apruebe).

## 4. Cómo se conectan las piezas

```
 jugador (Java o Bedrock)
        │  Geyser/Floodgate traducen Bedrock → Java
        ▼
     Paper 26.2 ── AuthMe (login) ── 3y33Login (títulos)
        │
        ├── LuckPerms ──► rangos y permisos ──► Lands (packs de límites por permiso)
        │
        ├── Vault ◄── EssentialsX (economía)
        │      ▲           ▲
        │      │           └── 3y33Shops (compras y ventas, ligadas a Lands)
        │      └────────────── 3y33Trabajos (paga plata por trabajar)
        │
        ├── 3y33Trabajos ── (chunks gratis, hoy apagado) ──► LuckPerms ──► Lands
        │
        ├── 3y33Core ── PlaceholderAPI ──► TAB / chat / ModernTags
        │      └── FancyHolograms, FancyNpcs, FancyDialogs
        │
        └── Resource pack (3y33-pack.zip, URL de GitHub fijada a un commit)
              ├─ fuentes: banderas, rangos (U+E300…), insignias, ventanas de tiendas
              └─ items/modelos: íconos y cartas de trabajos, mochilas, gorros de NPC
```

Reglas de acoplamiento que conviene saber:

- **Plata:** todo pasa por **Vault** con la economía de EssentialsX. Trabajos y Shops no tienen su propia moneda.
- **Lands:** `3y33Shops` habla con Lands **por reflexión** (no necesita su jar para compilar). Una tienda solo vale mientras está dentro de una tierra donde el dueño sigue siendo miembro; si no, queda *suspendida* hasta que se arregle (clase `Integridad`).
- **Chunks gratis de los trabajos:** Trabajos le suma un permiso de LuckPerms (`3y33.chunk.N`) a la cuenta y Lands lo convierte en un chunk gratis mediante un *pack* en `player-limits.yml`. Hoy está **apagado** (`chunks.activo: false` en `recompensas.yml`).
- **Nombres y chat:** `3y33Core` fija el *display name* con la insignia de rango y el degradado de color; Essentials tiene que tener `change-displayname: false` para no pisarlo. Los tags de rango son **un solo carácter** de la fuente del pack (`rangos.py`, caracteres desde U+E300).
- **Placeholders propios** (PlaceholderAPI): `%3y33_pvp%` (espada roja/verde para el TAB), `%trabajos_insignia%` (insignia del chat), y los de `3y33Tierras` (histórico).
- **Id del pack:** `resource-pack-id` de `server.properties` y `pack.id` de `3y33Core/config.yml` **tienen que ser el mismo** (así el Core sabe quién cargó el pack).

## 5. Dónde vive cada dato

| Qué | Dónde (en el server) | Notas |
|---|---|---|
| Perfiles de trabajos (nivel, XP, boosts, desbloqueos) | `plugins/3y33Trabajos/jugadores/<uuid>.yml` | Un archivo por jugador, escritura atómica |
| Mochilas (contenido) | `plugins/3y33Trabajos/mochilas.yml` | Se guarda por id de mochila, **no dentro del ítem** |
| Rarezas del día, misiones globales, balance, hologramas | `estado.yml`, `misiones-globales.yml`, `balance.yml`, `hologramas.yml` | En la misma carpeta |
| Configuración de trabajos | `config.yml`, `acciones.yml`, `misiones.yml`, `entregas.yml`, `recompensas.yml` | Copias editables de los `resources/` del plugin; ver `05-trabajos.md` |
| Tiendas | `plugins/3y33Shops/tiendas.db` (SQLite) | Hay que respaldarla junto con sus archivos `-wal` y `-shm` |
| Estilos de chat, colores, PvP, reglas aceptadas, reportes | `plugins/3y33Core/*.yml` | |
| Rangos y permisos | base de LuckPerms | Para respaldarla se usa `lp export` (genera `.json.gz` en `plugins/LuckPerms/`) |
| Tierras | `plugins/Lands/Data/` y `config.yml`, `player-limits.yml`, `roles.yml` | |
| Cuentas (contraseñas) | AuthMe | **Dato sensible**: no copiar ni subir |
| Mundos | carpeta `3y33SMP/` | |
| Packs de Bedrock | `plugins/Geyser-Spigot/packs/` | |
| Respaldos manuales | `/respaldo-prebeta/` | Convención de nombres en `03-operacion.md` |

Los `config.yml` de los plugins propios que están **dentro del repo** (`src/main/resources/`) son la **plantilla de fábrica**; el server tiene su propia copia en `plugins/<nombre>/` que se puede haber editado a mano. Si agregás una clave nueva a la plantilla, **tenés que agregarla también a mano en la copia del server** (Bukkit no fusiona claves nuevas en archivos ya existentes).

## 6. Compilación y publicación (CI)

Cada plugin con `pom.xml` tiene su workflow en `.github/workflows/<plugin>.yml`:

1. Se dispara con `push` que toque `plugins/<plugin>/src/**` o su `pom.xml` (o a mano con *workflow_dispatch*).
2. Baja las librerías de terceros que necesite a `libs/` (por ejemplo Vault 1.7.3 o los jars de Fancy), compila con Maven y Java 25.
3. Si compila, **commitea el jar** en `plugins/<plugin>/` como `github-actions` (mensaje `<plugin>: jar compilado`). Si falla, guarda `build-log.txt` en la raíz del repo.

Workflows auxiliares (manuales): `explorar.yml` (vuelca la API de Fancy a `api-fancy.txt`), `inspeccion.yml` (vuelca la API del plugin de tierras anterior), `geyser.yml` (baja el último Geyser-Spigot a `plugins/terceros/`) y `paperapi.yml` (consulta versiones de `paper-api`).

`3y33Login` **no tiene `pom.xml` ni CI**: es chico y se compila a mano con `javac` (instrucciones en `plugins/3y33Login/README.md`; las clases de `stubs/` son solo para compilar y no van en el jar).

## 7. Convenciones de código (comunes a los plugins propios)

- Paquete base `ar.com.tresyetres.<plugin>`; textos de cara al jugador en español con voseo; mensajes con prefijo `3y33 »` y formato por MiniMessage/Adventure.
- Compatibilidad **Java + Bedrock en el mismo código**: para texto o formularios se usan alternativas que Geyser traduzca (armor stands con nombre, `ItemDisplay` solo para Java, formularios de Floodgate en una clase aparte para no cargar Floodgate si no está).
- Cosas pesadas (SQLite, archivos) fuera del hilo principal; las operaciones de plata/ítems en el hilo principal y de una sola vez, con reversión si algo falla (ver `Operaciones` en Shops).
- Los ítems especiales (mochilas, íconos de menú) se identifican con **PersistentDataContainer** y usan **item models** del pack (`3y33:...`), no `CustomModelData`.
