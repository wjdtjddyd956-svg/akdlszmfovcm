#!/usr/bin/env python3
from pathlib import Path
import os,subprocess,urllib.parse
ROOT=Path(__file__).resolve().parents[1];BASE=Path('/workspace/toolchains');BUILD=BASE/'civic-tests';BUILD.mkdir(exist_ok=True)
(BUILD/'settings.gradle').write_text("rootProject.name='civic-tests'\n")
s=(ROOT/'tools/integration.gradle').read_text().replace('@TOOLCHAINS@',str(BASE)).replace('@REPO@',str(ROOT))
s+='\nsourceSets.test.java.srcDirs = '+repr([str(ROOT/m/'src/main/java') for m in ['korean-economy','korean-civic','korean-menu','city-textures']]+[str(ROOT/'korean-civic/src/test/java')])+'\n'
extra=BUILD/'ui-resources';extra.mkdir(exist_ok=True)
for name in ['ui.yml','ui-icons.tsv','icons.tsv']:(extra/name).write_bytes((ROOT/'city-textures/src/main/resources'/name).read_bytes())
s+='sourceSets.test.resources.srcDirs = '+repr([str(ROOT/'korean-civic/src/main/resources'),str(extra)])+'\n'
s+="test { systemProperty 'civic.repo', "+repr(str(ROOT))+" }\n"
(BUILD/'build.gradle').write_text(s)
args=['bash',str(BASE/'gradle-9.8.0/bin/gradle'),'--no-daemon','--console=plain','-p',str(BUILD)]
p=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
if p.hostname:args+=['-Dhttps.proxyHost='+p.hostname,'-Dhttps.proxyPort='+str(p.port or 80)]
args+=['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts','test']
subprocess.run(args,check=True,env=dict(os.environ,JAVA_HOME=str(BASE/'jdk-25.0.5.0.3'),GRADLE_USER_HOME=str(BASE/'gradle-cache')))
