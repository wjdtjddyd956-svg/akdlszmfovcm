#!/usr/bin/env python3
"""Build unchanged API sources from the user's Paper commit with public compile dependencies.

This is a local API build, not an official published Paper JAR or a server build.
The Bungee compile dependency uses Central's 1.21-R0.4 instead of Paper's deprecated fork;
our plugins do not call Bungee APIs. Record that substitution in the provenance report.
"""
from pathlib import Path
import json
import os
import subprocess
import urllib.parse
import zipfile

BASE=Path(os.environ.get('TYCOON_TOOLCHAINS','/workspace/toolchains'))
JDK=Path(os.environ.get('TYCOON_JDK',str(BASE/'jdk-25.0.5.0.3')))
PAPER=BASE/'paper-source/Paper-df120fd'
BRIGADIER=BASE/'brigadier-source/brigadier-master'
GRADLE=BASE/'gradle-9.8.0/bin/gradle'
BUILD=BASE/'paper-api-build';BUILD.mkdir(exist_ok=True)
assert PAPER.exists() and BRIGADIER.exists() and GRADLE.exists(), 'Prepare official source archives and Gradle listed in 이전자료/생활타이쿤-작업기록.md'
classes=BUILD/'brigadier-classes';classes.mkdir(exist_ok=True)
subprocess.run([str(JDK/'bin/javac'),'--release','21','-encoding','UTF-8','-d',str(classes)]+[str(p) for p in (BRIGADIER/'src/main/java').rglob('*.java')],check=True)
brigadier=BUILD/'brigadier-local.jar'
with zipfile.ZipFile(brigadier,'w',zipfile.ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
dependencies=[
    'com.google.guava:guava:33.6.0-jre','com.google.code.gson:gson:2.14.0','org.yaml:snakeyaml:2.2',
    'org.joml:joml:1.10.9','it.unimi.dsi:fastutil:8.5.18','org.apache.logging.log4j:log4j-api:2.26.0',
    'org.slf4j:slf4j-api:2.0.17','net.md-5:bungeecord-chat:1.21-R0.4',
    'org.apache.maven:maven-resolver-provider:3.9.6','org.apache.maven.resolver:maven-resolver-connector-basic:1.9.18',
    'org.apache.maven.resolver:maven-resolver-transport-http:1.9.18','org.jetbrains:annotations:26.1.0',
    'org.checkerframework:checker-qual:4.2.3','org.jspecify:jspecify:1.0.0'
]
adventure=['api','key','text-minimessage','text-serializer-gson','text-serializer-legacy','text-serializer-plain','text-logger-slf4j']
dependencies += ['net.kyori:adventure-'+name+':5.2.0' for name in adventure]
script="""plugins { id 'java-library' }
repositories { mavenCentral() }
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
tasks.withType(JavaCompile).configureEach { options.encoding = 'UTF-8'; options.release = 25 }
"""
script += 'sourceSets.main.java.srcDirs = '+json.dumps([str(PAPER/'paper-api/src/main/java'),str(PAPER/'paper-api/src/generated/java')])+"\n"
script += 'sourceSets.main.resources.srcDirs = '+json.dumps([str(PAPER/'paper-api/src/main/resources')])+"\n"
script += 'dependencies {\n'+'\n'.join(' implementation '+json.dumps(d) for d in dependencies)+'\n implementation files('+json.dumps(str(brigadier))+')\n}\n'
script += "tasks.named('jar') { archiveFileName = 'paper-api-26.3-df120fd-local.jar' }\n"
script += "tasks.register('copyDeps', Copy) { from configurations.compileClasspath; into layout.buildDirectory.dir('deps') }\n"
(BUILD/'build.gradle').write_text(script)
(BUILD/'settings.gradle').write_text("rootProject.name='paper-api-exact-local'\n")
env=dict(os.environ,JAVA_HOME=str(JDK),GRADLE_USER_HOME=str(BASE/'gradle-cache'))
args=['bash',str(GRADLE),'--no-daemon','--console=plain','-p',str(BUILD)]
proxy=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',os.environ.get('https_proxy','')))
if proxy.hostname:
    for scheme in ['http','https']:args += ['-D'+scheme+'.proxyHost='+proxy.hostname,'-D'+scheme+'.proxyPort='+str(proxy.port or 80)]
trust=Path('/etc/ssl/certs/java/cacerts')
if trust.exists():args+=['-Djavax.net.ssl.trustStore='+str(trust)]
args+=['jar','copyDeps']
subprocess.run(args,env=env,check=True)
report={'paper_commit':'df120fd','minecraft':'26.3','api_sources_modified':False,'official_published_jar':False,
    'bungee_compile_dependency':'net.md-5:bungeecord-chat:1.21-R0.4 (Central; replaces deprecated Paper compile fork)',
    'brigadier_compile_dependency':'Mojang/brigadier master source (local build)',
    'jar':str(BUILD/'build/libs/paper-api-26.3-df120fd-local.jar')}
(BUILD/'provenance.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
