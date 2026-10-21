from PIL import Image
# Icono 16x16: insignia roja con borde oscuro y 3 lineas blancas (menu)
S=16; im=Image.new('RGBA',(S,S),(0,0,0,0)); p=im.load()
dark=(90,0,0,255); red1=(200,20,20,255); red2=(255,85,85,255); white=(255,255,255,255); sh=(150,10,10,255)
for y in range(1,15):
    for x in range(1,15):
        corner=(x in (1,14) and y in (1,14))
        if corner: continue
        edge = x in (1,14) or y in (1,14) or (x in (2,13) and y in (2,13))
        if edge: p[x,y]=dark
        else:
            t=(y-2)/11
            p[x,y]=tuple(int(red2[i]*(1-t)+red1[i]*t) for i in range(3))+(255,)
for y in (5,8,11):
    for x in range(4,12): p[x,y]=white
    for x in range(5,13): 
        if p[x,y+1]!=dark: p[x,y+1]=sh
im.save('menu.png'); im.resize((128,128),Image.NEAREST).save('menu_preview.png')
