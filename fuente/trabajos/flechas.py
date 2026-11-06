"""Flechas dobles « » para pasar de pagina: rojas con borde oscuro, como las de Masivo pero dobles. 16x16 (un casillero).
Uso: python3 flechas.py hoja <salida.png> | python3 flechas.py <carpeta_pack>"""
import sys, os, json
from PIL import Image



PALETAS = {
    'rojo': ((255, 120, 132), (226, 40, 58), (150, 18, 36), (60, 6, 14)),
    'oro': ((255, 240, 160), (240, 190, 70), (170, 112, 28), (70, 40, 8)),
    'rojo_dorado': ((255, 120, 132), (226, 40, 58), (150, 18, 36), (214, 164, 65)),
    'blanco': ((255, 255, 255), (226, 226, 236), (150, 150, 170), (50, 40, 60)),
}


def flecha(derecha, tips=(3, 6), alto=11, trazo=3, paleta='rojo', dx=0, dy=1):
    """Dos chevrones pegados. alto: filas del chevron (impar); trazo: grosor horizontal; tips: x de la punta de cada uno."""
    luz, medio, sombra, contorno = PALETAS[paleta]
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    forma = set()
    mitad = alto // 2
    y0 = (16 - alto) // 2 + dy
    for tip in tips:
        for r in range(alto):
            d = abs(r - mitad)
            for k in range(trazo):
                forma.add((tip + dx + d + k, y0 + r))
    if derecha: forma = {(15 - x, y) for (x, y) in forma}
    forma = {(x, y) for (x, y) in forma if 0 <= x < 16}
    borde = set()
    for (x, y) in forma:
        for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + ddx, y + ddy)
            if p not in forma and 0 <= p[0] < 16 and 0 <= p[1] < 16: borde.add(p)
    for p in borde: im.putpixel(p, contorno + (255,))
    for (x, y) in forma:
        arriba = (x, y - 1) not in forma
        izq = (x - 1, y) not in forma if not derecha else (x + 1, y) not in forma
        abajo = (x, y + 1) not in forma
        col = luz if (arriba or izq) and not abajo else sombra if abajo and not arriba else medio
        im.putpixel((x, y), col + (255,))
    return im


def escribir(pack):
    tex = os.path.join(pack, 'assets', '3y33', 'textures', 'item', 'trabajos')
    mod = os.path.join(pack, 'assets', '3y33', 'models', 'item', 'trabajos')
    its = os.path.join(pack, 'assets', '3y33', 'items', 'trabajos')
    for nombre, der in (('flecha_izq', False), ('flecha_der', True)):
        flecha(der).save(os.path.join(tex, nombre + '.png'))
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': '3y33:item/trabajos/' + nombre}}, open(os.path.join(mod, nombre + '.json'), 'w'), indent=1)
        json.dump({'model': {'type': 'minecraft:model', 'model': '3y33:item/trabajos/' + nombre}}, open(os.path.join(its, nombre + '.json'), 'w'), indent=1)
    return 2


if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[1] == 'hoja':
        K = 16
        sh = Image.new('RGBA', (2 * 16 * K + 3 * 20, 16 * K + 40), (222, 196, 156, 255))
        for i, d in enumerate((False, True)):
            sh.alpha_composite(flecha(d).resize((16 * K, 16 * K), Image.NEAREST), (20 + i * (16 * K + 20), 20))
        sh.save(sys.argv[2]); print('ok')
    else:
        print(escribir(sys.argv[1]), 'flechas')
