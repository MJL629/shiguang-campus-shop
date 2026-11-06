"""AI-assisted SMTP sandbox. Captures messages locally without sending to real recipients."""
from pathlib import Path
import asyncore, smtpd, time, uuid
ROOT=Path(__file__).resolve().parents[1]/'.local/mailbox'
ROOT.mkdir(parents=True,exist_ok=True)
class Capture(smtpd.SMTPServer):
    def process_message(self,peer,mailfrom,rcpttos,data,**kwargs):
        (ROOT/(str(int(time.time()))+'-'+uuid.uuid4().hex[:8]+'.eml')).write_bytes(data)
        print('Captured one test message',flush=True)
Capture(('127.0.0.1',11025),None,decode_data=False)
print('Local SMTP sandbox ready on 127.0.0.1:11025',flush=True)
asyncore.loop()
