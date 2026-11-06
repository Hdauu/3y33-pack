"""Recupera una skin 64x64 desde una captura donde se mostro ampliada 8x sobre fondo magenta (el magenta es transparente)."""
import sys
from PIL import Image
import numpy as np

def recuperar(ruta, salida):
    im = np.array(Image.open(ruta).convert('RGB')).astype(int)
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    for ty in range(64):
        for tx in range(64):
            blk = im[ty * 8 + 2: ty * 8 + 6, tx * 8 + 2: tx * 8 + 6].reshape(-1, 3)
            m = np.median(blk, axis=0)
            if m[0] > 200 and m[1] < 70 and m[2] > 200: continue
            out.putpixel((tx, ty), tuple(int(v) for v in m) + (255,))
    out.save(salida)
    return out

if __name__ == '__main__':
    recuperar(sys.argv[1], sys.argv[2])
