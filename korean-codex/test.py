#!/usr/bin/env python3
from pathlib import Path
import subprocess,os,urllib.parse
ROOT=Path(__file__).resolve().parents[1];BASE=Path('/workspace/toolchains');BUILD=BASE/'korean-codex-tests';BUILD.mkdir(exist_ok=True)
(BUILD/'settings.gradle').write_text("rootProject.name='korean-codex-tests'\n")
script=(ROOT/'tools/integration.gradle').read_text().replace('@TOOLCHAINS@',str(BASE)).replace('@REPO@',str(ROOT))
script+='\nsourceSets.test.java.srcDirs = '+repr([str(ROOT/'korean-codex/src/main/java'),str(ROOT/'korean-codex/src/test/java'),str(ROOT/'korean-menu/src/main/java')])+'\n'
script+='sourceSets.test.resources.srcDirs = '+repr([str(ROOT/'korean-codex/src/main/resources')])+'\n'
script+="test { systemProperty 'codex.repo', "+repr(str(ROOT))+" }\n";(BUILD/'build.gradle').write_text(script)
args=['bash',str(BASE/'gradle-9.8.0/bin/gradle'),'--no-daemon','--console=plain','-p',str(BUILD)]
proxy=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
if proxy.hostname:args+=['-Dhttps.proxyHost='+proxy.hostname,'-Dhttps.proxyPort='+str(proxy.port or 80)]
args+=['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts','test']
subprocess.run(args,check=True,env=dict(os.environ,JAVA_HOME=str(BASE/'jdk-25.0.5.0.3'),GRADLE_USER_HOME=str(BASE/'gradle-cache')))
