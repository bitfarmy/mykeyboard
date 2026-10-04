# Il correttore della versione 0.1, riprodotto fedelmente (Dizionario.kt del commit 4181564).
import unicodedata, sys
S, cartella = sys.argv[1], sys.argv[2]
def norm(s): return ''.join(ch for ch in unicodedata.normalize('NFD',s.lower().replace('’',"'")) if unicodedata.category(ch)!='Mn')
def eparola(s): return any(c.isalpha() for c in s) and all(c.isalpha() or c in "'’" for c in s)
parole=[t.lower() for l in open(f'{S}/dati/freq_it.txt').readlines()[:60000] for t in l.strip().split() if eparola(t)]
n=len(parole); base={}
for i,p in enumerate(parole): base.setdefault(p,1+1000*(n-i)//n)
togli=set(); extra=[]
for l in open(f'{S}/banco/vecchio_extra_it.txt'):
    if l.startswith('#'): continue
    for t in l.split():
        w=t.lstrip('-').lower()
        if eparola(w): (togli.add(w) if t.startswith('-') else extra.append(w))
for w in togli: base.pop(w,None)
m=len(extra)
for j,p in enumerate(extra): base[p]=max(base.get(p,0),400+600*(m-j)//max(m,1))
indice={}; conap={}
for w in base:
    indice.setdefault(norm(w),[]).append(w)
    if "'" in w: conap.setdefault(norm(w).replace("'",""),[]).append(w)
voci=[(norm(w),w) for w in base]
def dist1(a,b):
    if a==b: return False
    if len(a)==len(b):
        i=0
        while i<len(a) and a[i]==b[i]: i+=1
        return (i+1<len(a) and a[i]==b[i+1] and a[i+1]==b[i] and a[i+2:]==b[i+2:]) or a[i+1:]==b[i+1:]
    c,l=(a,b) if len(a)<len(b) else (b,a)
    if len(l)-len(c)!=1: return False
    i=0
    while i<len(c) and c[i]==l[i]: i+=1
    return c[i:]==l[i+1:]
def correggi(s):
    w=s.lower()
    if len(w)<2 or not eparola(w) or w in base: return None
    k=norm(w); cand=[p for p in indice.get(k,[]) if p!=w]+conap.get(k.replace("'",""),[])
    if cand: return max(cand,key=lambda p:base[p])
    if "'" in k or len(base)<6000 or len(k)<4: return None
    best=None;pt=-1
    for vk,vw in voci:
        if abs(len(vk)-len(k))<=1 and "'" not in vk and dist1(k,vk) and base[vw]>pt: best,pt=vw,base[vw]
    return best
casi=[l.rstrip('\n').split('\t') for l in open(f'{cartella}/refusi_it.tsv')]
ok=sb=0
for t,e in casi:
    r=correggi(t); ok+= r==e; sb+= r is not None and r!=e
rare=[l.strip() for l in open(f'{cartella}/rare_it.txt') if l.strip()]
fp=sum(1 for w in rare if correggi(w) is not None); N=len(casi)
print(f"VECCHIA  refusi: corretti {ok/N:.1%}  sbagliati {sb/N:.1%}  lasciati {(N-ok-sb)/N:.1%}   parole rare giuste rovinate: {fp/len(rare):.1%}")
