"""Libro rojo con borde oscuro y detalles dorados (boton "Qué se entrega" de la mision global), mismo estilo que las flechas. 16x16.
Uso: python3 libro.py <salida.png>"""
import sys
from PIL import Image

LUZ, MEDIO, SOMBRA, CONTORNO = (255, 120, 132), (226, 40, 58), (150, 18, 36), (60, 6, 14)
ORO, ORO_SOMBRA = (255, 214, 96), (190, 132, 40)
PAPEL, PAPEL_SOMBRA = (250, 240, 214), (206, 190, 156)


def libro():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    def p(x, y, c): im.putpixel((x, y), c + (255,))
    # tapa: x 2..13, y 1..13 ; paginas debajo: y 14
    for y in range(1, 14):
        for x in range(2, 14):
            p(x, y, MEDIO)
    for x in range(2, 14):
        p(x, 1, LUZ)                       # borde de arriba, con luz
        p(x, 13, SOMBRA)                   # borde de abajo, sombra
    for y in range(1, 14):
        p(2, y, LUZ if y < 13 else SOMBRA)  # canto izquierdo claro
        p(13, y, SOMBRA)                   # canto derecho en sombra
    # lomo: franja oscura a la izquierda con dos bandas doradas
    for y in range(2, 13):
        p(3, y, SOMBRA); p(4, y, SOMBRA)
    for y in (3, 11):
        p(3, y, ORO); p(4, y, ORO)
    # emblema dorado en el centro de la tapa: un rombo
    cx, cy = 9, 7
    for dx, dy in ((0, -2), (0, 2), (-2, 0), (2, 0)): p(cx + dx, cy + dy, ORO_SOMBRA)
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0), (-1, -1), (1, -1), (-1, 1), (1, 1)): p(cx + dx, cy + dy, ORO)
    p(cx, cy, (255, 244, 196))
    # paginas (canto de abajo)
    for x in range(4, 14):
        p(x, 14, PAPEL if x < 13 else PAPEL_SOMBRA)
    # contorno
    forma = {(x, y) for x in range(16) for y in range(16) if im.getpixel((x, y))[3]}
    for (x, y) in list(forma):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in forma and 0 <= q[0] < 16 and 0 <= q[1] < 16: p(q[0], q[1], CONTORNO)
    return im


if __name__ == '__main__':
    libro().save(sys.argv[1])
