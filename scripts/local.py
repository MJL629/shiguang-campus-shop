"""AI-assisted local launcher. Tools, generated credentials and database stay in .local/."""
from pathlib import Path
import argparse, json, os, secrets, shutil, socket, subprocess, time, urllib.request

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / '.local'

def tools():
    return {
        'java': next((LOCAL / 'jdk').glob('*/bin/java.exe'), Path('C:/Program Files/JetBrains/PyCharm Community Edition 2023.1.4/jbr/bin/java.exe')),
        'maven': next((LOCAL / 'maven').glob('*/bin/mvn.cmd')),
        'mysqld': next((LOCAL / 'mysql').glob('*/bin/mysqld.exe')),
        'mysql': next((LOCAL / 'mysql').glob('*/bin/mysql.exe')),
        'node': Path(shutil.which('node')),
    }

def settings():
    path = LOCAL / 'runtime.json'
    if not path.exists():
        LOCAL.mkdir(exist_ok=True)
        path.write_text(json.dumps({'databasePassword': secrets.token_urlsafe(32),
                                    'rootPassword': secrets.token_urlsafe(32)}, indent=2), encoding='utf-8')
    config = json.loads(path.read_text(encoding='utf-8'))
    for key in ['adminPassword', 'customerPassword']:
        if key not in config: config[key] = secrets.token_urlsafe(16)
    path.write_text(json.dumps(config, indent=2), encoding='utf-8')
    return config

def environment(t, config):
    env = os.environ.copy()
    env['JAVA_HOME'] = str(t['java'].parents[1])
    env['PATH'] = str(t['java'].parent) + os.pathsep + env['PATH']
    env['DB_PASSWORD'] = config['databasePassword']
    env['DB_USERNAME'] = 'shiguang_app'
    env['DB_URL'] = 'jdbc:mysql://127.0.0.1:13306/shiguang_shop?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai'
    env['PORT'] = '18080'
    env['SERVER_ADDRESS'] = '127.0.0.1'
    env['DEMO_ADMIN_PASSWORD'] = config['adminPassword']
    env['DEMO_CUSTOMER_PASSWORD'] = config['customerPassword']
    return env

def run(command, **kwargs):
    subprocess.run([str(x) for x in command], check=True, **kwargs)

def spawn(command, log, env=None):
    with (LOCAL / log).open('ab') as output:
        process = subprocess.Popen([str(x) for x in command], cwd=ROOT, env=env,
                                   stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
    return process

def port_open(port):
    try:
        with socket.create_connection(('127.0.0.1', port), timeout=1): return True
    except OSError: return False

def database(t, config):
    client_config = LOCAL / 'mysql-client.cnf'
    client_config.write_text('[client]\nuser=root\npassword=' + config['rootPassword'] +
                             '\nhost=127.0.0.1\nport=13306\nprotocol=tcp\n', encoding='utf-8')
    if port_open(13306):
        # Authenticate against this project's database before reusing an occupied port.
        run([t['mysql'], '--defaults-file=' + str(client_config), '-N', '-e', 'SELECT 1'], stdout=subprocess.DEVNULL)
        return
    data = LOCAL / 'mysql-data'
    if not data.exists():
        with (LOCAL / 'mysql-initialize.log').open('ab') as log:
            run([t['mysqld'], '--no-defaults', '--initialize-insecure', '--basedir=' + str(t['mysqld'].parents[1]),
                 '--datadir=' + str(data)], stdout=log, stderr=subprocess.STDOUT)
    init = LOCAL / 'mysql-init.sql'
    init.write_text("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + config['rootPassword'] + "';\n"
                    "CREATE DATABASE IF NOT EXISTS shiguang_shop CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n"
                    "CREATE USER IF NOT EXISTS 'shiguang_app'@'127.0.0.1' IDENTIFIED BY '" + config['databasePassword'] + "';\n"
                    "GRANT ALL PRIVILEGES ON shiguang_shop.* TO 'shiguang_app'@'127.0.0.1';\n", encoding='utf-8')
    process = spawn([t['mysqld'], '--no-defaults', '--basedir=' + str(t['mysqld'].parents[1]),
                     '--datadir=' + str(data), '--port=13306', '--bind-address=127.0.0.1', '--mysqlx=0',
                     '--init-file=' + str(init), '--console'], 'mysql.log')
    for _ in range(60):
        if process.poll() is not None: raise RuntimeError('MySQL exited. See .local/mysql.log')
        if port_open(13306):
            probe = subprocess.run([str(t['mysql']), '--defaults-file=' + str(client_config), '-N', '-e', 'SELECT 1'], capture_output=True)
            if probe.returncode == 0:
                init.unlink()
                print('MySQL is ready on 127.0.0.1:13306', flush=True)
                return
        time.sleep(1)
    raise RuntimeError('MySQL startup timed out. See .local/mysql.log')

def build(t, config):
    pnpm = t['node'].parent.parent / 'node_modules/pnpm/bin/pnpm.cjs'
    if not pnpm.exists(): raise RuntimeError('pnpm not found next to Node; install pnpm and adjust the launcher.')
    if not (ROOT / 'frontend/node_modules/vite/bin/vite.js').exists():
        run([t['node'], pnpm, 'install', '--frozen-lockfile', '--store-dir', LOCAL / 'pnpm-store'], cwd=ROOT / 'frontend')
    run([t['node'], ROOT / 'frontend/node_modules/vite/bin/vite.js', 'build'], cwd=ROOT / 'frontend')
    run([t['maven'], '-B', '-ntp', '-Dmaven.repo.local=' + str(LOCAL / 'm2'), 'verify'], cwd=ROOT / 'backend', env=environment(t, config))

def start(t, config):
    database(t, config)
    if not port_open(11025):
        spawn([shutil.which('python'), ROOT / 'scripts/mail_capture.py'], 'mail-capture.log')
    if port_open(18080):
        with urllib.request.urlopen('http://127.0.0.1:18080/api/health', timeout=3) as response:
            if json.load(response).get('database') != 'UP': raise RuntimeError('Backend health check failed.')
        print('Shop is already running: http://127.0.0.1:18080', flush=True)
        return
    jar = ROOT / 'backend/target/shop-0.1.0.jar'
    if not jar.exists(): raise RuntimeError('Build first: python scripts/local.py build')
    process = spawn([t['java'], '-jar', jar], 'backend.log', environment(t, config))
    (LOCAL / 'backend.pid').write_text(str(process.pid))
    for _ in range(90):
        if process.poll() is not None: raise RuntimeError('Backend exited. See .local/backend.log')
        try:
            with urllib.request.urlopen('http://127.0.0.1:18080/api/health', timeout=1) as response:
                if json.load(response).get('database') == 'UP':
                    print('Shop is ready: http://127.0.0.1:18080', flush=True)
                    return
        except (OSError, ValueError): pass
        time.sleep(1)
    raise RuntimeError('Backend startup timed out. See .local/backend.log')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('action', choices=['database', 'build', 'start'])
    args = parser.parse_args(); t = tools(); config = settings()
    if args.action == 'database': database(t, config)
    elif args.action == 'build': build(t, config)
    else: start(t, config)
