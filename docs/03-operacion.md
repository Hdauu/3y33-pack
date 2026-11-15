# Operación del server

Manual de tareas frecuentes: reiniciar, mirar logs, respaldar, desplegar un plugin o el pack, tocar configuraciones y diagnosticar problemas. Todo se puede hacer desde la interfaz web del panel; la sección 9 explica además cómo automatizarlo por API.

## 1. Reglas de oro

1. **No se borra nada.** Lo que se reemplaza o se saca se mueve a `/respaldo-prebeta/` con un sufijo que diga el motivo.
2. **Antes de editar una configuración, copiala** a `/respaldo-prebeta/<archivo>.pre-<motivo>` (por ejemplo `server.properties.pre-pack46`, `recompensas.yml.pre-chunks-activo`, `3y33Trabajos-1.10.0.jar.pre-1.11.0`).
3. **Todo reinicio se avisa** y se hace con parada limpia (botón *Restart* del panel o la señal `restart`). Nunca matando el proceso.
4. **Probar siempre como jugador común**, no con op ni con `*` (los costos, límites y permisos no se ven igual).
5. **Un solo jar por plugin** en `plugins/`. Si subís una versión nueva, la vieja sale de ahí.
6. No subir al repo contraseñas, tokens, datos de AuthMe ni bases de datos.

## 2. Reiniciar el server

1. En la consola: `say Reinicio en 10 segundos: <motivo>`.
2. Esperá 10 segundos y reiniciá (panel → *Restart*).
3. El arranque tarda **unos 65 segundos** (Geyser y Sonar son lo más lento).
4. Verificá en `logs/latest.log` (ver sección 3).

## 3. Mirar el log y saber si arrancó bien

Archivo: `logs/latest.log` (en el panel: *Files → logs → latest.log*). Buscá:

| Qué | Cómo se ve | Significa |
|---|---|---|
| Plugin propio cargado | `[3y33Trabajos] Enabling 3y33Trabajos v1.11.2` y `3y33Trabajos listo: 116 acciones en 5 trabajos.` | Bien |
| Server listo | `Done (6x.xxxs)!` | Arrancó |
| Error de un plugin | `[Server thread/ERROR]: ...` con el nombre del plugin | Mirar el stack trace de abajo |
| Aviso conocido | `[Essentials] You are running an unsupported server version!` | Se ignora |

Una forma práctica de revisar todo: filtrar por `ERROR`, `WARN`, `Enabling 3y33` y `Done (`.

## 4. Respaldos

- **Automático:** el hosting hace un backup **todos los días a las 05:00** (programación "Backup diario"). El plan permite **un solo backup guardado** y está en el mismo host. Pesa unos 12 GB. Antes de abrir al público conviene sumar una copia **fuera** del host y una rotación (el plan original era 7 diarios, 4 semanales, 1 mensual).
- **Manual de configuraciones:** copiar a `/respaldo-prebeta/` con sufijo (regla de oro 2).
- **Rangos y permisos:** `lp export <nombre>` genera `plugins/LuckPerms/<nombre>.json.gz`; después moverlo a `/respaldo-prebeta/`.
- **Tiendas:** `plugins/3y33Shops/tiendas.db` junto con `tiendas.db-wal` y `tiendas.db-shm` (conviene copiarlos con el server apagado o justo después de un guardado).
- **Tierras:** carpeta `plugins/Lands/Data/` (la base `database_v2.db` y su carpeta). Antes de operaciones masivas, copiala (por ejemplo `Lands-database_v2.db.pre-borrar-claims`).
- **Probar una restauración** al menos una vez antes de abrir (pendiente).

## 5. Desplegar un plugin propio (Trabajos, Core, Shops)

Ejemplo con 3y33Trabajos; para los otros cambia el nombre.

**En el repo:**

1. Editá el código en `plugins/3y33Trabajos/src/…`.
2. Subí la versión en **dos lugares**: `<version>` del `pom.xml` y `version:` de `src/main/resources/plugin.yml`.
3. Si agregaste claves de configuración, actualizá `src/main/resources/config.yml` (la plantilla).
4. Hacé `git pull --rebase` (el CI mete commits de jars en `main`, así que tu copia suele estar atrasada) y después sacá el jar viejo del repo con `git rm plugins/3y33Trabajos/3y33Trabajos-<versión vieja>.jar`.
5. Commit y `push`. El CI compila y commitea el jar nuevo (tarda 1 o 2 minutos). Mirá con `git ls-tree origin/main plugins/3y33Trabajos/` hasta que aparezca el jar de la versión nueva. Si no aparece, mirá `build-log.txt` (guarda el error de compilación).

**En el server:**

