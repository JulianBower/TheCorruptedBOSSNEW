import numpy as np
from PIL import Image
from common import *

def hexc(h): h=h.lstrip('#'); return np.array([int(h[i:i+2],16) for i in (0,2,4)],float)
STEEL=hexc('6A707A'); STEEL_D=hexc('4E535C'); STEEL_L=hexc('828994'); VISOR_D=hexc('0F1115'); CAPE_N=hexc('3A3D44')
PLATE_A=hexc('36393F'); PLATE_AL=hexc('4A4E56'); PLATE_AD=hexc('26282D'); OBS=hexc('161418'); OBS_L=hexc('2B252D'); OBS_N=hexc('1E1A20')
RED=hexc('D91E18'); RED_B=hexc('FF3B30'); RED_D=hexc('7A0F0C')

def hash3(ix,iy,iz,seed=0):
    h=(ix*374761393+iy*668265263+iz*2147483647+seed*1274126177)&0xFFFFFFFF
    h=((h^(h>>13))*1274126177)&0xFFFFFFFF
    h=h^(h>>16)
    return (h&0xFFFFFF)/float(0x1000000)
def vnoise(p,scale,seed=0):
    x,y,z=p[0]*scale,p[1]*scale,p[2]*scale
    ix,iy,iz=int(np.floor(x)),int(np.floor(y)),int(np.floor(z)); fx,fy,fz=x-ix,y-iy,z-iz
    sx,sy,sz=[t*t*(3-2*t) for t in (fx,fy,fz)]
    v=0
    for dx in (0,1):
        for dy in (0,1):
            for dz in (0,1):
                w=(sx if dx else 1-sx)*(sy if dy else 1-sy)*(sz if dz else 1-sz)
                v+=w*hash3(ix+dx,iy+dy,iz+dz,seed)
    return v
def voronoi_edge(p,cell,seed=0):
    x,y,z=p[0]/cell,p[1]/cell,p[2]/cell
    ix,iy,iz=int(np.floor(x)),int(np.floor(y)),int(np.floor(z))
    best=[9e9,9e9]
    for dx in (-1,0,1):
        for dy in (-1,0,1):
            for dz in (-1,0,1):
                cx,cy,cz=ix+dx,iy+dy,iz+dz
                fx=cx+hash3(cx,cy,cz,seed+1); fy=cy+hash3(cx,cy,cz,seed+2); fz=cz+hash3(cx,cy,cz,seed+3)
                d=((x-fx)**2+(y-fy)**2+(z-fz)**2)**0.5
                if d<best[0]: best[1]=best[0]; best[0]=d
                elif d<best[1]: best[1]=d
    return (best[1]-best[0])*cell   # distance-ish to the nearest crack line, in model units

def in_visor(pb,nbb):
    """T-shaped visor slit, in blockbench coords, only on the faces that look forward (-x)."""
    if nbb[0]>-0.35 or pb[0]>-1.2: return 0
    dz=abs(pb[2]-0.4)
    bar = 9.55<=pb[1]<=10.2 and dz<=1.15
    stem= 7.95<=pb[1]<=10.2 and dz<=0.27
    if bar or stem:
        core=(9.7<=pb[1]<=10.05 and dz<=0.95) or (8.1<=pb[1]<=10.05 and dz<=0.13)
        return 2 if core else 1
    return 0

def shade(c,k): return np.clip(c*k,0,255)

