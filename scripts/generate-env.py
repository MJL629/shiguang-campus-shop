# coding: utf-8
"""AI-assisted deployment config generator. Creates independent random passwords and never prints them."""
from pathlib import Path
import argparse,secrets
parser=argparse.ArgumentParser();parser.add_argument('--output',default=str(Path(__file__).resolve().parents[1]/'.env'));parser.add_argument('--showcase',action='store_true')
args=parser.parse_args();path=Path(args.output)
if path.exists():raise SystemExit('Configuration already exists; it was not overwritten.')
values={'SHOP_DOMAIN':'shop.your-domain.example','DB_PASSWORD':secrets.token_urlsafe(32),'MYSQL_ROOT_PASSWORD':secrets.token_urlsafe(32),'DEMO_ADMIN_PASSWORD':secrets.token_urlsafe(18),'DEMO_CUSTOMER_PASSWORD':secrets.token_urlsafe(18),'DEMO_SEED_ORDERS':str(args.showcase).lower(),'MAIL_HOST':'','MAIL_PORT':'587','MAIL_USERNAME':'','MAIL_PASSWORD':'','MAIL_FROM':''}
path.parent.mkdir(parents=True,exist_ok=True)
with path.open('x',encoding='utf-8') as output:output.write('# Local deployment configuration. Never commit this file.\n'+'\n'.join(key+'='+value for key,value in values.items())+'\n')
print('Created deployment configuration. Set the real domain and SMTP values before starting Compose.')
