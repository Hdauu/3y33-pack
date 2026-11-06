"""Cartas grandes de los trabajos: 48x72 px (3 casilleros de ancho x 4 de alto), una por trabajo / marco de prestigio / estado.
Se muestran como item "oversized" del menu, con escala 3 sobre un modelo de 16x24: cada pixel de la textura es un pixel del juego.
Estados: normal, activo (aro + gema verde) y bloqueado (gris con candado, para los trabajos que no se pueden tomar ahora).
Uso: python3 cartas48.py hoja <salida.png>  |  python3 cartas48.py <carpeta_pack>"""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import pixel3
from pixel3 import ACC, ICON, CW, CH
from estados import activa, bloqueada

IDS = {'MINERO': 'minero', 'LEÑADOR': 'lenador', 'GRANJERO': 'granjero', 'CAZADOR': 'cazador', 'PESCADOR': 'pescador'}
MARCOS = {'hierro': (128, 134, 146), 'bronce': (200, 120, 58), 'plata': (212, 222, 238), 'oro': pixel3.GOLD}
ORDEN = ['hierro', 'bronce', 'plata', 'oro']
ESTADOS = ['normal', 'activo', 'bloqueado']
REAL = pixel3.GOLD

# el icono se dibuja siempre con los colores de siempre (el oro del marco es lo unico que cambia con el prestigio)
_icono = pixel3.icono
_cache = {}
def _icono_fijo(nombre):
    if nombre not in _cache:
        g = pixel3.GOLD; pixel3.GOLD = REAL
        try: _cache[nombre] = _icono(nombre)
        finally: pixel3.GOLD = g
    return _cache[nombre].copy()
pixel3.icono = _icono_fijo


def carta(nombre, marco, estado):
    pixel3.GOLD = MARCOS[marco]
    try: c = pixel3.carta(nombre, 'oro_rayos')
    finally: pixel3.GOLD = REAL
    if estado == 'activo': c = activa(c, ACC[nombre])
    elif estado == 'bloqueado': c = bloqueada(c)
    return c


# ---------- ubicacion en el menu ----------
# Menu de 6 filas: las cartas ocupan las filas 1 a 4 (y 34 a 106). Cada carta cuelga de un casillero de la fila 3.
# Primera pagina: 3 cartas (izquierda x=10, 64 y 118), segunda pagina: 2 cartas centradas (x=37 y 91).
# Casilleros de 18 px; el primero empieza en x=7. Posicion de la carta: x = sx + 3*b - 16 (sx: origen del casillero), y = 34 fijo.
POS = {  # trabajo: (x izquierdo de la carta, columna del casillero donde se pone el item)
    'minero': (10, 1), 'lenador': (64, 4), 'granjero': (118, 7),
    'cazador': (37, 2), 'pescador': (91, 5),
}
A = 2 / 3   # y inferior del modelo: en el juego deja la carta con el borde de arriba en y=30 (medido en pantalla), centrada en la caja


def x0(tid):
    izq, col = POS[tid]
    sx = 7 + 18 * col
    return (izq - sx + 15) / 3   # el item se dibuja 1 px a la derecha del origen del casillero (medido en pantalla)


def modelo(b):
    return {
        'gui_light': 'front',
        'textures': {'layer0': ''},
        'display': {'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [3, 3, 3]}},
        'elements': [{'from': [b, A, 7.5], 'to': [b + 16, A + 24, 8.5], 'faces': {
            'south': {'uv': [0, 0, 16, 16], 'texture': '#layer0'},
            'north': {'uv': [16, 0, 0, 16], 'texture': '#layer0'}}}],
    }


def escribir(pack):
    tex = os.path.join(pack, 'assets', '3y33', 'textures', 'item', 'trabajos')
    mod = os.path.join(pack, 'assets', '3y33', 'models', 'item', 'trabajos')
    its = os.path.join(pack, 'assets', '3y33', 'items', 'trabajos')
    for p in (tex, mod, its): os.makedirs(p, exist_ok=True)
    n = 0
    for nombre, tid in IDS.items():
        for mid in ORDEN:
            for estado in ESTADOS:
                clave = 'carta_%s_%s_%s' % (tid, mid, estado)
                carta(nombre, mid, estado).save(os.path.join(tex, clave + '.png'))
                for prefijo, b in (('', x0(tid)), ('detalle_', (64 - 79 + 15) / 3)):
                    m = modelo(b); m['textures']['layer0'] = '3y33:item/trabajos/' + clave
                    json.dump(m, open(os.path.join(mod, prefijo + clave + '.json'), 'w'), indent=1)
                    json.dump({'oversized_in_gui': True, 'model': {'type': 'minecraft:model', 'model': '3y33:item/trabajos/' + prefijo + clave}},
                              open(os.path.join(its, prefijo + clave + '.json'), 'w'), indent=1)
                n += 1
    Image.new('RGBA', (16, 16), (0, 0, 0, 0)).save(os.path.join(tex, 'vacio.png'))
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': '3y33:item/trabajos/vacio'}}, open(os.path.join(mod, 'vacio.json'), 'w'), indent=1)
    json.dump({'model': {'type': 'minecraft:model', 'model': '3y33:item/trabajos/vacio'}}, open(os.path.join(its, 'vacio.json'), 'w'), indent=1)
    return n


def hoja(salida, K=3):
    pad = 8
    sh = Image.new('RGBA', (pad + 12 * (CW * K + pad), pad + 5 * (CH * K + pad)), (40, 40, 44, 255))
    for r, (nombre, tid) in enumerate(IDS.items()):
        c = 0
        for mid in ORDEN:
            for estado in ESTADOS:
                im = carta(nombre, mid, estado).resize((CW * K, CH * K), Image.NEAREST)
                sh.alpha_composite(im, (pad + c * (CW * K + pad), pad + r * (CH * K + pad))); c += 1
    sh.save(salida)


if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[1] == 'hoja':
        hoja(sys.argv[2], 2); print('ok')
    else:
        print(escribir(sys.argv[1]), 'cartas')
