import random, unicodedata, sys
from spylls.hunspell import Dictionary
S, seme, da, a, uscita = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), sys.argv[5]
random.seed(seme)
righe=["qwertyuiop","asdfghjkl","zxcvbnm"]; pos={}
for r,row in enumerate(righe):
    for i,c in enumerate(row): pos[c]=(r,i+{0:0,1:0.5,2:1.0}[r])
def vicini(c):
    if c not in pos: return []
    r,x=pos[c]; return [d for d,(r2,x2) in pos.items() if d!=c and abs(r2-r)<=1 and abs(x2-x)<=1.0 and not (r2!=r and abs(x2-x)>0.75)]
def norm(s): return ''.join(ch for ch in unicodedata.normalize('NFD',s) if unicodedata.category(ch)!='Mn')
nuovo=[l.split('\t')[0] for l in open(f'{S}/mykeyboard/TastieraPersonale/app/src/main/assets/parole_it.txt') if not l.startswith(('#','-'))]
noti=set(w.lower() for w in nuovo)
noti_vecchio=set(l.split()[0] for l in open(f'{S}/dati/freq_it.txt').readlines()[:60000])
cand=[w for w in nuovo[:6000] if w.isalpha() and w.islower() and len(w)>=4 and w==norm(w)]
def refuso(w):
    t=random.random(); i=random.randrange(len(w)); doppie=[k for k in range(1,len(w)) if w[k]==w[k-1]]
    if t<0.30:
        v=vicini(w[i]); return w[:i]+random.choice(v)+w[i+1:] if v else None
    if t<0.50:
        if i==len(w)-1: i-=1
        return None if w[i]==w[i+1] else w[:i]+w[i+1]+w[i]+w[i+2:]
    if t<0.65:
        v=vicini(w[i]); return w[:i+1]+random.choice(v)+w[i+1:] if v else None
    if t<0.85 and doppie:
        k=random.choice(doppie); return w[:k]+w[k+1:]
    return w[:i]+w[i+1:] if 0<i<len(w)-1 else None
casi=[]
while len(casi)<3000:
    w=random.choice(cand); r=refuso(w)
    if r and r not in noti and r not in noti_vecchio: casi.append((r,w))
open(f'{uscita}/refusi_it.tsv','w').write(''.join(f'{x}\t{y}\n' for x,y in casi))
hun=Dictionary.from_files(f'{S}/dati/hun_it/index'); rare=[]
for l in open(f'{S}/dati/freq_it.txt').readlines()[da:a]:
    w=l.split()[0]
    if w.isalpha() and len(w)>=4 and w not in noti and w not in noti_vecchio and hun.lookup(w): rare.append(w)
    if len(rare)>=3000: break
open(f'{uscita}/rare_it.txt','w').write('\n'.join(rare)+'\n')
print(len(casi),len(rare))