# under-layer cubes of CorruptedPlayerArmorModel: head 9x9x9, body 9x12x5, arm 4.8x12.8x4.8, leg 4.6x12x4.6, boot 4.8x4x4.8
PATCHES=[(320,0,36,18),(320,20,28,17),(320,40,20,18),(320,60,19,17),(320,80,20,9)]
OLD_PATCHES=[(320,0,32,16),(320,16,32,16),(320,32,24,16),(320,48,16,16),(320,64,16,8),(352,48,16,16),(352,64,16,8)]
def patches(img,glow,variant):
    """Plain plate patches for the real armor cubes (unused corner of the 512x512 atlas)."""
    for (x0,y0,w,h) in PATCHES:
        for y in range(y0,y0+h):
            for x in range(x0,x0+w):
                n=(hash3(x,y,5,3)-0.5)*10
                shade=1.1-0.25*((y-y0)/h)
                if variant=='normal':
                    c=STEEL*shade+n
                    if x%8==0 or y%8==0: c=c*0.9
                    if (x+y*3)%53==0: c=STEEL_L
                else:
                    obs=hash3(x//4,y//4,7,9)<0.5
                    c=(OBS if obs else PLATE_A)*shade+n*0.6
                    if x%8==0 or y%8==0: c=c*0.85
                    if obs and hash3(x,y,11,2)<0.1:
                        c=RED; glow[y,x]=(*RED.astype(np.uint8),255)
                img[y,x]=(*np.clip(c,0,255).astype(np.uint8),255)

def paint(variant):
    """variant: 'normal' | 'awakened'. returns (color RGBA image, glow RGBA image)"""
    img=np.zeros((TEX,TEX,4),np.uint8); glow=np.zeros((TEX,TEX,4),np.uint8)
    quads=bake()
    for q in quads:
        u1,v1,u2,v2=q['uv']; bb=q['bb']; TL,TR,BR,BL=bb
        x0,x1=sorted((int(round(u1)),int(round(u2)))); y0,y1=sorted((int(round(v1)),int(round(v2))))
        w=max(1,x1-x0); h=max(1,y1-y0)
        is_cape = q['bone']=='TORSO' and abs(q['size'][0]-0.8)<0.01 and abs(q['size'][1]-1.6)<0.01 and abs(q['size'][2]-2.8)<0.01
        small = max(q['size'])<=1.0 or q['bone'] in ('L_LEG','R_LEG') and max(q['size'])<1.0
        nbb=q['nbb']
        for py in range(y0,y1):
            for px in range(x0,x1):
                fu=((px+0.5)-u1)/(u2-u1) if u2!=u1 else 0.5
                fv=((py+0.5)-v1)/(v2-v1) if v2!=v1 else 0.5
                pb=TL+fu*(TR-TL)+fv*(BL-TL)
                ex=min(px-x0,x1-1-px); ey=min(py-y0,y1-1-py)
                edge_tl=(px==x0 or py==y0); edge_br=(px==x1-1 or py==y1-1)
                rng=hash3(px*7+3,py*13+5,q['ci']*3+1,9)
                grain=(rng-0.5)*10
                vis=in_visor(pb,nbb)
                g=None; alpha=255
                if variant=='normal':
                    base=STEEL_D if (small or q['face']=='down') else STEEL
                    if q['face']=='up': base=base*1.07
                    if is_cape: base=CAPE_N*(0.9+0.2*vnoise(pb,3.0))
                    c=base+grain
                    if not is_cape:
                        if edge_tl: c=STEEL_L+grain*0.5
                        elif edge_br: c=STEEL_D*0.95
                    else:
                        if int(pb[2]*5)%2==0: c=c*0.9
                    if vis: c=VISOR_D+grain*0.3
                else:
                    wob=0.35*(vnoise(pb,1.7,4)-0.5)*2
                    right = (pb[2]+wob)<0.4
                    # cube sides that are fully on one side
                    if q['bone'] in ('R_ARM','R_LEG'): right=True
                    if q['bone'] in ('L_ARM','L_LEG'): right=False
                    edge=voronoi_edge(pb,1.15,5)
                    edge2=voronoi_edge(pb,0.6,17)
                    wv=0.055+0.05*vnoise(pb,2.3,8)
                    near_boundary=abs(pb[2]+wob-0.4)<0.45
                    vein_lvl=0
                    if right:
                        if edge<wv: vein_lvl=2 if edge<wv*0.45 else 1
                        elif edge2<0.03 and vnoise(pb,2.0,21)>0.5: vein_lvl=1
                    else:
                        if near_boundary and edge<wv*0.8 and vnoise(pb,2.5,31)>0.45: vein_lvl=1
                    if right:
                        base=OBS+(OBS_N-OBS)*vnoise(pb,3.1,3)
                        c=base+grain*0.4
                        if edge_tl: c=OBS_L
                        elif edge_br: c=OBS*0.8
                    else:
                        base=PLATE_AD if (small or q['face']=='down') else PLATE_A
                        c=base+grain*0.6
                        if edge_tl: c=PLATE_AL+grain*0.4
                        elif edge_br: c=PLATE_AD*0.9
                    if is_cape:
                        c=hexc('2A2C32')*(0.9+0.25*vnoise(pb,3.0))+grain*0.4
                        if int(pb[2]*5)%2==0: c=c*0.88
                        jag=2.52+0.32+0.3*vnoise(pb,5.0,44)+0.2*np.sin(pb[2]*9.0)*0.5
                        if pb[1]<jag-0.38: alpha=0
                        elif pb[1]<jag+0.1: c=RED_B; g=RED_B
                        elif pb[1]<jag+0.42: t=(jag+0.42-pb[1])/0.32; c=c*(1-t)+RED*t; g=RED*t
                        vein_lvl=0
                    if vein_lvl==2: c=RED_B; g=RED_B
                    elif vein_lvl==1: c=RED; g=RED
                    if vis==2: c=hexc('FF5A4A'); g=hexc('FF5A4A')
                    elif vis==1: c=RED_B; g=RED_B
                    elif is_cape is False and False: pass
                if g is None and alpha and not vis:
                    k={'up':1.14,'down':0.66,'north':0.96,'south':0.84,'east':1.0,'west':0.9}[q['face']]
                    k*=1.10-0.26*min(max(fv,0),1)              # light from above: top of each plate brighter
                    k*=0.80+0.025*min(max(pb[1],0),8)            # lower body sits in shadow
                    k*=0.96+0.08*hash3(py,q['ci'],q['face'].__len__(),1)   # brushed horizontal grain
                    if min(ex,ey)==1 and (px>=x1-2 or py>=y1-2): k*=0.88       # contact shadow along bottom/right
                    if min(ex,ey)==1 and (px<=x0+1 or py<=y0+1): k*=1.07       # bevel light on top/left
                    if w>=10 and h>=10 and px-x0 in (2,w-3) and py-y0 in (2,h-3): k*=1.45   # rivets
                    elif w>=10 and h>=10 and px-x0 in (3,w-2) and py-y0 in (3,h-2): k*=0.72 # rivet shadow
                    if vnoise(pb,2.2,60)>0.72: k*=0.84                           # grime
                    if hash3(px,py,q['ci'],77)<0.012: k*=1.35                     # scratches
                    c=c*k
                    if variant=='awakened' and right and vnoise(pb,4.0,70)>0.74: c=c+np.array([16,10,18])   # glassy obsidian facets
                    if variant=='awakened' and right and vnoise(pb,3.0,80)>0.78: c=c+np.array([30,4,4])     # faint red subsurface glow
                img[py,px]=(*np.clip(c,0,255).astype(np.uint8),alpha)
                if g is not None and alpha: glow[py,px]=(*np.clip(g,0,255).astype(np.uint8),255)
    patches(img,glow,variant)
    return Image.fromarray(img,'RGBA'),Image.fromarray(glow,'RGBA')
if __name__=='__main__':
    import os
    os.makedirs('out',exist_ok=True)
    n,_=paint('normal'); n.save('out/the_corrupted_normal.png')
    a,g=paint('awakened'); a.save('out/the_corrupted_awakened.png'); g.save('out/the_corrupted_glow.png')
    print('ok')
