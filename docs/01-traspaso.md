# Guía de traspaso

Esta es la primera hoja que tiene que leer quien retome 3y33 SMP. Dice **en qué estado quedó el proyecto**, **qué necesitás para trabajar**, **qué reglas puso el dueño** y **qué hacer el primer día**. Estado al **15/11/2026**.

## 1. Qué es el proyecto

3y33 SMP es un servidor survival vanilla+ para Java y Bedrock (Geyser + Floodgate), en español rioplatense, con estética roja y dorada. La idea del dueño, Francisco (`hdau`), es un SMP "simple, lindo y justo":

- **Simple:** todo sale de `/menu` o de un NPC; nada de comandos raros ni textos en inglés.
- **Lindo:** un solo estilo visual (colores, fuente, sonidos, menús) como si fuera un juego hecho por nosotros.
- **Justo:** lo que da ventaja se gana jugando; lo que se paga es **solo cosmético** (EULA de Minecraft).

El juego sigue siendo Minecraft normal: al morir se pierde todo, el comercio es entre jugadores y nada de tienda del server ni minijuegos. Más detalle en [`referencia/plan-3y33.md`](referencia/plan-3y33.md).

## 2. En qué estado quedó

**Funciona y está instalado en el server:**

- Servidor Paper 26.2 con Java 25, unos 45 plugins ([lista en `02-arquitectura.md`](02-arquitectura.md)).
- **Tierras con Lands** (reclamo de chunks, roles, subzonas) con costos y límites ya configurados ([`07-rangos-y-permisos.md`](07-rangos-y-permisos.md)).
- **Tiendas de cofre propias** (`3y33Shops` 2.12.1), atadas a las tierras.
- **Trabajos** (`3y33Trabajos` 1.11.2): cinco trabajos, XP, multiplicadores, misiones diarias/semanales/globales, recompensas por nivel, insignias en el chat, NPCs, y **mochilas** de 4 niveles con páginas y mejora en la mesa de crafteo ([`05-trabajos.md`](05-trabajos.md)).
- **Core** (`3y33Core` 1.14.3): reglas obligatorias en pantalla, anuncios, `/report`, `/rtp`, PvP opcional, estilos de chat (incluido el de la bandera argentina), editor de hologramas y NPCs.
- Resource pack Java (versión **v46** al cierre) con fuentes, banderas, insignias, íconos y ventanas propias.
- Backup diario automático del hosting.

**No está hecho (ver [`09-pendientes.md`](09-pendientes.md)):**

- Prestigio de los trabajos (el campo existe en los datos pero **nada lo sube**).
- Los encantamientos propios de los niveles 100/115/130 (el hito se reclama y se guarda, pero no hacen nada).
- Sistema de **logros propios** (`/logros`): Francisco lo quiere con "muchísimos" logros. Hay un diseño en la cabeza, no código.
- Auditoría y retiro de los plugins **Aurora, AuroraLevels y AuroraQuests**: Francisco decidió **sacar los niveles de cuenta** y dejar solo cosméticos; falta revisar qué otras cosas (TAB, chat, menús, `/nivel`) dependen de ellos.
- Penalización por abandonar un trabajo sin completarlo (diseño acordado, sin código).
- Mochila en la espalda con modelo 3D visible para otros jugadores (idea para más adelante).
- Cosméticos de hitos y de logros (se dejaron para la beta).
- Pack para Bedrock desactualizado (ver `06-resource-pack.md`).

**El server no abrió al público.** Está en beta cerrada: whitelist activada, 5 usuarios en LuckPerms (un owner, un dev y tres con el rango por defecto, entre ellos la cuenta de prueba).

## 3. Qué necesitás para trabajar

El repo es público; **los accesos no**. Pedíselos a Francisco:

| Qué | Para qué |
|---|---|
| Cuenta del **panel del hosting** (Baires Host, Pterodactyl) con acceso al server | Consola, archivos, reinicios, backups, programación de tareas |
| Permiso de **escritura en el repo** de GitHub (`Hdauu/3y33-pack`) | Subir código; el CI compila los jars |
| Acceso al **Discord** del proyecto (enlace público en las reglas del Core) | Comunidad y soporte |
| Una cuenta de Minecraft **de prueba sin permisos** | Probar siempre como jugador común (con op o con `*` los costos y límites no se ven) |

Herramientas en tu máquina: Git, **JDK 25** (3y33Trabajos y 3y33Shops compilan con `release 25`; 3y33Core usa 21), Maven y Python 3 con Pillow y cairosvg (solo si vas a regenerar arte del pack). Algunos plugins compilan contra jars de terceros que van en `libs/` (no se suben al repo): mirá el workflow de cada uno en `.github/workflows/` para saber cuál bajar.

## 4. Reglas de la casa (las puso el dueño)

Leelas antes de tocar nada. El detalle y el porqué están en [`08-decisiones.md`](08-decisiones.md).

