import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops
import numpy as np
S = 8                      # supersampling
W, H = 176, 168            # pixeles logicos de la ventana de 3 filas
OUT = 2                    # el png final es de 2x
FONT = '/home/claude/3y33-pack/fuente/fuentes/PressStart2P.ttf'

def px(v): return int(round(v * S))
def box(x0, y0, x1, y1): return [px(x0), px(y0), px(x1) - 1, px(y1) - 1]

img = Image.new('RGBA', (W * S, H * S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

def vgrad(w, h, top, bot):
    t = np.linspace(0, 1, h)[:, None, None]
    a = (np.array(top)[None, None, :] * (1 - t) + np.array(bot)[None, None, :] * t)
    a = np.repeat(a, w, axis=1)
    return Image.fromarray(a.astype('uint8'), 'RGB').convert('RGBA')

# ---- fondo con borde ----
mask = Image.new('L', img.size, 0)
ImageDraw.Draw(mask).rounded_rectangle(box(0, 0, W, H), radius=px(4), fill=255)
bg = vgrad(W * S, H * S, (46, 24, 29), (24, 13, 16))
img.paste(bg, (0, 0), mask)
d = ImageDraw.Draw(img)
d.rounded_rectangle(box(0, 0, W, H), radius=px(4), outline=(10, 5, 7, 255), width=px(1))
d.rounded_rectangle(box(1, 1, W - 1, H - 1), radius=px(3), outline=(128, 26, 36, 255), width=px(1))
d.rounded_rectangle(box(2, 2, W - 2, H - 2), radius=px(2.5), outline=(214, 164, 65, 255), width=px(0.6))

# ---- toldo (rayas rojas y crema con flecos) ----
ax0, ax1, ay0, ay1 = 7, 169, 4, 13
n = 18
sw = (ax1 - ax0) / n
awn = Image.new('RGBA', img.size, (0, 0, 0, 0))
ad = ImageDraw.Draw(awn)
for i in range(n):
    x0 = ax0 + i * sw
    col = (196, 40, 52, 255) if i % 2 == 0 else (246, 236, 216, 255)
    ad.rectangle(box(x0, ay0, x0 + sw, ay1), fill=col)
    # fleco redondeado
    ad.pieslice(box(x0, ay1 - sw / 2, x0 + sw, ay1 + sw / 2), 0, 180, fill=col)
# sombreado vertical del toldo
sh = vgrad(W * S, H * S, (255, 255, 255), (60, 20, 24))
shade = np.array(sh)[:, :, :3].astype(float) / 255.0
a = np.array(awn).astype(float)
ymap = np.linspace(0, 1, H * S)[:, None]
k = 1.0 - 0.38 * np.clip((ymap - ay0 / H) / ((ay1 + 3) / H - ay0 / H), 0, 1)
for c in range(3): a[:, :, c] = a[:, :, c] * k
awn = Image.fromarray(a.astype('uint8'), 'RGBA')
# sombra bajo el toldo
shadow = Image.new('RGBA', img.size, (0, 0, 0, 0))
alpha = awn.split()[3].filter(ImageFilter.GaussianBlur(px(1.2)))
shadow.putalpha(alpha.point(lambda v: int(v * 0.55)))
img.alpha_composite(shadow, (0, px(1.2)))
img.alpha_composite(awn)
d = ImageDraw.Draw(img)
# barra de madera arriba del toldo
d.rounded_rectangle(box(6, 3, 170, 5), radius=px(1), fill=(92, 52, 30, 255), outline=(40, 22, 14, 255), width=px(0.4))
d.line([px(7), px(3.7), px(169), px(3.7)], fill=(150, 98, 58, 255), width=px(0.4))

# ---- cartel con TIENDA ----
cx0, cx1, cy0, cy1 = 38, 138, 17, 35
d.rounded_rectangle(box(cx0 + 0.6, cy0 + 1, cx1 + 0.6, cy1 + 1), radius=px(3), fill=(0, 0, 0, 110))
plaque = vgrad(px(cx1 - cx0), px(cy1 - cy0), (62, 30, 36), (30, 15, 19))
pm = Image.new('L', plaque.size, 0)
ImageDraw.Draw(pm).rounded_rectangle([0, 0, plaque.size[0] - 1, plaque.size[1] - 1], radius=px(3), fill=255)
img.paste(plaque, (px(cx0), px(cy0)), pm)
d = ImageDraw.Draw(img)
d.rounded_rectangle(box(cx0, cy0, cx1, cy1), radius=px(3), outline=(214, 164, 65, 255), width=px(0.9))
d.rounded_rectangle(box(cx0 + 1.6, cy0 + 1.6, cx1 - 1.6, cy1 - 1.6), radius=px(2), outline=(120, 80, 30, 255), width=px(0.4))
# remaches
for rx, ry in ((cx0 + 3.2, cy0 + 3.2), (cx1 - 3.2, cy0 + 3.2), (cx0 + 3.2, cy1 - 3.2), (cx1 - 3.2, cy1 - 3.2)):
    d.ellipse(box(rx - 0.9, ry - 0.9, rx + 0.9, ry + 0.9), fill=(238, 190, 80, 255), outline=(120, 80, 30, 255), width=px(0.25))

font = ImageFont.truetype(FONT, px(11))
txt = 'TIENDA'
tw = d.textlength(txt, font=font)
tx = (W * S - tw) / 2
ty = px(cy0 + 3.6)
# sombra
d.text((tx + px(0.9), ty + px(0.9)), txt, font=font, fill=(0, 0, 0, 200))
# texto dorado con degradado
tm = Image.new('L', img.size, 0)
ImageDraw.Draw(tm).text((tx, ty), txt, font=font, fill=255)
gold = vgrad(img.size[0], img.size[1], (255, 232, 140), (226, 150, 42))
img.paste(gold, (0, 0), tm)
d = ImageDraw.Draw(img)

# ---- monedas a los costados ----
def moneda(cx, cy, r):
    d.ellipse(box(cx - r - 0.5, cy - r + 0.2, cx + r + 0.5, cy + r + 0.9), fill=(0, 0, 0, 90))
    d.ellipse(box(cx - r, cy - r, cx + r, cy + r), fill=(176, 121, 34, 255), outline=(92, 58, 14, 255), width=px(0.4))
    d.ellipse(box(cx - r + 0.7, cy - r + 0.7, cx + r - 0.7, cy + r - 0.7), fill=(246, 196, 76, 255))
    d.ellipse(box(cx - r + 2.0, cy - r + 2.0, cx + r - 2.0, cy + r - 2.0), outline=(190, 134, 40, 255), width=px(0.5))
    d.line([px(cx - r * 0.45), px(cy + r * 0.25), px(cx + r * 0.1), px(cy - r * 0.5)], fill=(255, 238, 170, 255), width=px(0.6))
moneda(23, 28.5, 6.2); moneda(14.5, 25.5, 4.2); moneda(153, 28.5, 6.2); moneda(161.5, 25.5, 4.2)
# rombos decorativos
for gx in (30.2, 145.8):
    pass

# ---- zona de contenido: pads de slots ----
def pad(x, y):
    # (x, y) = esquina del marco 18x18 de un slot
    d.rounded_rectangle(box(x, y, x + 18, y + 18), radius=px(1.4), fill=(15, 8, 10, 255), outline=(78, 40, 47, 255), width=px(0.7))
    d.line([px(x + 1), px(y + 17.4), px(x + 17), px(y + 17.4)], fill=(96, 50, 58, 255), width=px(0.5))
    d.line([px(x + 1), px(y + 0.9), px(x + 17), px(y + 0.9)], fill=(6, 3, 4, 255), width=px(0.5))
# fila 1 y 2 del contenedor (la fila 0 es el cartel)
for r in (1, 2):
    for c in range(9):
        pad(7 + 18 * c, 17 + 18 * r)
# separador
d.line([px(8), px(73), px(168), px(73)], fill=(128, 26, 36, 255), width=px(0.6))
d.line([px(8), px(73.8), px(168), px(73.8)], fill=(214, 164, 65, 120), width=px(0.3))

# etiqueta clara detras del texto "Inventario" (el juego lo escribe en gris oscuro)
d.rounded_rectangle(box(5.5, 72.2 + 1.2, 62, 84.2), radius=px(2.2), fill=(222, 208, 182, 255), outline=(120, 80, 30, 255), width=px(0.5))

# inventario del jugador
for r in range(3):
    for c in range(9):
        pad(7 + 18 * c, 84 + 18 * r)
for c in range(9):
    pad(7 + 18 * c, 142)

out = img.resize((W * OUT, H * OUT), Image.LANCZOS)
out.save('tienda_gui.png')  # se copia a assets/3y33/textures/font/tienda_gui.png
# vista previa agrandada sobre gris para ver los bordes
prev = Image.new('RGBA', out.size, (120, 120, 120, 255)); prev.alpha_composite(out)
prev = prev.resize((out.size[0] * 2, out.size[1] * 2), Image.NEAREST); prev.save('preview.png')
print(out.size)
