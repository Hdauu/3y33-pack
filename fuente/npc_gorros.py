"""Gorros 3D de los NPC (uno por trabajo): modelos de cajitas con una textura de muestras de color.
Se ponen en la cabeza del maniqui como un item con item_model "3y33:gorro/<trabajo>".
Medidas: la cabeza ocupa de 1,6 a 14,4 en x, y, z (el frente es el lado norte, z chico); la parte de arriba de la cabeza esta en y=14,4.
Uso: python3 npc_gorros.py <carpeta_pack>"""
import sys, os, json
from PIL import Image

# colores de las muestras (indice -> rgb); cada muestra es un cuadradito de 4x4 de la textura de 16x16
PALETAS = {
    'minero': [(240, 190, 40), (214, 160, 24), (255, 252, 214), (60, 62, 70), (255, 255, 255)],
    'lenador': [(178, 38, 38), (130, 24, 28), (245, 245, 245), (210, 210, 214)],
    'granjero': [(232, 202, 96), (200, 168, 70), (126, 78, 40), (170, 130, 52)],
    'cazador': [(52, 110, 60), (36, 78, 42), (214, 48, 48), (240, 230, 200), (122, 82, 50)],
    'pescador': [(238, 196, 38), (206, 160, 20), (30, 52, 96), (255, 232, 130)],
}

# cajas: (desde, hasta, indice de color)
GORROS = {
    'minero': [
        ([1.0, 12.8, 1.0], [15.0, 15.6, 15.0], 0),        # casco
        ([2.0, 15.6, 2.0], [14.0, 16.8, 14.0], 0),        # cupula
        ([6.4, 16.8, 3.0], [9.6, 17.4, 13.0], 1),         # cresta del casco
        ([0.6, 12.8, -1.4], [15.4, 13.6, 1.0], 1),        # visera del frente
        ([6.0, 13.8, 0.2], [10.0, 15.4, 1.1], 3),         # soporte de la lampara
        ([6.6, 14.2, -0.6], [9.4, 15.0, 0.2], 2),         # lampara
    ],
    'lenador': [
        ([1.0, 11.6, 1.0], [15.0, 13.4, 15.0], 1),        # puño doblado
        ([1.2, 13.4, 1.2], [14.8, 16.0, 14.8], 0),        # gorro
        ([2.4, 16.0, 2.4], [13.6, 17.2, 13.6], 0),
        ([6.0, 17.2, 6.0], [10.0, 20.4, 10.0], 2),        # pompon
    ],
    'granjero': [
        ([-3.0, 14.2, -3.0], [19.0, 14.9, 19.0], 0),      # ala de paja
        ([-2.0, 14.0, -2.0], [18.0, 14.2, 18.0], 1),      # sombra de abajo
        ([2.6, 14.9, 2.6], [13.4, 15.8, 13.4], 2),        # cinta
        ([3.0, 15.8, 3.0], [13.0, 18.6, 13.0], 0),        # copa
        ([4.0, 18.6, 4.0], [12.0, 19.0, 12.0], 3),
    ],
    'cazador': [
        ([1.0, 12.4, 1.0], [15.0, 15.8, 15.0], 0),        # gorro verde
        ([2.2, 15.8, 2.2], [13.8, 17.0, 13.8], 0),
        ([0.6, 12.4, -1.8], [15.4, 13.4, 1.0], 1),        # visera
        ([13.4, 14.6, 5.0], [14.6, 15.6, 11.0], 2),       # pluma roja (base)
        ([14.0, 15.6, 6.0], [15.0, 20.0, 10.0], 2),       # pluma roja
        ([14.2, 20.0, 7.0], [14.8, 21.4, 9.0], 3),        # punta clara
    ],
    'pescador': [
        ([-3.4, 12.4, -3.4], [19.4, 13.4, 19.4], 0),      # ala ancha
        ([-3.4, 12.4, 15.0], [19.4, 7.4, 19.4], 0),       # cubre nuca (atras)
        ([2.0, 13.4, 2.0], [14.0, 17.4, 14.0], 0),        # copa
        ([1.8, 13.4, 1.8], [14.2, 14.6, 14.2], 2),        # cinta azul
        ([6.4, 14.6, 1.6], [9.6, 16.8, 2.0], 3),          # insignia
    ],
}


def textura(j):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for k, c in enumerate(PALETAS[j]):
        x0, y0 = (k % 4) * 4, (k // 4) * 4
        for y in range(4):
            for x in range(4): im.putpixel((x0 + x, y0 + y), c + (255,))
    return im


def modelo(j):
    els = []
    for d, h, k in GORROS[j]:
        desde = [min(u, v) for u, v in zip(d, h)]
        hasta = [max(u, v) for u, v in zip(d, h)]
        x0, y0 = (k % 4) * 4 + 1, (k // 4) * 4 + 1
        uv = [x0, y0, x0 + 2, y0 + 2]
        els.append({'from': desde, 'to': hasta, 'faces': {c: {'uv': uv, 'texture': '#0'} for c in ('north', 'south', 'east', 'west', 'up', 'down')}})
    return {'textures': {'0': '3y33:gorro/' + j, 'particle': '3y33:gorro/' + j}, 'elements': els,
            'display': {'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}


def escribir(pack):
    base = os.path.join(pack, 'assets', '3y33')
    for sub in ('textures/gorro', 'models/gorro', 'items/gorro'): os.makedirs(os.path.join(base, sub), exist_ok=True)
    for j in GORROS:
        textura(j).save(os.path.join(base, 'textures', 'gorro', j + '.png'))
        json.dump(modelo(j), open(os.path.join(base, 'models', 'gorro', j + '.json'), 'w'), indent=1)
        json.dump({'model': {'type': 'minecraft:model', 'model': '3y33:gorro/' + j}}, open(os.path.join(base, 'items', 'gorro', j + '.json'), 'w'), indent=1)
    return len(GORROS)


if __name__ == '__main__':
    print(escribir(sys.argv[1]), 'gorros')
