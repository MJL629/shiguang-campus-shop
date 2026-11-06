# coding: utf-8
"""AI-assisted tests for the isolated local SMTP capture utility."""
from pathlib import Path
from email import policy
from email.message import EmailMessage
from email.parser import BytesParser
import smtplib,tempfile,threading,unittest
from mail_capture import CaptureServer

class CaptureTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.server=CaptureServer(('127.0.0.1',0),self.temp.name)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.temp.cleanup()
    def test_mime_message_is_saved_without_external_delivery(self):
        message=EmailMessage();message['From']='test@example.test';message['To']='reader@example.test';message['Subject']='Shipment confirmation';message.set_content('Test order\n.Line beginning with a dot\n')
        with smtplib.SMTP('127.0.0.1',self.server.server_address[1],timeout=5) as smtp:smtp.send_message(message)
        files=list(Path(self.temp.name).glob('*.eml'));self.assertEqual(len(files),1)
        parsed=BytesParser(policy=policy.default).parsebytes(files[0].read_bytes());self.assertIn('.Line beginning with a dot',parsed.get_content())
    def test_recipient_without_sender_is_rejected(self):
        with smtplib.SMTP('127.0.0.1',self.server.server_address[1],timeout=5) as smtp:
            code,_=smtp.rcpt('reader@example.test');self.assertEqual(code,503)
        self.assertFalse(list(Path(self.temp.name).glob('*.eml')))

if __name__=='__main__':unittest.main()
