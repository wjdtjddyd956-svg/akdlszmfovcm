#!/usr/bin/env python3
from playwright.sync_api import sync_playwright
from pathlib import Path
import json,threading,functools
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from urllib.parse import quote
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'releases/civic-update/1.0.0'
class Handler(SimpleHTTPRequestHandler):
 def log_message(self,*args):pass
http=ThreadingHTTPServer(('127.0.0.1',0),functools.partial(Handler,directory=str(OUT)));threading.Thread(target=http.serve_forever,daemon=True).start()
with sync_playwright() as pw:
 browser=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);page=browser.new_page(viewport={'width':1300,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.goto('http://127.0.0.1:'+str(http.server_port)+'/'+quote('메뉴-미리보기.html'))
 for i in range(6):page.locator('nav button').nth(i).click();assert page.locator('#grid button').count()==54
 page.locator('nav button').nth(0).click();page.locator('#grid button').nth(10).hover();assert '마을 설립' in page.locator('#detail').inner_text() and '10000원' in page.locator('#detail').inner_text()
 page.locator('nav button').nth(4).click();page.locator('#grid button').nth(4).click();assert '3개 전체 가격: 50원' in page.locator('#detail').inner_text();assert '개당 가격이 아닌' in page.locator('#detail').inner_text()
 page.locator('nav button').nth(3).click();page.locator('#grid button').nth(10).click();assert '판매자: Seller' in page.locator('#detail').inner_text();assert '3개 전체 50원' in page.locator('#detail').inner_text()
 assert not errors,errors;page.screenshot(path=str(OUT/'미리보기-브라우저.png'),full_page=True);page.set_viewport_size({'width':390,'height':844});page.reload();assert page.evaluate('document.documentElement.scrollWidth<=window.innerWidth');browser.close();http.shutdown()
(OUT/'preview-validation.json').write_text(json.dumps({'browser':'system Chromium via Playwright','headless':True,'screens':6,'slots_per_screen':54,'navigation_town_fee_whole_bundle_price_seller_tooltips':True,'mobile_width_390_tested':True,'javascript_errors':errors,'native_minecraft_client_tested':False},ensure_ascii=False,indent=2)+'\n');print('PASS: six screens, 54 slots, Korean prices/seller/quantity, mobile width, no JS errors')