6. Si agregaste claves nuevas de configuración, **editá a mano** `plugins/3y33Trabajos/config.yml` del server y agregalas (con copia previa a `/respaldo-prebeta/`). El plugin no las fusiona solo.
7. Mové el jar viejo: `plugins/3y33Trabajos-<vieja>.jar` → `/respaldo-prebeta/3y33Trabajos-<vieja>.jar.pre-<nueva>`.
8. Bajá el jar nuevo a `/plugins` con *Pull remote file* del panel usando la URL de GitHub *raw* de la rama `main`:
   `https://raw.githubusercontent.com/Hdauu/3y33-pack/main/plugins/3y33Trabajos/3y33Trabajos-<versión>.jar`
9. Avisá (`say`), esperá 10 s y reiniciá. Verificá `Enabling 3y33Trabajos v<versión>` en el log.

> **Ojo con los reinicios en caliente:** no uses plugman ni `/reload`. Siempre reinicio completo.

**Pruebas que conviene hacer después:** entrar con la cuenta de prueba, abrir `/trabajos` y revisar que cobre una acción; para mochilas, `/trabajosadmin mochila dar <jugador> <1-4>` desde la consola.

## 6. Cambiar configuración sin reiniciar

| Plugin | Cómo recargar | Alcance |
|---|---|---|
| 3y33Trabajos | `/trabajosadmin recargar` | `config.yml` (incluida la curva de niveles), `acciones.yml`, `misiones.yml` y `recompensas.yml`. **No** recarga las mochilas (espacios y materiales de mejora), los NPC ni los textos del pack: para esos cambios, reiniciar |
| 3y33Core | `/3y33core reload` | `config.yml` (reglas, anuncios, cooldowns…) |
| 3y33Shops | `/tiendaadmin recargar` | `config.yml` |
| Lands | `lands admin reload` (consola) | Configuración y `player-limits.yml` (los packs por permiso se re-evalúan en vivo) |
| LuckPerms | los cambios con `/lp` valen al instante | |
| `server.properties` | **requiere reinicio** | |

## 7. Desplegar el resource pack

El server le manda el pack al jugador con una URL de **GitHub raw fijada a un commit** y con su **sha1**. Si cambia el zip hay que actualizar las dos cosas. Si no se actualizan, los jugadores siguen viendo el pack viejo (o no lo cargan si no coincide el sha1).

1. Modificá el pack (con los scripts de `fuente/` o a mano; ver `06-resource-pack.md`). El zip es `java/3y33-pack.zip`.
2. Commit y `push` (sin tocar nada más del zip). 
3. Sacá el hash del commit que **último tocó el zip**: `git log -1 --format=%H -- java/3y33-pack.zip`.
4. Verificá que GitHub sirve el mismo contenido y sacá el sha1:
   `curl -sL https://raw.githubusercontent.com/Hdauu/3y33-pack/<hash>/java/3y33-pack.zip | sha1sum`
5. Copiá `server.properties` a `/respaldo-prebeta/server.properties.pre-pack<N>` y editá dos líneas: la URL `resource-pack=` (reemplazá el hash) y `resource-pack-sha1=` (el sha1 del paso 4).
6. Avisá (`say`) y reiniciá. Entrá y comprobá que el pack se descarga.

**Pin actual** (al cierre, pack v46): commit `60e478ac0100652a2527fb3b76e2d53bc297b3dc`, sha1 `8e812f79023bb1e123f81402f6eff2de7acc6a6e`. Atención: **si alguien reescribe el historial de `main`, el hash cambia** y hay que repetir este procedimiento (así se hizo el 10/11/2026).

`resource-pack-id` **no se cambia** salvo que se quiera obligar a todos a volver a descargar; si se cambia, hay que poner el mismo valor en `3y33Core/config.yml` (`pack.id`).

## 8. Tareas de administración frecuentes

**Trabajos** (`/trabajosadmin …`, solo staff):

| Comando | Para qué |
|---|---|
| `reroll` | Renovar ya las rarezas del día |
| `nivel <jugador> <trabajo> <n>` / `xp <jugador> <trabajo> <n>` | Poner nivel o sumar XP (jugador conectado) |
| `tomar <trabajo> [jugador]` / `quitar [jugador]` / `espera <jugador>` | Asignar o sacar el trabajo sin penalización, ver la espera |
| `misiones …`, `mision …`, `global …` | Ver o tocar misiones y la misión global |
| `ofrecer <jugador> <trabajo>` | Ofrecer los nodos de recompensa pendientes |
| `mochila dar <jugador> <1-4>` | Entregar una mochila (jugador conectado) |
| `npc crear <trabajo\|general>` / `npc quitar` / `holo …` | Poner o sacar NPC y hologramas (parado donde va) |
| `balance` | Informe de cuánta plata y XP crean los trabajos por hora activa (últimas 72 h) |
| `info <jugador>` | Estado de un jugador |
| `insignias` | Herramientas de las insignias del chat |

