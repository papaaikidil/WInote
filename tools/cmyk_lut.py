#!/usr/bin/env python3
"""
Pembuat LUT warna cetak CMYK untuk WInote (aset app/src/main/assets/warna/).

Model: cetak offset kertas coated (primer Lab mendekati ISO 12647-2 / FOGRA39), Neugebauer-Yule-Nielsen (n = 2)
untuk C, M, Y, penambahan titik (TVI) 12% di 50%, tinta K sebagai densitas tambahan dengan kejenuhan, batas
tinta total 300%, GCR sedang. Pemetaan gamut di OKLab (bobot kecerahan 0.35), relatif kolorimetrik dengan
kompensasi titik hitam. Ini perkiraan, bukan profil ICC hasil ukur mesin cetak.

Keluaran (grid 33x33x33, r berubah paling cepat, lalu g, lalu b):
  cmyk-proof.bin  33^3 x 3 byte  sRGB -> sRGB tampilan hasil cetak
  cmyk-sep.bin    33^3 x 4 byte  sRGB -> C, M, Y, K (0..255)

Jalankan: python3 tools/cmyk_lut.py app/src/main/assets/warna
"""
import numpy as np, sys, time
Wd50=np.array([0.9642,1.0,0.8249])
def lab2xyz(L):
    L=np.asarray(L,float); fy=(L[...,0]+16)/116; fx=fy+L[...,1]/500; fz=fy-L[...,2]/200
    f=np.stack([fx,fy,fz],-1)
    return np.where(f>6/29, f**3, 3*(6/29)**2*(f-4/29))*Wd50
P={(0,0,0):(95,0,-2),(1,0,0):(55,-37,-50),(0,1,0):(48,74,-3),(0,0,1):(89,-5,93),
   (0,1,1):(47,68,48),(1,0,1):(50,-65,27),(1,1,0):(24,22,-46),(1,1,1):(23,0,0)}
KL=(16,0,0)
N=2.0; G=0.12; DMAX=2.1; PP=6
WX=lab2xyz(P[(0,0,0)])
PX={k:lab2xyz(v)**(1/N) for k,v in P.items()}
DK=-np.log10(lab2xyz(KL)/WX)
def tvi(v): return np.clip(v+G*4*v*(1-v),0,1)
def fwd(x,k):
    """x: (n,3) cmy, k: (n,) -> XYZ relatif (kertas = putih D50)"""
    c,m,y=[tvi(x[:,i])[:,None] for i in range(3)]
    s=0
    for (bc,bm,by),px in PX.items():
        w=(c if bc else 1-c)*(m if bm else 1-m)*(y if by else 1-y)
        s=s+w*px
    s=s**N
    D=-np.log10(np.maximum(s,1e-7)/WX)
    ke=tvi(k)[:,None]
    Dk=-np.log10((1-ke)+ke*10**(-DK))
    x2=D+Dk
    Ds=x2/(1+(x2/DMAX)**PP)**(1/PP)
    return 10**(-Ds)*Wd50
M=np.array([[0.4360747,0.3850649,0.1430804],[0.2225045,0.7168786,0.0606169],[0.0139322,0.0971045,0.7141733]])
Minv=np.linalg.inv(M)
M1=np.array([[0.4122214708,0.5363325363,0.0514459929],[0.2119034982,0.6806995451,0.1073969566],[0.0883024619,0.2817188376,0.6299787005]])
M2=np.array([[0.2104542553,0.7936177850,-0.0040720468],[1.9779984951,-2.4285922050,0.4505937099],[0.0259040371,0.7827717662,-0.8086757660]])
WL=0.35
def ok(X):
    lms=np.cbrt(np.maximum((X@Minv.T)@M1.T,1e-9)); o=lms@M2.T
    o[:,0]*=WL; return o
def lin(u): return np.where(u<=0.04045,u/12.92,((u+0.055)/1.055)**2.4)
def enc(u): u=np.clip(u,0,1); return np.where(u<=0.0031308,u*12.92,1.055*u**(1/2.4)-0.055)
BK=fwd(np.array([[0.76,0.64,0.59]]),np.array([1.0]))[0,1]
TAC=3.0
def solve(t,k,x0,iters=120,xref=None,mu=0.0,wt=0.0):
    n=len(t); x=x0.copy(); lam=np.full(n,1e-3)
    def r(x):
        parts=[ok(fwd(x,k))-t]
        if xref is not None: parts.append(mu*(x-xref))
        if wt>0: parts.append(wt*np.maximum(0,x.sum(1)+k-TAC)[:,None])
        return np.concatenate(parts,1)
    f=r(x); cost=(f*f).sum(1)
    m=f.shape[1]
    for it in range(iters):
        J=np.zeros((n,m,3)); h=1e-5
        for j in range(3):
            xp=x.copy(); up=xp[:,j]<1-h
            xp[:,j]=np.where(up,xp[:,j]+h,xp[:,j]-h)
            J[:,:,j]=(r(xp)-f)/(xp[:,j]-x[:,j])[:,None]
        A=np.einsum('nij,nik->njk',J,J); g=np.einsum('nij,ni->nj',J,f)
        dA=np.einsum('nii->ni',A)
        Ad=A+ (lam[:,None]*(dA+1e-9))[:,:,None]*np.eye(3)
        dx=-np.linalg.solve(Ad,g[...,None])[...,0]
        xn=np.clip(x+dx,0,1); fn=r(xn); cn=(fn*fn).sum(1)
        better=cn<cost
        x=np.where(better[:,None],xn,x); f=np.where(better[:,None],fn,f); cost=np.where(better,cn,cost)
        lam=np.where(better,np.maximum(lam/3,1e-7),np.minimum(lam*4,1e8))
    e=np.sqrt(((ok(fwd(x,k))-t)**2).sum(1))
    return x,e
def separate(rgb):
    """rgb (n,3) sRGB 0..1 -> cmyk (n,4), proof rgb (n,3)"""
    X=lin(rgb)@M.T
    X=X*(1-BK)+BK*Wd50
    t=ok(X); n=len(t)
    kn=1-rgb.max(1)
    k=np.clip((kn-0.35)/0.65,0,1)**1.3
    den=np.maximum(1-k,1e-3)[:,None]
    xref=np.clip((1-rgb-k[:,None])/den,0,1)*np.where(k[:,None]>0.999,0.6,1)
    x,e=solve(t,k,xref.copy(),iters=150,xref=xref,mu=0.001,wt=1.0)
    cmyk=np.concatenate([x,k[:,None]],1)
    Xp=fwd(x,k)
    Xp=(Xp-BK*Wd50)/(1-BK)
    proof=enc(Xp@Minv.T)
    return cmyk,proof
if __name__=='__main__':
    out=sys.argv[1]; n=33
    g=np.linspace(0,1,n)
    B,Gg,R=np.meshgrid(g,g,g,indexing='ij')
    rgb=np.stack([R.ravel(),Gg.ravel(),B.ravel()],1)
    cm=[];pr=[]
    for i in range(0,len(rgb),4096):
        c,p=separate(rgb[i:i+4096]); cm.append(c); pr.append(p)
    cm=np.concatenate(cm); pr=np.concatenate(pr)
    open(out+'/cmyk-proof.bin','wb').write(np.round(np.clip(pr,0,1)*255).astype(np.uint8).tobytes())
    open(out+'/cmyk-sep.bin','wb').write(np.round(np.clip(cm,0,1)*255).astype(np.uint8).tobytes())
    print('selesai', cm.shape)
