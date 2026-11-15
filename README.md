# 3y33 SMP

Servidor de Minecraft **survival vanilla+** para Java y Bedrock, en español rioplatense. Es un SMP donde "lo que da ventaja se gana jugando y lo que se paga es solo visual". Este repositorio tiene **todo lo propio del proyecto**: los plugins que escribimos, el resource pack con su código fuente y la documentación para seguirlo.

> **Estado al 15/11/2026:** el desarrollo está en pausa. El server funciona (beta cerrada, con whitelist) pero **todavía no abrió al público**. Quien lo retome tiene que empezar por [`docs/01-traspaso.md`](docs/01-traspaso.md).

**Este repositorio es público.** No guardes acá contraseñas, tokens, claves del panel ni datos personales. Los accesos se piden a Francisco (`hdau`), dueño del proyecto.

## Por dónde empezar

| Si querés... | Leé |
|---|---|
| Entender en qué estado quedó todo y qué hacer el primer día | [`docs/01-traspaso.md`](docs/01-traspaso.md) |
| Saber cómo está armado el server (hosting, plugins, mundos, datos) | [`docs/02-arquitectura.md`](docs/02-arquitectura.md) |
| Operar el server: reiniciar, desplegar un plugin o el pack, backups, comandos útiles | [`docs/03-operacion.md`](docs/03-operacion.md) |
| Tocar el código de nuestros plugins | [`docs/04-plugins-propios.md`](docs/04-plugins-propios.md) |
| Entender el sistema de trabajos (el más grande) | [`docs/05-trabajos.md`](docs/05-trabajos.md) |
| Cambiar texturas, fuentes, íconos o regenerar el pack | [`docs/06-resource-pack.md`](docs/06-resource-pack.md) |
| Ver rangos, permisos y límites de Lands | [`docs/07-rangos-y-permisos.md`](docs/07-rangos-y-permisos.md) |
| Saber por qué se decidió cada cosa (y qué reglas puso el dueño) | [`docs/08-decisiones.md`](docs/08-decisiones.md) |
| Ver todo lo que falta hacer, priorizado | [`docs/09-pendientes.md`](docs/09-pendientes.md) |
| Consultar manuales y planes viejos (pueden estar desactualizados) | [`docs/referencia/`](docs/referencia/) |

## Qué hay en el repositorio

```
plugins/                Código y jars de nuestros plugins (uno por carpeta)
  3y33Core/             Reglas, anuncios, /report, /rtp, PvP, estilos de chat, editor de hologramas y NPCs
  3y33Login/            Títulos en pantalla para el login de AuthMe (se compila a mano, ver su README)
  3y33Shops/            Tiendas de cofre con motor propio, atadas a las tierras de Lands
  3y33Trabajos/         Cinco trabajos con XP, misiones, recompensas por nivel, mochilas e insignias
  3y33Tierras/          (histórico) Extras para el plugin de tierras anterior. Ya no se usa
  3y33Tiendas/          (histórico) Reglas para QuickShop. Ya no se usa
  terceros/             Jars de terceros que bajamos para compilar o probar (Geyser, Multiverse)
java/3y33-pack.zip      Resource pack para Java (lo manda el server al entrar)
bedrock/                Pack para Bedrock (lo manda Geyser)
fuente/                 Scripts en Python que generan las texturas, fuentes y modelos del pack
.github/workflows/      Compilación automática de los plugins (GitHub Actions)
docs/                   Documentación
```

Archivos sueltos en la raíz que **no son documentación**: `api-fancy.txt`, `api-landclaim*.txt` (volcados de la API de plugins de terceros, sacados con los workflows `explorar` e `inspeccion`) y `build-log.txt` (el último error de compilación que guardó el CI; solo es útil si falló una compilación).

## Cómo funciona el flujo de trabajo en dos líneas

1. Se edita el código de un plugin y se hace `push` a `main`: GitHub Actions lo compila y **commitea el `.jar` nuevo** en `plugins/<nombre>/` con el mensaje `<plugin>: jar compilado`.
2. Ese jar se instala en el server desde el panel (bajándolo del repo). El resource pack funciona parecido pero el server lo baja de GitHub fijado a un commit concreto.

Los pasos exactos están en [`docs/03-operacion.md`](docs/03-operacion.md).

## Créditos y licencias

- Banderas: [flag-icons](https://github.com/lipis/flag-icons) (MIT).
- Fuente Press Start 2P: © The Press Start 2P Project Authors, SIL Open Font License 1.1 (`fuente/fuentes/OFL.txt`).
- Todo el arte del pack (íconos, cartas, mochilas, insignias, NPC, ventanas) es **original del proyecto**. Los íconos de ítems de Minecraft que se ven en algunas insignias se referencian desde el pack base del juego, no se copian.
- Plugins de terceros (Lands, LuckPerms, EssentialsX, etc.): cada uno con su licencia; no están en este repo salvo los jars de `plugins/terceros/`.
