# coding: utf-8
"""AI-assisted original vector study covers. These are not publishers' product photographs."""
from pathlib import Path
import html,json,textwrap
ROOT=Path(__file__).resolve().parents[1]
data=json.loads((ROOT/'data/catalog.json').read_text(encoding='utf-8'))
assets=ROOT/'frontend/public/images/books';assets.mkdir(parents=True,exist_ok=True)
palettes=[('#334e44','#e2d5a6','#e8e7dc'),('#a56548','#f1dbc0','#ece3d6'),('#46566a','#d8d3ae','#e2e4e0'),('#7e8052','#f0e2b5','#e7e6d9'),('#6a4c49','#e7cda8','#e9e0d9'),('#566b66','#e0d8b7','#e1e7dd')]
lines=['-- Original local study covers, not publishers\' actual book covers. Source metadata links remain unchanged.']
books=[p for p in data['products'] if p['sourceType']=='OPEN_LIBRARY']
for index,p in enumerate(books):
 title=p.get('displayName',p['name']);author=p.get('brand','')
 if len(author)>28:author=author.split(',')[0]+' et al.'
 chunks=[];current='';weight=0
 for ch in title:
  cost=1 if ord(ch)>127 else .55
  if weight+cost>8.7:chunks.append(current);current='';weight=0
  current+=ch;weight+=cost
 if current:chunks.append(current)
 chunks=chunks[:4]
 fill,ink,bg=palettes[index%len(palettes)]
 text=''.join('<text x="216" y="'+str(181+i*35)+'" fill="'+ink+'" font-size="'+('27' if len(chunks)<4 else '24')+'" font-family="Microsoft YaHei,serif">'+html.escape(chunk)+'</text>' for i,chunk in enumerate(chunks))
 if index%3==0:motif=f'<circle cx="329" cy="303" r="36" stroke="{ink}" fill="none"/><path d="M277 303H381M329 252V354" stroke="{ink}"/><ellipse cx="329" cy="303" rx="20" ry="36" stroke="{ink}" fill="none"/>'
 elif index%3==1:motif=f'<path d="M250 337Q290 264 365 286M294 321Q267 279 277 272Q304 277 294 321M327 302Q334 264 352 268Q365 287 327 302" fill="none" stroke="{ink}" stroke-width="1.5"/>'
 else:motif=f'<path d="M258 333L282 291L306 333L330 291L354 333ZM258 347H354" fill="none" stroke="{ink}" stroke-width="1.5"/>'
 if len(chunks)>=3:motif=''
 svg=f'''<!-- AI-assisted original illustrative book cover by Codex. Not the publisher's cover. -->
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 480"><rect width="600" height="480" fill="{bg}"/><ellipse cx="303" cy="418" rx="137" ry="13" fill="#26362b" opacity=".13"/><g transform="rotate(-5 300 240)"><rect x="190" y="63" width="239" height="350" rx="4" fill="#b6b29b"/><rect x="178" y="55" width="239" height="350" rx="4" fill="{fill}"/><path d="M196 55V405" stroke="#132b25" stroke-opacity=".32" stroke-width="4"/><path d="M214 84H384M214 374H384" stroke="{ink}" stroke-opacity=".6"/><text x="215" y="110" font-size="9" fill="{ink}" font-family="sans-serif" letter-spacing="2">SHIGUANG READING</text>{text}<text x="217" y="{190+35*len(chunks)}" font-size="10" fill="{ink}" font-family="Microsoft YaHei,sans-serif">{html.escape(author)}</text>{motif}<text x="217" y="391" font-size="7" letter-spacing="1.5" fill="{ink}" font-family="sans-serif">ORIGINAL STUDY COVER / {index+1:02d}</text></g><text x="468" y="443" font-size="9" fill="#858a76" font-family="Microsoft YaHei,sans-serif">书目示意封面</text></svg>'''
 path=assets/(str(p['id'])+'.svg');path.write_text(svg,encoding='utf-8')
 p['sourceImageUrl']=p.get('sourceImageUrl',p['imageUrl']);p['imageUrl']='/images/books/'+str(p['id'])+'.svg';p['imageNote']='原创书目示意封面，非出版社实物图片'
 lines.append("UPDATE product SET image_url='"+p['imageUrl']+"' WHERE id="+str(p['id'])+';')
migration=ROOT/'backend/src/main/resources/db/migration/V10__local_original_book_covers.sql'
sql_text='\n'.join(lines)+'\n'
if migration.exists() and migration.read_text(encoding='utf-8')!=sql_text:
    (ROOT/'.local/book-cover-update-preview.sql').write_text(sql_text,encoding='utf-8')
    print('New SQL was saved as a preview. Create a new Flyway migration instead of editing V10.')
elif not migration.exists():migration.write_text(sql_text,encoding='utf-8')
(ROOT/'data/catalog.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
print('Generated',len(books),'original study covers, preserving original image URLs in the source manifest.')
