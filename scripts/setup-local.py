# coding: utf-8
"""Install official portable development tools under .local without changing system settings."""
from pathlib import Path
import base64, concurrent.futures, hashlib, json, tarfile, time, urllib.request, zipfile
ROOT=Path(__file__).resolve().parents[1]/'.local'
ROOT.mkdir(exist_ok=True)
PACKAGES=[
 ('maven','https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip'),
 ('mysql','https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.13-winx64.zip'),
 ('jdk','https://download.visualstudio.microsoft.com/download/pr/c334da97-1c11-44ba-93ac-b056c6ca5ec4/3d94837dbd8905c1e3c3cddf8f7d3531/microsoft-jdk-21.0.13.0.2-windows-x64.zip'),
]
def request(url,headers=None):return urllib.request.urlopen(urllib.request.Request(url,headers=headers or {}),timeout=45)
def download(url,target):
    # Resumable range downloads avoid restarting large official archives after network interruption.
    with request(url,{'Range':'bytes=0-0'}) as response:
        if response.status!=206:
            target.write_bytes(response.read());return
        total=int(response.headers['Content-Range'].split('/')[-1]);response.read()
    parts=ROOT/(target.stem+'-parts');parts.mkdir(exist_ok=True);step=2*1024*1024
    offsets=list(range(0,total,step))
    def part(start):
        end=min(total-1,start+step-1);path=parts/str(start)
        if path.exists() and path.stat().st_size==end-start+1:return
        for attempt in range(5):
            try:
                with request(url,{'Range':f'bytes={start}-{end}'}) as response:data=response.read();assert response.status==206
                assert len(data)==end-start+1;path.write_bytes(data);return
            except Exception:
                if attempt==4:raise
                time.sleep(1+attempt)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:list(pool.map(part,offsets))
    with target.open('wb') as output:
        for offset in offsets:output.write((parts/str(offset)).read_bytes())
def install(item):
    name,url=item
    if (ROOT/name).exists():print(name+' ready',flush=True);return
    print('Downloading '+name,flush=True);archive=ROOT/(name+'.zip');download(url,archive)
    if name=='mysql':assert hashlib.md5(archive.read_bytes()).hexdigest()=='7b94489d5524dea9b27097068bea25a8','MySQL checksum mismatch'
    if name=='maven':
        with request(url+'.sha512') as response:expected=response.read().decode().split()[0]
        assert hashlib.sha512(archive.read_bytes()).hexdigest()==expected,'Maven checksum mismatch'
    with zipfile.ZipFile(archive) as source:
        assert source.testzip() is None,'Archive integrity check failed';source.extractall(ROOT/name)
    print(name+' installed',flush=True)
if __name__=='__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:list(pool.map(install,PACKAGES))
    pnpm=ROOT/'pnpm'
    if not pnpm.exists():
        with request('https://registry.npmjs.org/pnpm/11.25.0') as response:metadata=json.load(response)
        with request(metadata['dist']['tarball']) as response:content=response.read()
        expected=metadata['dist']['integrity'].split('-',1)[1]
        assert base64.b64encode(hashlib.sha512(content).digest()).decode()==expected
        archive=ROOT/'pnpm.tgz';archive.write_bytes(content);pnpm.mkdir()
        with tarfile.open(archive) as source:
            for member in source.getmembers():
                destination=(pnpm/member.name).resolve()
                if not destination.is_relative_to(pnpm.resolve()) or member.issym() or member.islnk():raise RuntimeError('Unsafe package archive path')
            source.extractall(pnpm)
    print('Local tools are ready. Build with: python scripts/local.py build',flush=True)
