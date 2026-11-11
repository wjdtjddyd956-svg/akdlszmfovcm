#!/usr/bin/env python3
from pathlib import Path
import subprocess,os,urllib.parse
ROOT=Path(__file__).resolve().parents[1];BASE=Path('/workspace/toolchains');BUILD=BASE/'farming-tests';BUILD.mkdir(exist_ok=True)
(BUILD/'settings.gradle').write_text("rootProject.name='farming-tests'\n")
s=(ROOT/'tools/integration.gradle').read_text().replace('@TOOLCHAINS@',str(BASE)).replace('@REPO@',str(ROOT))
s+='\nsourceSets.test.java.srcDirs = '+repr([str(ROOT/m/'src/main/java') for m in ['korean-economy','korean-farming','korean-codex','korean-menu']]+[str(ROOT/'korean-farming/src/test/java')])+'\n'
s+='sourceSets.test.resources.srcDirs = '+repr([str(ROOT/'korean-codex/src/main/resources')])+'\n'
s+="test { systemProperty 'farm.repo', "+repr(str(ROOT))+" }\n"
s+="dependencies { testImplementation files("+repr(str(ROOT/'releases/korean-plugins/worldguard-bukkit-7.0.21-ko.jar'))+", "+repr(str(ROOT/'releases/korean-plugins/worldedit-bukkit-7.4.6-ko.jar'))+") }\n"
(BUILD/'build.gradle').write_text(s)
args=['bash',str(BASE/'gradle-9.8.0/bin/gradle'),'--no-daemon','--console=plain','-p',str(BUILD)]
proxy=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
if proxy.hostname:args+=['-Dhttps.proxyHost='+proxy.hostname,'-Dhttps.proxyPort='+str(proxy.port or 80)]
args+=['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts','test']
subprocess.run(args,check=True,env=dict(os.environ,JAVA_HOME=str(BASE/'jdk-25.0.5.0.3'),GRADLE_USER_HOME=str(BASE/'gradle-cache')))