**Tiendas:** `/tiendaadmin lista|info|borrar|rotar|revisar|recargar`.
**Core:** `/3y33core reload|anuncio|aceptar|rechazar|holograma <plantilla> [nombre]|spawn`, `/editor` (hologramas y NPC con botones), `/reportes`.
**Lands (consola o staff):** `lands admin player <jugador> limits`, `lands admin player <jugador> delete [confirm]`, `lands admin land <tierra>`, `lands admin reload`.
**Permisos:** `lp user <jugador> parent set <grupo>`, `lp group <grupo> permission info`, `lp export`.

## 9. Automatizar por la API del panel (opcional)

Casi todo se puede hacer con la interfaz web. Si querés scripts, el panel tiene una API de cliente en `/api/client/servers/<id-del-servidor>/…` (el id aparece en la URL del servidor en el panel). Dos formas de autenticarse:

- **Clave de API** (recomendada): panel → tu cuenta → *API Credentials*. Se manda como `Authorization: Bearer <clave>` con `Accept: application/json`. **No la subas al repo.**
- Desde la sesión del navegador (como se trabajó hasta ahora): cabeceras `X-Requested-With: XMLHttpRequest` y `X-XSRF-TOKEN` con el valor (decodificado) de la cookie `XSRF-TOKEN`. Si la API devuelve **419**, el token venció: recargá la página del servidor y volvé a leerlo.

Endpoints que se usaron:

| Acción | Llamada |
|---|---|
| Listar carpeta | `GET /files/list?directory=/plugins` |
| Leer archivo | `GET /files/contents?file=/server.properties` |
| Escribir archivo | `POST /files/write?file=<ruta>` con el texto como cuerpo (`text/plain`) |
| Mover o renombrar | `PUT /files/rename` con `{"root":"/","files":[{"from":"…","to":"…"}]}` |
| Copiar | `POST /files/copy` con `{"location":"<ruta>"}` (crea "copy" al lado) |
| Bajar un archivo de internet | `POST /files/pull` con `{"url":"…","directory":"/plugins","filename":"…","use_header":false,"foreground":true}` |
| Descargar | `GET /files/download?file=<ruta>` (devuelve una URL firmada) |
| Comando de consola | `POST /command` con `{"command":"say hola"}` |
| Encender/apagar/reiniciar | `POST /power` con `{"signal":"restart"}` |
| Backups, tareas, arranque | `GET /backups`, `GET /schedules`, `GET /startup` |

## 10. Diagnóstico rápido

| Síntoma | Qué mirar |
|---|---|
| Un plugin propio no carga | `latest.log` (error al habilitar). Casi siempre falta una dependencia o la configuración tiene una clave inválida; las mochilas, por ejemplo, avisan en el log si `mochilas.espacios`/`mejoras` están mal y usan los valores de fábrica |
| El CI no commitea el jar | `build-log.txt` en la raíz del repo o la pestaña *Actions* de GitHub. Revisá que el `push` haya tocado `src/` o `pom.xml` |
| Los jugadores no reciben el pack | `resource-pack` apunta a un hash que existe, `resource-pack-sha1` coincide (paso 4 de la sección 7) y el repo es público |
| Una tienda no funciona | Puede estar **suspendida** porque su tierra ya no existe o el dueño dejó de ser miembro (`/tiendaadmin revisar`) |
| Alguien no cobra de un trabajo | ¿Mundo habilitado (`mundos` de `config.yml`)? ¿Modo supervivencia o aventura? ¿Bloque puesto por un jugador, mob de spawner o AFK más de 60 s? ¿Tiene ese trabajo puesto? |
| Costos de Lands "en $0" | Es la cuenta de staff (`*`/`lands.bypass.*`). Probar con una cuenta común |
| La mochila IV "no abre" | A propósito: el trabajo actual tiene que estar en nivel 65 o más (ver `05-trabajos.md`) |
| `419` en la API del panel | Token vencido, recargar la página del servidor |

## 11. Checklist antes de abrir al público

- [ ] Sacar `3y33Beta` de `mundos` (Trabajos) y de los mundos donde Lands permite reclamar, y definir qué pasa con ese mundo.
- [ ] Apagar la whitelist (`white-list=false`) y revisar `max-players`.
- [ ] Resolver los **backups** (copia fuera del host + rotación) y **probar una restauración**.
- [ ] Programar el **reinicio diario** de madrugada con aviso.
- [ ] Limpiar permisos y grupos viejos (ver `07-rangos-y-permisos.md`).
- [ ] Decidir Aurora (retirar o no) y actualizar los anuncios que nombran `/misiones`.
- [ ] Activar o no los chunks gratis por trabajos (`chunks.activo`) después de probarlos.
- [ ] Anti-xray modo 2 y semillas de features configurados **antes** de pregenerar con Chunky; world border puesto.
- [ ] Reglas escritas (están en el Core), guía de inicio y spawn armado.
- [ ] Probar todo desde **Bedrock** (celular), incluidos menús y formularios.
- [ ] Borrar los datos de prueba (cuentas de prueba, tiendas de prueba, NPC de prueba) y registrar los NPC definitivos.
- [ ] Jugar uno o dos días con 3 o 4 personas mirando `spark` y la economía.
