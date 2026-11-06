package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.format.TextColor;

/** Rareza de una accion: se sortea cada dia y suma al multiplicador (comun +0, rara +0,5, epica +1, legendaria +2). */
enum Rareza {
    COMUN("Común", 1.0, 0xBFBFBF),
    RARA("Rara", 1.5, 0x55A8FF),
    EPICA("Épica", 2.0, 0xB455FF),
    LEGENDARIA("Legendaria", 3.0, 0xFFAA00);

    final String nombre;
    final double mult;
    final TextColor color;

    Rareza(String nombre, double mult, int rgb) {
        this.nombre = nombre;
        this.mult = mult;
        this.color = TextColor.color(rgb);
    }

    /** Lo que suma al multiplicador personal. */
    double bono() { return mult - 1.0; }
}
