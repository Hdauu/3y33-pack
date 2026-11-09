"""Arte de las mochilas de 3y33Trabajos (16x16, diseño propio): una mochila de cuero con solapa y hebilla, una por nivel (I a IV).
Colores por rareza: I verde, II violeta, III dorado, IV rojo vino. Escribe los archivos DIRECTO en java/3y33-pack.zip
(items/trabajos/mochila_N.json, models/item/trabajos/mochila_N.json, textures/item/trabajos/mochila_N.png) y guarda una vista previa.
Uso: python3 mochilas.py [vista_previa.png]"""
import io, json, os, sys, zipfile, shutil
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
ZIP = os.path.normpath(os.path.join(AQUI, '..', '..', 'java', '3y33-pack.zip'))

# (cuerpo, sombra, luz, solapa, solapa_sombra) por nivel
PALETAS = [
    ((63, 174, 73), (38, 112, 48), (122, 214, 128), (44, 128, 56), (28, 84, 38)),
    ((142, 63, 224), (94, 36, 156), (186, 130, 246), (104, 44, 176), (68, 26, 120)),
    ((242, 176, 30), (176, 118, 14), (255, 220, 110), (200, 140, 20), (140, 92, 10)),
    ((196, 42, 85), (130, 24, 56), (232, 100, 140), (150, 28, 64), (100, 16, 42)),
]
BORDE = (40, 28, 24)
CORREA = (96, 64, 40)
HEBILLA = (255, 226, 130)
HEBILLA_S = (184, 140, 40)


def dibujar(i):
    cuerpo, sombra, luz, solapa, solapa_s = PALETAS[i]
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.putpixel

    def rect(x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1): px((x, y), c + (255,))

    # asa arriba
    rect(6, 1, 9, 1, BORDE); rect(5, 2, 5, 3, BORDE); rect(10, 2, 10, 3, BORDE)
    rect(6, 2, 9, 2, CORREA); rect(6, 3, 6, 3, CORREA); rect(9, 3, 9, 3, CORREA)
    # cuerpo (borde, relleno, luz a la izquierda y sombra a la derecha / abajo)
    rect(3, 4, 12, 14, BORDE)
    rect(4, 5, 11, 13, cuerpo)
    rect(4, 5, 4, 13, luz)
    rect(11, 5, 11, 13, sombra); rect(4, 13, 11, 13, sombra)
    # solapa (arriba del cuerpo, baja en punta)
    rect(4, 5, 11, 8, solapa)
    rect(4, 8, 11, 8, solapa_s)
    px((7, 9), solapa_s + (255,)); px((8, 9), solapa_s + (255,))
    rect(4, 5, 4, 7, tuple(min(255, c + 30) for c in solapa))
    # hebilla
    rect(7, 9, 8, 10, HEBILLA); px((8, 10), HEBILLA_S + (255,)); px((7, 9), (255, 248, 200, 255))
    # bolsillo de abajo con costura
    rect(5, 11, 10, 12, sombra)
    rect(5, 11, 10, 11, cuerpo)
    for x in range(5, 11, 2): px((x, 12), luz + (255,))
    # costado: correas laterales
    rect(2, 7, 2, 11, BORDE); rect(13, 7, 13, 11, BORDE)
    rect(3, 8, 3, 10, CORREA); rect(12, 8, 12, 10, CORREA)
    return im


def main():
    previa = sys.argv[1] if len(sys.argv) > 1 else os.path.join(AQUI, 'mochilas_preview.png')
    imgs = [dibujar(i) for i in range(4)]
    hoja = Image.new('RGBA', (4 * 16 * 8 + 5 * 8, 16 * 8 + 16), (46, 40, 44, 255))
    for i, im in enumerate(imgs):
        hoja.alpha_composite(im.resize((128, 128), Image.NEAREST), (8 + i * (128 + 8), 8))
    hoja.save(previa)

    nuevos = {}
    for n, im in enumerate(imgs, 1):
        b = io.BytesIO(); im.save(b, 'PNG')
        nuevos['assets/3y33/textures/item/trabajos/mochila_%d.png' % n] = b.getvalue()
        nuevos['assets/3y33/models/item/trabajos/mochila_%d.json' % n] = json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": "3y33:item/trabajos/mochila_%d" % n}}, indent=1).encode()
        nuevos['assets/3y33/items/trabajos/mochila_%d.json' % n] = json.dumps(
            {"model": {"type": "minecraft:model", "model": "3y33:item/trabajos/mochila_%d" % n}}, indent=1).encode()

    tmp = ZIP + '.tmp'
    with zipfile.ZipFile(ZIP) as zi, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zo:
        for it in zi.infolist():
            if it.is_dir() or it.filename in nuevos: continue
            zo.writestr(it.filename, zi.read(it.filename))
        for nombre, datos in nuevos.items(): zo.writestr(nombre, datos)
    shutil.move(tmp, ZIP)
    print('listo:', len(nuevos), 'archivos en', ZIP)


if __name__ == '__main__':
    main()
