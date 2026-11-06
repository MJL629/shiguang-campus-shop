"""AI-assisted catalog import. Public prototype data and real book metadata retain distinct provenance."""
from pathlib import Path
import concurrent.futures, datetime, json, time, urllib.request, urllib.parse

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'data'
PREVIEW = ROOT / '.local/import-preview'
IMAGES = PREVIEW / 'images'
DATA.mkdir(exist_ok=True); IMAGES.mkdir(parents=True, exist_ok=True)
HEADERS = {'User-Agent': 'ShiguangCourseShop/0.1 educational catalog prototype'}

def fetch(url, path):
    if path.exists(): return path.read_bytes()
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30) as response:
                content = response.read()
            path.write_bytes(content); return content
        except Exception:
            if attempt == 3: raise
            time.sleep(2 + attempt)

def sql(value):
    if value is None: return 'NULL'
    if isinstance(value, (int, float)): return str(value)
    return "'" + str(value).replace('\\', '\\\\').replace("'", "''") + "'"

def main():
    raw = json.loads(fetch('https://dummyjson.com/products?limit=0', DATA / 'dummyjson-original.json'))
    wanted = {'home-decoration': '居家好物', 'kitchen-accessories': '居家好物',
              'mobile-accessories': '数码配件', 'laptops': '数码好物', 'tablets': '数码好物',
              'sports-accessories': '运动随行', 'womens-bags': '日常包袋'}
    selected = [p for p in raw['products'] if p['category'] in wanted][:75]
    catalog = []
    for p in selected:
        extension = '.webp' if '.webp' in p['thumbnail'] else '.png'
        image = 'dummy-' + str(p['id']) + extension
        catalog.append({'id': 1000 + p['id'], 'name': p['title'], 'category': wanted[p['category']],
                        'price': round(p['price'] * 7, 2), 'stock': 20 + p['id'] % 80,
                        'imageUrl': '/images/catalog/' + image, 'tagline': p.get('brand') or '日常精选',
                        'description': '来自公开电商原型目录的' + wanted[p['category']] + '。' +
                         '名称与图片保留来源，本站人民币价格、库存与交易均为课程演示数据，不代表品牌或零售商的销售承诺。',
                        'brand': p.get('brand', ''), 'sourceType': 'DUMMYJSON',
                        'sourceUrl': 'https://dummyjson.com/products/' + str(p['id']),
                        'sourceImageUrl': p['thumbnail'], 'localFile': image})
    def image(item):
        try:
            fetch(item['sourceImageUrl'], IMAGES / item['localFile'])
            return True
        except Exception as exc:
            print('Image failed:', item['id'], type(exc).__name__, flush=True)
            return False
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        flags = list(pool.map(image, catalog))
    catalog = [p for p, ok in zip(catalog, flags) if ok]
    # One limited lookup per subject for a learning project; cache responses and do not crawl covers.
    for subject, category in [('classics', '文学经典'), ('design', '设计艺术'), ('science', '科学阅读')]:
        query = urllib.parse.urlencode({'q': 'subject:' + subject, 'limit': 12,
                                       'fields': 'key,title,author_name,cover_i,first_publish_year'})
        try:
            books = json.loads(fetch('https://openlibrary.org/search.json?' + query, DATA / ('openlibrary-' + subject + '.json')))['docs']
            for b in books[:10]:
                if not b.get('cover_i') or not b.get('author_name'): continue
                if any(p['sourceUrl'] == 'https://openlibrary.org' + b['key'] for p in catalog): continue
                index = len(catalog)
                catalog.append({'id': 3000 + index, 'name': b['title'][:100], 'category': category,
                                'price': round(25 + index * 7 % 65 + .8, 2), 'stock': 15 + index % 50,
                                'imageUrl': 'https://covers.openlibrary.org/b/id/' + str(b['cover_i']) + '-M.jpg',
                                'tagline': ', '.join(b['author_name'])[:100],
                                'description': '真实书目资料来自 Open Library。作者：' + ', '.join(b['author_name']) +
                                  '。首次出版年份：' + str(b.get('first_publish_year', '未知')) +
                                  '。封面按来源服务建议直接引用；本站价格、库存为课程演示数据，不对应实际销售版本。',
                                'brand': ', '.join(b['author_name'])[:100], 'sourceType': 'OPEN_LIBRARY',
                                'sourceUrl': 'https://openlibrary.org' + b['key']})
        except Exception as exc: print('Book lookup failed:', subject, type(exc).__name__, flush=True)
        time.sleep(1.1)
    (PREVIEW / 'catalog.json').write_text(json.dumps({'importedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'products': catalog}, ensure_ascii=False, indent=2), encoding='utf-8')
    columns = ['id','name','category','price','stock','image_url','tagline','description','brand','source_type','source_url']
    keys = ['id','name','category','price','stock','imageUrl','tagline','description','brand','sourceType','sourceUrl']
    lines = ['-- Public prototype merchandise and real bibliographic records. Prices/stocks are fictional.',
             '-- Imported via scripts/import_catalog.py; see data/catalog.json for per-product provenance.']
    lines += ['INSERT INTO product (' + ','.join(columns) + ') VALUES (' + ','.join(sql(p.get(k)) for k in keys) + ');' for p in catalog]
    (PREVIEW / 'migration-preview.sql').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print('Prepared review-only catalog preview:', len(catalog), 'products. Existing data and migrations were not overwritten.', flush=True)

if __name__ == '__main__': main()
