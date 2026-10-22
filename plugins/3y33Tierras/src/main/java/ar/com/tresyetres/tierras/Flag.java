package ar.com.tresyetres.tierras;

import org.bukkit.Material;

/** Opciones de naturaleza de cada terreno. "activo = true" significa que la cosa SUCEDE dentro del terreno. */
enum Flag {
    MONSTRUOS("monstruos", Material.ZOMBIE_HEAD, "#ff8787", "#e03131", "Monstruos",
            "Zombies, esqueletos, creepers, arañas", "y demás monstruos que aparecen solos."),
    PHANTOMS("phantoms", Material.PHANTOM_MEMBRANE, "#b197fc", "#7048e8", "Phantoms",
            "Los que te atacan de noche", "cuando no dormís."),
    ANIMALES("animales", Material.PORKCHOP, "#ffc9c9", "#f783ac", "Animales",
            "Vacas, ovejas, cerdos y demás", "animales que aparecen solos."),
    FUEGO("fuego", Material.CAMPFIRE, "#ffa94d", "#e8590c", "Fuego",
            "Que el fuego se propague", "y queme bloques."),
    LIQUIDOS("liquidos", Material.LAVA_BUCKET, "#74c0fc", "#1971c2", "Líquidos de afuera",
            "Que el agua o la lava de", "afuera entre al terreno."),
    CULTIVOS("cultivos", Material.FARMLAND, "#d8f5a2", "#66a80f", "Pisoteo de cultivos",
            "Que los mobs rompan los", "cultivos al pisarlos."),
    HOJAS("hojas", Material.OAK_LEAVES, "#b2f2bb", "#2b8a3e", "Caída de hojas",
            "Que las hojas sueltas", "se desarmen solas.");

    final String clave;
    final Material icono;
    final String c1, c2, nombre;
    final String[] descripcion;

    Flag(String clave, Material icono, String c1, String c2, String nombre, String... descripcion) {
        this.clave = clave;
        this.icono = icono;
        this.c1 = c1;
        this.c2 = c2;
        this.nombre = nombre;
        this.descripcion = descripcion;
    }
}
