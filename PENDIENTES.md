# Pendientes (anotados por Francisco)

## Trabajos: recompensas (próxima sesión)
- [x] Icono de "reclamar" cambiado a un regalo (1.7.2).
- [x] Reclamo en orden (1.7.2). 1.7.3: reclamadas quedan vacías, solo se ve lo del trabajo activo (de los otros solo misiones), "no" del aldeano al mover items.
- [ ] (Opcional, a decidir) Rareza de las cartas: asegurar al menos una rara o mejor, o bajar el peso de las comunes (hoy 65/24/8/2,5, mega 0,1 %).
- [ ] (A decidir) Pasar `ventana-banner` de las tiendas de `staff` a `todos`.

## Hitos e insignias (diseño acordado el 08/11)
- Insignias: 4 rarezas — Común (verde) desde nivel 30, Épica (violeta) 65, Legendaria (dorado) 100, Mítica (rojo vino) 130. Emblema por trabajo, arte propio (lo hace el otro chat), hoja de 5x4 celdas de 20x20.
- Chunks: chance en cada opción de las ofertas normales (subida a 2 %, antes 0,5 %), tope de +12 por cuenta; además chunk garantizado como opción en los hitos 50, 90 y 130.
- Hitos: 10,20,30,40,45,50,60,65 (mochila IV),70,75,90 con elección; 100/115/130 encantamientos fijos.
- Código de hitos (1.8.0): pendiente de arte de insignias para la parte de chat.

## Estado 08/11
- Trabajos 1.8.0 en el servidor: hitos (dinero, pack de boosts, chunk gratis, mochila IV y encantamientos fijos). `chunks.activo: false` hasta que se arme el lado de Lands.
- Lands: costo por claim +25 % (era 10 %), ya aplicado (backup /respaldo-prebeta/Lands-config.yml.pre-costo25).
- Falta (lado Lands, al final, a confirmar con Francisco): sin límite de chunks/tierra (`land_size`, `player_chunks`), chunks gratis VIP 25 y VIP+ 50 (¿confirmado?), sacar pack Donador (mover a respaldo), packs `3y33.chunk.1..25` (+1 chunk gratis c/u), probar si Lands toma el permiso sin reloguear; después `chunks.activo: true`.
- Falta: insignias (arte del otro chat), cosméticos de hito, encantamientos y mochila (los hitos fijos solo guardan el desbloqueo).
