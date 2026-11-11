#!/usr/bin/env python3
from pathlib import Path
import os,subprocess,urllib.parse
ROOT=Path(__file__).resolve().parents[1];BASE=Path('/workspace/toolchains');BUILD=BASE/'tycoon-integration'
BUILD.mkdir(parents=True,exist_ok=True)
(BUILD/'settings.gradle').write_text("rootProject.name='tycoon-integration'\n")
p=BUILD/'build.gradle';s=(ROOT/'tools/integration.gradle').read_text().replace('@TOOLCHAINS@',str(BASE)).replace('@REPO@',str(ROOT));marker='// TYCOON SOURCES'
s+='\n'+marker+'\nsourceSets.test.java.srcDirs = '+repr([str(ROOT/'korean-economy/src/main/java'),str(ROOT/'korean-menu/src/main/java'),str(ROOT/'korean-economy/src/integrationTest/java')])+'\n'
s+='test { systemProperty \'tycoon.repo\', '+repr(str(ROOT))+' }\n'
s+='sourceSets.test.resources.srcDirs = '+repr([str(ROOT/'korean-economy/src/main/resources')])+'\n'
p.write_text(s)
env=dict(os.environ,JAVA_HOME=str(BASE/'jdk-25.0.5.0.3'),GRADLE_USER_HOME=str(BASE/'gradle-cache'))
args=['bash',str(BASE/'gradle-9.8.0/bin/gradle'),'--no-daemon','--console=plain','-p',str(BUILD)]
proxy=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
if proxy.hostname:args+=['-Dhttps.proxyHost='+proxy.hostname,'-Dhttps.proxyPort='+str(proxy.port or 80)]
args+=['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts','test']
subprocess.run(args,check=True,env=env)
