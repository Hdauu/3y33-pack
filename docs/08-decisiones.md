# Decisiones y reglas del dueño

Qué se decidió y **por qué**, para no discutir dos veces lo mismo. Las decisiones salen de las conversaciones entre Francisco (`hdau`) y el asistente; cuando algo **no quedó confirmado** lo digo. Si querés cambiar algo de acá, hablalo con el dueño.

## 1. Principios del proyecto

1. **Simple, lindo y justo.** Todo se alcanza desde `/menu` o un NPC; un solo estilo visual; lo que da ventaja se gana jugando.
2. **Nada pay-to-win.** Lo que se paga es solo cosmético (las reglas comerciales de Mojang no permiten vender ventajas). Los rangos por nivel (los de Aurora: Sheriff, Supreme, Global) eran gratis y se están por retirar.
3. **Survival vanilla+.** Se muere y se pierde todo, el comercio es entre jugadores, no hay tienda del server ni minijuegos.
4. **Java y Bedrock por igual.** Los menús funcionan en ambos (en Bedrock con ítems comunes); nada debe depender de que el cliente tenga el pack.
5. **Arte original.** Nada copiado de otros servers ni packs. Se usaron servidores de referencia (por ejemplo MasivoSMP) solo para entender *qué* hacen las cosas, nunca para copiar números ni dibujos.
6. **Español argentino con voseo y paleta roja.**

## 2. Reglas de trabajo del dueño

| Regla | Detalle |
|---|---|
| Nunca borrar | Lo reemplazado se **mueve** a `/respaldo-prebeta/` con sufijo (`.pre-1.11.0`). Antes de editar un archivo de configuración, copia ahí |
| Reinicios avisados | `say` con 10 s de anticipación; el server vuelve en unos 65 s |
| Se prueba cada etapa | En el juego, con el dueño, con una cuenta **sin permisos**; él aprueba antes de seguir |
| Commits | A nombre de `hdau`, **sin `Co-Authored-By` ni líneas de sesión de ninguna herramienta** (el dueño pidió no figurar con asistentes como colaboradores y el 10/11/2026 se reescribió el historial) |
| Quien termina, sube | Commit y push al terminar una tarea |
| Repo público | Ni contraseñas, ni tokens, ni ids del panel, ni correos, ni datos personales en el repo |
| Credenciales | Nadie (ni el asistente) escribe contraseñas ni claves ajenas; el acceso al panel lo da el dueño |
| `/respaldo-landclaim` | Se borra solo cuando el dueño diga que está conforme con Lands |

## 3. Decisiones de diseño

### Cuentas y niveles
- **Sin nivel de cuenta.** Se decidió **sacar los niveles de cuenta** (Aurora/AuroraLevels) y dejar solo cosméticos. Falta auditar qué depende de eso antes de retirarlos ([`09-pendientes.md`](09-pendientes.md)).
- **Ni nivel ni trabajo en el TAB ni en el scoreboard.** El trabajo se ve solo como insignia en el chat.
- **Sin integración de tags con las tierras.**

### Trabajos
- **Un trabajo activo a la vez**; el progreso de cada uno se conserva. Abandonar impone 13 horas de espera para tomar otro (evita saltar de un trabajo a otro para farmear).
- **Las recompensas pertenecen al trabajo.** Qué se desbloquea con un trabajo solo se **usa** mientras el trabajo activo cumpla la condición. La razón: que nadie farmee un trabajo y después use sus premios en otro. Aplicado a la Mochila IV (solo se abre con trabajo activo en nivel 65+; solo se arma una vez por cuenta). Para los encantamientos de los hitos 100/115/130 la regla es la misma (aún sin código).
- **Penalización por abandonar un trabajo sin completarlo:** el dueño aprobó la idea ("dejémoslo si no completaste todo el trabajo"): perder un porcentaje del XP de ese trabajo si no llegó al 130; no hay penalización si lo completó; el menú de confirmación muestra cuánto se pierde. Se propuso **20 %** y **el dueño no confirmó el número**.
- **Ganar plata y XP según lo que se hace, no por estar parado:** momentum, AFK a los 60 s, anti-trampa de bloques puestos, spawners y topes por hora.
- **Los números de balance son una estimación** (300 horas para llegar al 130). Se corrigen midiendo con `/trabajosadmin balance`.
- **Misiones de entregar ítems en un NPC** (no de "hacer X cosas"), con ítems limpios, y una misión global colectiva que se adapta sola a lo activa que esté la comunidad.
- **Chunks gratis como premio:** diseñado pero apagado hasta armar bien el lado de Lands y probar si Lands toma el permiso sin reloguear.

### Mochilas
- Cuatro niveles (45, 90, 135, 180 espacios), en páginas de 45. Se **mejoran en la mesa de crafteo** con cosas que se consiguen jugando (decisión del dueño; antes se había pensado en niveles fijos).
- Más adelante: mostrar la mochila en la espalda del jugador con un modelo 3D (idea del dueño, para cuando todo lo demás esté hecho y funcional).

### Tierras y tiendas
- **Lands** reemplazó al plugin de tierras anterior (LandClaim). **3y33Shops** reemplazó a QuickShop.
- **Tiendas solo dentro de una tierra del dueño**, para que nadie use tiendas ni puntos de viaje como teletransportes gratis.
- **Sin límite de tiendas por tierra** para nadie (decisión del dueño; la clave `maximo-tiendas-por-tierra` está en 30 pero se quiere sacar).
- **Costo de chunks:** sube un 25 % por cada chunk (antes 10 %), y devuelve el 15 % al soltarlo. Sin límite de chunks por tierra.
- **VIP 25 y VIP+ 50 chunks gratis:** está configurado pero **el dueño no lo confirmó** (ver [`07-rangos-y-permisos.md`](07-rangos-y-permisos.md)).

### Servidor
- **Cuentas no premium permitidas** (`online-mode=false`) con **AuthMe** para la seguridad. Es una decisión consciente.
- **El resource pack no es obligatorio** (`require-resource-pack=false`).
- **El pack se despliega fijado a un commit**, no a `main`, para que los clientes no usen copias viejas guardadas.
- **PvP opcional** (`/pvp`), con etiqueta de combate para que nadie se salve desconectándose.
- **Reglas obligatorias en pantalla** antes de poder jugar.
- **Beta cerrada con whitelist.** Se abre cuando estén resueltos los puntos de "antes de abrir" ([`09-pendientes.md`](09-pendientes.md)).

## 4. Cosas que se probaron y se descartaron

- **QuickShop** y **3y33Tiendas**: reemplazados por 3y33Shops (motor propio, atado a Lands, con revisión de integridad).
- **Plugin de tierras viejo (LandClaim)** y **3y33Tierras**: reemplazados por Lands; la copia queda en `respaldo-landclaim/`.
- **EzRTP:** reemplazado por el `/rtp` del Core.
- **Niveles de cuenta con rangos Sheriff, Supreme y Global:** se van a retirar (ver arriba).
- **Pack Bedrock con arte nuevo:** no se rehízo; queda pendiente.

## 5. Cómo se tomaron las decisiones

El dueño suele **hacer una pregunta abierta** ("¿se podrían hacer mochilas 3D?", "¿se upgradean en la mesa?"), el asistente propone con pros y contras, y el dueño elige. Cuando algo no está claro se lo propone por escrito y se espera la confirmación. Si vas a retomar el proyecto, hacé lo mismo: **proponé, esperá el sí y recién ahí implementá**.