1. **Idioma y tono:** todo texto que ve el jugador va en **español argentino con voseo** ("tenés", "elegí", "reclamá"). Paleta **roja** (el prefijo `3y33 »` en rojo oscuro, degradados rojos).
2. **Nunca se borra nada del server:** lo que se reemplaza o se saca **se mueve** a `/respaldo-prebeta/` con un sufijo que diga de qué era (`.pre-1.11.0`, `.pre-chunks`, etc.). Antes de editar un archivo de configuración, se copia ahí.
3. **Los reinicios se avisan** con `say` en la consola, 10 segundos antes. El server tarda unos 65 segundos en volver.
4. **Cada etapa se prueba en el juego con el dueño** y él la aprueba antes de seguir. Probar siempre con una cuenta sin permisos.
5. **Arte 100 % original.** No se copia arte de otros servers ni de otros packs. Las referencias de otros servers (por ejemplo MasivoSMP) se usaron para entender *qué* hacen, nunca para copiar números ni dibujos.
6. **Nada de nivel ni de trabajo en el TAB ni en el scoreboard**, y **no hay integración de tags con las tierras**.
7. **Nada pay-to-win:** lo pago es cosmético.
8. **Las recompensas pertenecen al trabajo.** Un jugador solo puede *usar* lo ganado en un trabajo mientras su trabajo actual cumpla las condiciones. No se puede farmear un trabajo, saltar a otro y conservar todo. (Ver mochila IV en [`05-trabajos.md`](05-trabajos.md).)
9. **Commits a nombre de `hdau`**, sin líneas de coautoría de herramientas. El 10/11/2026 se reescribió el historial de `main` para que figure un solo autor humano; no vuelvas a agregar `Co-Authored-By`.
10. **Quien termina una tarea hace commit y push** (el flujo del dueño lo exige).

## 5. Riesgos y cosas raras que conviene saber

Ordenadas de más a menos importantes:

1. **Los backups están en el mismo host.** El hosting guarda **un solo backup** (límite del plan), a las 05:00 todos los días, y pesa unos 12 GB. No hay copia fuera de la máquina. El plan original pedía rotación 7/4/1 y copia externa. Conviene resolverlo **antes de abrir**.
2. **Los grupos `owner`, `admin` y `dev` tienen el permiso `*`.** Eso hace que, con esas cuentas, los costos de Lands, los límites y los permisos de los plugins no se vean como los ve un jugador. Es la razón de probar con una cuenta común.
3. **Quedan restos de plugins viejos en LuckPerms y en configs:**
   - `moderador` hereda de un grupo `global` que ya no existe, y varios grupos tienen permisos de `landclaim.*`, `tierras.*`, `quickshop.*`, `ezrtp.*` (plugins que ya no están).
   - El `track` `rangos` todavía lista `sheriff`, `supreme` y `global`.
   - `3y33Core/config.yml` tiene cooldowns de `/rtp` para `sheriff`, `supreme` y `global`.
   - No rompen nada, pero confunden. Limpiarlos es una tarea chica.
4. **Los anuncios del Core nombran `/misiones`**, que hoy lo da AuroraQuests. Si se saca Aurora, hay que cambiar ese texto.
5. **Pruebas pendientes de limpiar antes de abrir:**
   - El mundo `3y33Beta` está en `mundos` de Trabajos y Lands (permite reclamar y cobrar ahí). Sacarlo antes de abrir.
   - Hay 4 NPC de trabajos por registrar/ubicar definitivamente y un maniquí de prueba suelto con el tag `3y33test`.
   - La cuenta de prueba puede conservar permisos `3y33.chunk.1` y `3y33.chunk.2` de las pruebas de chunks gratis: verificar y quitar.
   - En el server hay una carpeta `respaldo-landclaim/` (copia del plugin de tierras anterior). Francisco quiere borrarla **solo cuando esté conforme** con Lands.
   - Dos tiendas de prueba del dueño figuran *suspendidas* porque se borraron sus tierras (comportamiento normal de `Integridad`).
6. **`online-mode` está en `false`** (el server acepta cuentas no premium) y la seguridad de cuentas la da **AuthMe**. Es una decisión consciente; `3y33Login` solo le agrega los títulos en pantalla.
7. **No hay reinicio diario programado.** El plan lo pedía (de madrugada, con aviso). Hoy solo corre el backup.
8. **El pack de Bedrock** (`bedrock/3y33-bedrock.mcpack`) quedó en una versión vieja (30/10). Los jugadores de Bedrock ven los ítems comunes y no las insignias ni los íconos nuevos hasta que se regenere.
9. En el log aparece `[Essentials] You are running an unsupported server version!`. Es un aviso conocido y no impide nada.
10. Hay muchos jars viejos de nuestros plugins en el repo (cada versión se conserva). Es intencional (historial de versiones); no hace falta limpiarlos.

## 6. Plan sugerido para el primer día

1. Leé este documento y [`02-arquitectura.md`](02-arquitectura.md).
2. Entrá al panel, mirá la consola y confirmá que arranca limpio (`Done (` sin `ERROR` de nuestros plugins).
3. Entrá al juego con la cuenta de prueba y recorré: `/menu`, `/trabajos`, `/lands`, `/tienda`, abrí una mochila (`/trabajosadmin mochila dar <jugador> 1` desde la consola).
4. Cloná el repo y compilá un plugin localmente para confirmar que tu entorno anda (`mvn -B package` dentro de `plugins/3y33Trabajos`).
5. Hacé un cambio mínimo (por ejemplo un texto), subilo y mirá que el CI commitee el jar nuevo. Recién ahí desplegá siguiendo [`03-operacion.md`](03-operacion.md).
6. Elegí el próximo trabajo de [`09-pendientes.md`](09-pendientes.md) y **hablalo con el dueño antes de empezar**.

## 7. Cómo se trabajó hasta acá

El proyecto se construyó en conversaciones largas entre Francisco y un asistente de IA: Francisco decide y prueba en el juego, el asistente programa, despliega y documenta. De ahí vienen algunas costumbres: se trabaja **por etapas chicas**, cada una se **despliega y se prueba en el juego**, y los textos de diseño se escriben en español. Los documentos de `referencia/` salen de ese trabajo.
