#!/usr/bin/env python3
"""Prepare licensed Noto Sans faces as generated Android assets, not source uploads.

The Ubuntu/Debian fonts-noto-core package supplies the upstream font and OFL
notice. Use stdlib checks so the CI does not need another Python dependency.
"""
from pathlib import Path
import hashlib, json, shutil, struct, sys

ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path('/usr/share/fonts/truetype/noto')
LICENSE=Path('/usr/share/doc/fonts-noto-core/copyright')
OUT=ROOT/'app/build/generated/htq-font/assets/htq-font'
# Explicit Vietnamese alphabet plus precomposed tone marks and combining marks.
REQUIRED=set(map(ord,'ĂăÂâĐđÊêÔôƠơƯưÀàÁáÃãÈèÉéÌìÍíĨĩÒòÓóÕõÙùÚúŨũÝý'))|set(range(0x1ea0,0x1efa))|{0x300,0x301,0x303,0x309,0x323,0x302,0x306,0x31b}|set(range(32,127))

def u16(b,i):return struct.unpack_from('>H',b,i)[0]
def u32(b,i):return struct.unpack_from('>I',b,i)[0]
def tables(b):
    return {b[i:i+4].decode('ascii'):b[u32(b,i+8):u32(b,i+8)+u32(b,i+12)] for i in range(12,12+16*u16(b,4),16)}
def names(b):
    result={}
    for i in range(6,6+12*u16(b,2),12):
        platform,encoding,language,name_id,length,offset=struct.unpack_from('>6H',b,i)
        if platform in (0,3):
            value=b[u16(b,4)+offset:u16(b,4)+offset+length].decode('utf-16-be')
            result.setdefault(name_id,set()).add(value)
    return result

def has_glyph(cmap,cp):
    for i in range(4,4+8*u16(cmap,2),8):
        platform,enc,off=struct.unpack_from('>HHI',cmap,i)
        if platform!=0 and not(platform==3 and enc in (1,10)):continue
        b=cmap[off:]; fmt=u16(b,0)
        if fmt==12:
            for j in range(16,16+12*u32(b,12),12):
                first,last,glyph=struct.unpack_from('>III',b,j)
                if first<=cp<=last and glyph+cp-first!=0:return True
        elif fmt==4 and cp<=65535:
            n=u16(b,6)//2; start=16+2*n; delta=start+2*n; rng=delta+2*n
            for j in range(n):
                if u16(b,start+2*j)<=cp<=u16(b,14+2*j):
                    d=u16(b,delta+2*j); o=u16(b,rng+2*j)
                    if o==0:return (cp+d)%65536!=0
                    idx=rng+2*j+o+2*(cp-u16(b,start+2*j))
                    if idx+2>len(b):continue
                    g=u16(b,idx)
                    if g!=0 and (g+d)%65536!=0:return True
    return False

def main():
    notice=LICENSE.read_text(encoding='utf-8');assert 'OFL-1.1' in notice and 'SIL Open Font License' in notice
    records=[]
    OUT.mkdir(parents=True,exist_ok=True)
    for name,weight in [('Regular',400),('Bold',700)]:
        src=SOURCE/f'NotoSans-{name}.ttf'; data=src.read_bytes(); t=tables(data); n=names(t['name'])
        assert 'Noto Sans' in n.get(1,set()),n.get(1)
        assert u16(t['OS/2'],4)==weight
        assert any('SIL Open Font License' in s for s in n.get(13,set())),n.get(13)
        missing=[hex(cp) for cp in sorted(REQUIRED) if not has_glyph(t['cmap'],cp)];assert not missing,missing
        shutil.copyfile(src,OUT/src.name)
        records.append({'face':src.name,'weight':weight,'versions':sorted(n.get(5,set())),'sha256':hashlib.sha256(data).hexdigest(),'required_glyphs':len(REQUIRED)})
    shutil.copyfile(LICENSE,OUT/'LICENSE-NotoSans.txt')
    (OUT/'font-manifest.json').write_text(json.dumps(records,indent=2)+'\n')
    print('HTQ_NOTO_FONT_PASS',json.dumps(records,ensure_ascii=False))

if __name__=='__main__':main()
