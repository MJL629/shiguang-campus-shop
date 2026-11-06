# coding: utf-8
"""AI-assisted SMTP test inbox using Python's standard library; never relays mail externally."""
from pathlib import Path
import socketserver,time,uuid

class CaptureHandler(socketserver.StreamRequestHandler):
    def reply(self,text): self.wfile.write((text+'\r\n').encode('ascii'));self.wfile.flush()
    def handle(self):
        self.request.settimeout(20);self.reply('220 localhost Shiguang test inbox')
        sender=False;recipients=False
        while True:
            try:line=self.rfile.readline(65536)
            except (TimeoutError,OSError):return
            if not line:return
            command=line.decode('ascii',errors='replace').strip();upper=command.upper()
            if upper.startswith(('EHLO ','HELO ')):
                self.reply('250-localhost');self.reply('250-8BITMIME');self.reply('250 SIZE 1048576')
            elif upper.startswith('MAIL FROM:'):
                sender=True;recipients=False;self.reply('250 Sender accepted')
            elif upper.startswith('RCPT TO:'):
                if not sender:self.reply('503 Send MAIL FROM first')
                else:recipients=True;self.reply('250 Recipient accepted for local capture only')
            elif upper=='DATA':
                if not sender or not recipients:self.reply('503 Envelope is incomplete');continue
                self.reply('354 End message with a dot on its own line')
                content=bytearray();too_large=False
                while True:
                    part=self.rfile.readline(65536)
                    if not part:return
                    if part in (b'.\r\n',b'.\n'):break
                    if part.startswith(b'..'):part=part[1:]
                    if len(content)+len(part)<=1048576:content.extend(part)
                    else:too_large=True
                if too_large:self.reply('552 Message exceeds local capture limit')
                else:
                    destination=self.server.mailbox/(str(int(time.time()))+'-'+uuid.uuid4().hex[:8]+'.eml')
                    destination.write_bytes(content);self.reply('250 Message captured locally')
                    print('Captured one test message',flush=True)
                sender=False;recipients=False
            elif upper=='RSET':sender=False;recipients=False;self.reply('250 Reset')
            elif upper=='NOOP':self.reply('250 OK')
            elif upper=='QUIT':self.reply('221 Bye');return
            else:self.reply('502 Command not supported by this local test inbox')

class CaptureServer(socketserver.ThreadingTCPServer):
    allow_reuse_address=True
    daemon_threads=True
    def __init__(self,address,mailbox):
        self.mailbox=Path(mailbox);self.mailbox.mkdir(parents=True,exist_ok=True)
        super().__init__(address,CaptureHandler)

if __name__=='__main__':
    mailbox=Path(__file__).resolve().parents[1]/'.local/mailbox'
    with CaptureServer(('127.0.0.1',11025),mailbox) as server:
        print('Local SMTP sandbox ready on 127.0.0.1:11025',flush=True)
        server.serve_forever()
