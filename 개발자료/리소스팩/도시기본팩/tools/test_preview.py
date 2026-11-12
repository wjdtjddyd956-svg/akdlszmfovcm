#!/usr/bin/env python3
from pathlib import Path
import json,threading,functools
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from urllib.parse import quote
from playwright.sync_api import sync_playwright
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file());OUT=ROOT/'releases/city-resourcepack'
checks=0
def check(condition):
    global checks
    checks+=1
    assert condition
server=ThreadingHTTPServer(('127.0.0.1',0),functools.partial(SimpleHTTPRequestHandler,directory=str(OUT)));thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
with sync_playwright() as pw:
    browser=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
    page=browser.new_page(viewport={'width':1280,'height':900});page.goto('http://127.0.0.1:'+str(server.server_port)+'/'+quote('미리보기.html'));page.wait_for_load_state('load')
    check(page.locator('article:visible').count()==32);check(page.locator('img').evaluate_all('(images)=>images.every(i=>i.complete&&i.naturalWidth===64&&i.naturalHeight===64)'))
    page.get_by_role('button',name='요리',exact=True).click();check(page.locator('article:visible').count()==10)
    page.get_by_role('button',name='메뉴',exact=True).click();check(page.locator('article:visible').count()==14)
    page.get_by_role('button',name='조작',exact=True).click();check(page.locator('article:visible').count()==2)
    page.get_by_role('button',name='전체',exact=True).click();check(page.locator('article:visible').count()==32)
    page.get_by_role('button',name='밝은 바탕 비교',exact=True).click();check(page.locator('body').evaluate("b=>b.classList.contains('light')"));page.get_by_role('button',name='밝은 바탕 비교',exact=True).click()
    page.screenshot(path=str(ROOT/'개발자료/리소스팩/도시기본팩/build/browser-desktop.png'),full_page=True)
    page.set_viewport_size({'width':390,'height':844});check(page.evaluate('document.documentElement.scrollWidth<=window.innerWidth'))
    page.get_by_role('button',name='요리',exact=True).click();check(page.locator('article:visible').count()==10)
    browser.close()
server.shutdown();server.server_close()
(OUT/'browser-validation.json').write_text(json.dumps({'passed':True,'checks':checks,'engine':'system Chromium / Playwright','desktop_width':1280,'mobile_width':390,'local_preview_only':True,'served_over_loopback_http':True},indent=2)+'\n');print('PASS:',checks,'offline preview browser checks')
