#!/usr/bin/env python3
from playwright.sync_api import sync_playwright
from pathlib import Path
import json,threading,functools
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from urllib.parse import quote
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'releases/ui-update'
class Handler(SimpleHTTPRequestHandler):
 def log_message(self,*args):pass
http=ThreadingHTTPServer(("127.0.0.1",0),functools.partial(Handler,directory=str(OUT)));threading.Thread(target=http.serve_forever,daemon=True).start()
with sync_playwright() as pw:
 browser=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);page=browser.new_page(viewport={'width':1300,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.goto('http://127.0.0.1:'+str(http.server_port)+'/'+quote('메뉴-미리보기.html'));page.locator('nav button').nth(1).click();assert page.locator('#grid button').count()==54;page.locator('#grid button').nth(10).hover();assert '숲과 채집' in page.locator('#detail').inner_text();page.get_by_role('button',name='아이템 소비 · 실제 보상 확인').click();page.locator('#grid button').nth(22).click();assert '실제 증가' in page.locator('#detail').inner_text();assert not errors,errors;page.screenshot(path=str(OUT/'미리보기-브라우저.png'),full_page=True);page.set_viewport_size({'width':390,'height':844});page.reload();assert page.evaluate('document.documentElement.scrollWidth<=window.innerWidth');browser.close();http.shutdown()
(OUT/'preview-validation.json').write_text(json.dumps({'browser':'system Chromium via Playwright','headless':True,'screens':7,'slots_per_screen':54,'navigation_tooltips_reward_details_tested':True,'mobile_width_390_tested':True,'javascript_errors':errors,'native_minecraft_client_tested':False},ensure_ascii=False,indent=2)+'\n');print('PASS: interactive preview navigation, 54 slots, tooltips, actual reward detail, mobile width, no JS errors')
