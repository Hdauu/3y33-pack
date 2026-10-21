import os, io, json, zipfile, hashlib, cairosvg
from PIL import Image
SRC='/home/claude/flagicons/flags/4x3'
codes=sorted(f[:-4] for f in os.listdir(SRC) if f.endswith('.svg') and len(f[:-4])==2)
W,H=32,24
def render(c):
    png=cairosvg.svg2png(url=os.path.join(SRC,c+'.svg'),output_width=W,output_height=H)
    im=Image.open(io.BytesIO(png)).convert('RGBA')
    # borde oscuro sutil de 1px para que las banderas blancas se distingan
    px=im.load()
    for x in range(W):
        for y in (0,H-1): px[x,y]=(40,40,40,255) if px[x,y][3]==0 else tuple(int(v*0.75) for v in px[x,y][:3])+(255,)
    for y in range(H):
        for x in (0,W-1): px[x,y]=tuple(int(v*0.75) for v in px[x,y][:3])+(255,)
    return im
imgs={c:render(c) for c in codes}
mapping={}
for i,c in enumerate(codes): mapping[c.upper()]=0xE100+i
# ---------- Java ----------
cols=16; rows=(len(codes)+cols-1)//cols
sheet=Image.new('RGBA',(cols*W,rows*H),(0,0,0,0))
charrows=[]
for r in range(rows):
    s=''
    for k in range(cols):
        i=r*cols+k
        if i<len(codes):
            sheet.paste(imgs[codes[i]],(k*W,r*H)); s+=chr(0xE100+i)
        else: s+='\u0000'
    charrows.append(s)
jbuf=io.BytesIO(); sheet.save(jbuf,'PNG')
font={"providers":[{"type":"bitmap","file":"3y33:font/flags.png","height":8,"ascent":7,"chars":charrows}]}
mcmeta={"pack":{"description":"3y33 SMP","pack_format":75,"supported_formats":[15,999],"min_format":15,"max_format":999}}
os.makedirs('/home/claude/pack/out',exist_ok=True)
jzip='/home/claude/pack/out/3y33-pack.zip'
with zipfile.ZipFile(jzip,'w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('pack.mcmeta',json.dumps(mcmeta,indent=2))
    z.writestr('assets/minecraft/font/default.json',json.dumps(font,ensure_ascii=True,indent=1))
    z.writestr('assets/3y33/textures/font/flags.png',jbuf.getvalue())
    z.writestr('CREDITS.txt','Banderas: flag-icons (MIT) - https://github.com/lipis/flag-icons\n')
# ---------- Bedrock (Geyser) ----------
import uuid
bz='/home/claude/pack/out/3y33-bedrock.mcpack'
sheets={}
for c,cp in mapping.items():
    page=(cp>>8)&0xFF; idx=cp&0xFF
    if page not in sheets: sheets[page]=Image.new('RGBA',(16*32,16*32),(0,0,0,0))
    cell=Image.new('RGBA',(32,32),(0,0,0,0)); cell.paste(imgs[c.lower()],(0,4))
    sheets[page].paste(cell,((idx%16)*32,(idx//16)*32))
manifest={"format_version":2,"header":{"name":"3y33 SMP","description":"Banderas 3y33","uuid":"6f3a1c3e-3y33-0000-0000-000000000001","version":[1,0,0],"min_engine_version":[1,20,0]},"modules":[{"type":"resources","uuid":"6f3a1c3e-3y33-0000-0000-000000000002","version":[1,0,0]}]}
manifest["header"]["uuid"]=str(uuid.uuid5(uuid.NAMESPACE_DNS,'3y33-pack-header'))
manifest["modules"][0]["uuid"]=str(uuid.uuid5(uuid.NAMESPACE_DNS,'3y33-pack-module'))
with zipfile.ZipFile(bz,'w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('manifest.json',json.dumps(manifest,indent=2))
    for page,im in sheets.items():
        b=io.BytesIO(); im.save(b,'PNG'); z.writestr('font/glyph_%02X.png'%page,b.getvalue())
json.dump(mapping,open('/home/claude/pack/out/mapping.json','w'))
sheet.save('/home/claude/pack/out/preview.png')
print(len(codes),'banderas', 'paginas bedrock', [hex(p) for p in sheets], hashlib.sha1(open(jzip,'rb').read()).hexdigest(), os.path.getsize(jzip), os.path.getsize(bz))
