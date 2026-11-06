# coding: utf-8
"""AI-assisted real HTTP/MySQL smoke test. Uses fictional test identities, never external email delivery."""
from pathlib import Path
import http.cookiejar, json, time, urllib.error, urllib.request, uuid
ROOT=Path(__file__).resolve().parents[1]
BASE='http://127.0.0.1:18080/api'
checks=[]
class Client:
    def __init__(self):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.token=''
        self.session()
    def call(self,path,method='GET',body=None,status=200,headers=None,csrf=True):
        args={'method':method,'headers':{'Content-Type':'application/json',**(headers or {})}}
        if method!='GET' and csrf:args['headers']['X-XSRF-TOKEN']=self.token
        if body is not None:args['data']=json.dumps(body).encode()
        try:
            response=self.opener.open(urllib.request.Request(BASE+path,**args),timeout=15);code=response.status;raw=response.read()
        except urllib.error.HTTPError as error:code=error.code;raw=error.read()
        assert code==status,(path,method,code,status,raw[:300])
        return json.loads(raw) if raw else None
    def session(self):
        data=self.call('/auth/session');self.token=data['csrfToken'];return data
    def login(self,username,password):
        self.call('/auth/login','POST',{'username':username,'password':password});self.session()
def check(name):checks.append(name);print('PASS',name,flush=True)

def main():
    config=json.loads((ROOT/'.local/runtime.json').read_text())
    customer=Client();name='qa_'+uuid.uuid4().hex[:12]
    customer.call('/cart',status=401);check('anonymous cart rejected')
    customer.call('/auth/register','POST',{'username':name,'displayName':'QA Customer','email':name+'@example.test','password':'Qa-Only-Password-123'},status=403,csrf=False);check('CSRF required')
    customer.call('/auth/register','POST',{'username':name,'displayName':'QA Customer','email':name+'@example.test','password':'Qa-Only-Password-123'},status=201);customer.session();check('registration and session')
    customer.call('/admin/stats',status=403);check('admin role enforced')
    data=customer.call('/products?size=1');assert data['total']>=100 and len(data['items'])==1;check('rich catalog and pagination')
    assert customer.call('/products?keyword=%25')['total']==0;check('search wildcard escaping')
    before=customer.call('/products/1')['stock']
    customer.call('/cart','POST',{'productId':1,'quantity':2});cart=customer.call('/cart');assert cart['totalAmount']==56;check('cart amount from database')
    key=str(uuid.uuid4());address={'recipientName':'QA Customer','recipientEmail':name+'@example.test','recipientPhone':'13800000000','shippingAddress':'Fictional campus test address','totalAmount':0}
    order=customer.call('/orders','POST',address,headers={'Idempotency-Key':key});assert order['totalAmount']==56;check('client amount cannot change server amount')
    duplicate=customer.call('/orders','POST',address,headers={'Idempotency-Key':key});assert duplicate['id']==order['id'];check('checkout idempotency')
    assert customer.call('/products/1')['stock']==before-2;check('stock reserved once')
    stranger=Client();stranger.login('demo',config['customerPassword']);stranger.call('/orders/'+str(order['id']),status=404);check('cross-user order access rejected')
    customer.call('/orders/'+str(order['id'])+'/pay','POST');customer.call('/orders/'+str(order['id'])+'/pay','POST');check('repeated demo payment safe')
    admin=Client();admin.login('admin',config['adminPassword']);stats=admin.call('/admin/stats');assert float(stats['summary']['revenue'])>=56;check('sales summary includes paid order')
    admin.call('/admin/orders/'+str(order['id'])+'/ship','POST',{'trackingNumber':'DEMO-QA-'+str(order['id'])});check('admin shipment')
    for attempt in range(55):
        messages=admin.call('/admin/mail');mail=next((m for m in messages if m['order_id']==order['id']),None)
        if mail and mail['status']=='CAPTURED':break
        time.sleep(1)
    assert mail and mail['status']=='CAPTURED',mail;check('SMTP message captured locally')
    assert list((ROOT/'.local/mailbox').glob('*.eml'));check('actual email artifact exists')
    customer.call('/orders/'+str(order['id'])+'/complete','POST');assert customer.call('/orders/'+str(order['id']))['status']=='COMPLETED';check('order history and receipt confirmation')
    customer.call('/cart','POST',{'productId':1,'quantity':1});cancel=customer.call('/orders','POST',address,headers={'Idempotency-Key':str(uuid.uuid4())})
    customer.call('/orders/'+str(cancel['id'])+'/cancel','POST');customer.call('/orders/'+str(cancel['id'])+'/cancel','POST');assert customer.call('/products/1')['stock']==before-2;check('cancellation restores stock exactly once')
    customer.call('/auth/logout','POST',status=204);customer.session();customer.call('/cart',status=401);check('logout invalidates session')
    report={'checks':checks,'passed':len(checks),'baseUrl':BASE,'completedAt':time.strftime('%Y-%m-%d %H:%M:%S'),'mailMode':'LOCAL_CAPTURE','testUser':name,'testOrderId':order['id']}
    (ROOT/'.local/smoke-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print('All',len(checks),'HTTP/MySQL checks passed.',flush=True)
if __name__=='__main__':main()
