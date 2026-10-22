#!/usr/bin/env python3
"""Capture the released Android UI with disposable local music and locale fixtures."""
from pathlib import Path
import subprocess, time, re, json, xml.etree.ElementTree as ET, struct, wave, math
OUT=Path('android-readme-captures');OUT.mkdir(exist_ok=True)
PACKAGE='com.neoplayer.app'

def adb(*args,timeout=90,check=True):
 r=subprocess.run(['adb',*map(str,args)],capture_output=True,timeout=timeout)
 if check and r.returncode:raise RuntimeError(f'adb {args}: {r.stderr.decode(errors="replace")} {r.stdout.decode(errors="replace")}')
 return r.stdout

def shell(*args,**kw):return adb('shell',*args,**kw)
def hierarchy(tag):
 shell('uiautomator','dump','/sdcard/readme-window.xml',check=False)
 data=shell('cat','/sdcard/readme-window.xml',check=False)
 (OUT/(tag+'.xml')).write_bytes(data)
 try:return ET.fromstring(data)
 except ET.ParseError:return ET.Element('empty')

def tap_match(tag,patterns):
 root=hierarchy(tag)
 nodes=list(root.iter('node'))
 print(tag,[(n.get('text'),n.get('content-desc')) for n in nodes if n.get('text') or n.get('content-desc')],flush=True)
 for pattern in patterns:
  for n in nodes:
   if any(re.search(pattern,n.get(a,''),re.I) for a in ['text','content-desc']):
    box=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
    if len(box)==4:
     shell('input','tap',(box[0]+box[2])//2,(box[1]+box[3])//2);time.sleep(2);return True
 return False

def capture(lang,name):
 time.sleep(2);data=adb('exec-out','screencap','-p');assert data.startswith(b'\x89PNG\r\n\x1a\n') and len(data)>10000
 (OUT/lang).mkdir(exist_ok=True);(OUT/lang/(name+'.png')).write_bytes(data);hierarchy(lang+'-'+name)

def pfield(n,b):return bytes([(n<<3)|2,len(b)])+b

def language_fixture(lang):
 # PreferencesProto string map; this is a disposable emulator, never a real user's datastore.
 entry=pfield(1,b'language')+pfield(2,pfield(5,lang.encode()))
 p=OUT/'neo_settings.preferences_pb';p.write_bytes(pfield(1,entry))
 shell('am','force-stop',PACKAGE)
 uid=shell('stat','-c','%u','/data/user/0/'+PACKAGE).decode().strip()
 shell('mkdir','-p',f'/data/user/0/{PACKAGE}/files/datastore')
 adb('push',p,'/data/local/tmp/neo_settings.preferences_pb')
 shell('cp','/data/local/tmp/neo_settings.preferences_pb',f'/data/user/0/{PACKAGE}/files/datastore/neo_settings.preferences_pb')
 shell('chown','-R',uid+':'+uid,f'/data/user/0/{PACKAGE}/files')
 shell('restorecon','-RF',f'/data/user/0/{PACKAGE}/files',check=False)

adb('root');adb('wait-for-device');time.sleep(2)
adb('install','-r','NEO-PLAYER-0.8Alpha-release.apk',timeout=180)
for permission in ['android.permission.READ_MEDIA_AUDIO','android.permission.POST_NOTIFICATIONS','android.permission.READ_EXTERNAL_STORAGE']:
 shell('pm','grant',PACKAGE,permission,check=False)
shell('settings','put','global','window_animation_scale','0');shell('settings','put','global','transition_animation_scale','0');shell('settings','put','global','animator_duration_scale','0')
shell('settings','put','system','screen_off_timeout','2147483647');shell('input','keyevent','82')
# Real, generated audio fixtures; no app source or UI is replaced.
Path('audio-fixtures').mkdir(exist_ok=True)
for i,title in enumerate(['First Light','Blue Horizon','Midnight Signal']):
 p=Path('audio-fixtures')/(title+'.wav')
 rate=22050
 with wave.open(str(p),'wb') as w:
  w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate)
  block=b''.join(struct.pack('<h',int(700*math.sin(2*math.pi*(220+i*55)*t/rate))) for t in range(rate))
  w.writeframes(block*36)
 adb('push',p,'/sdcard/Music/'+title+'.wav');shell('am','broadcast','-a','android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d','file:///sdcard/Music/'+title+'.wav',check=False)
shell('am','broadcast','-a','android.intent.action.MEDIA_MOUNTED','-d','file:///sdcard',check=False)
time.sleep(5)
for lang in ['en','fa']:
 language_fixture(lang);shell('cmd','locale','set-app-locales',PACKAGE,'--user','0','--locales',lang,check=False);shell('am','start','-n',PACKAGE+'/.MainActivity');time.sleep(8)
 # Dismiss an OS permission dialog only if one appears.
 tap_match(lang+'-permissions',[r'^Allow$',r'While using',r'اجازه',r'مجاز']);time.sleep(2)
 capture(lang,'01-home')
 if tap_match(lang+'-library-nav',[r'^Library$',r'^کتابخانه$',r'^Songs$',r'^آهنگ‌ها$']):
  capture(lang,'02-library')
  if tap_match(lang+'-play-song',[r'First Light',r'Blue Horizon',r'Midnight Signal']):
   time.sleep(2)
   tap_match(lang+'-open-player',[r'Expand player',r'Open player',r'Now playing',r'نمایش پخش',r'پخش‌کننده'])
   root=hierarchy(lang+'-mini-player');nodes=[n for n in root.iter('node') if any(title in n.get('text','') for title in ['First Light','Blue Horizon','Midnight Signal'])];nodes.sort(key=lambda n:int(re.findall(r'\d+',n.get('bounds','[0,0][0,0]'))[1]),reverse=True)
   if nodes:
    bounds=list(map(int,re.findall(r'\d+',nodes[0].get('bounds',''))));shell('input','tap',(bounds[0]+bounds[2])//2,(bounds[1]+bounds[3])//2);time.sleep(2)
   capture(lang,'03-player')
 shell('input','keyevent','4');time.sleep(1)
 tap_match(lang+'-home-nav',[r'^Home$',r'^خانه$']);time.sleep(1)
 if tap_match(lang+'-settings-nav',[r'^Settings$',r'^تنظیمات$',r'settings']):capture(lang,'04-settings')
 (OUT/(lang+'-logcat.txt')).write_bytes(adb('logcat','-d','-t','1200',check=False))
(OUT/'provenance.json').write_text(json.dumps({'app':'NEO Player','release':'v0.8.0-alpha','runtime':'Android API 34 x86_64 emulator with KVM on GitHub Actions','capture':'adb exec-out screencap -p','fixtures':'Generated local WAV files and language preference in disposable emulator; original signed APK unmodified','apk_sha256':__import__('hashlib').sha256(Path('NEO-PLAYER-0.8Alpha-release.apk').read_bytes()).hexdigest()},indent=2))
print('Captured',sorted(str(p) for p in OUT.rglob('*.png')),flush=True)
