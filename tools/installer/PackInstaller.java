import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Standalone Java 21+ installer. Payload is fully verified before touching a server. */
public final class PackInstaller {
    static final String BACKUPS="tycoon-server-backups";
    static final class Locks implements AutoCloseable {
        final List<FileChannel> channels=new ArrayList<>();final List<FileLock> locks=new ArrayList<>();
        Locks(Path server)throws IOException {try(var walk=Files.walk(server,5)){for(Path p:walk.filter(p->(p.getFileName().toString().equals("session.lock")||p.equals(server.resolve("life-control/manager.lock")))&&!p.startsWith(server.resolve(BACKUPS))).toList()){
            FileChannel c=FileChannel.open(p,StandardOpenOption.WRITE);channels.add(c);FileLock l=c.tryLock();if(l==null)throw new IOException("서버가 실행 중입니다. 콘솔에서 stop으로 종료하세요.");locks.add(l);
        }}catch(IOException|RuntimeException e){close();throw e;}}
        public void close()throws IOException {for(FileLock l:locks)l.release();for(FileChannel c:channels)c.close();}
    }
    static String stamp(){return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());}
    static String sha(Path file)throws Exception {MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(file)){byte[] b=new byte[65536];for(int n;(n=in.read(b))>0;)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}
    static Path safe(Path base,String name)throws IOException {Path result=base.resolve(name).normalize();if(!result.startsWith(base)||Path.of(name).isAbsolute()||name.contains("\\")||name.contains(":"))throw new IOException("안전하지 않은 파일 경로: "+name);return result;}
    static void noLinks(Path root)throws IOException {try(var paths=Files.walk(root)){for(Path p:paths.toList())if(Files.isSymbolicLink(p))throw new IOException("심볼릭 링크가 있는 폴더는 먼저 별도로 백업하세요: "+p);}}
    static Properties properties(InputStream in)throws IOException {try(in){Properties p=new Properties();p.load(in);return p;}}
    static String name(Path jar)throws IOException {try(ZipFile z=new ZipFile(jar.toFile())){ZipEntry e=z.getEntry("plugin.yml");if(e==null)return null;String text=new String(z.getInputStream(e).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);Matcher m=Pattern.compile("(?m)^name:[ \\t]*['\"]?([^'\"\\r\\n]+)").matcher(text);return m.find()?m.group(1).trim():null;}}
    static String version(Path jar)throws IOException {
        try(ZipFile zip=new ZipFile(jar.toFile())){
            String yaml=new String(zip.getInputStream(zip.getEntry("plugin.yml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            for(String line:yaml.lines().toList())if(line.startsWith("version:")){String value=line.substring(8).strip();if(value.length()>=2&&((value.charAt(0)==39&&value.charAt(value.length()-1)==39)||(value.charAt(0)==34&&value.charAt(value.length()-1)==34)))value=value.substring(1,value.length()-1);return value;}
            return "";
        }
    }
    static void server(Path root)throws IOException {if(!Files.isDirectory(root)||!Files.isRegularFile(root.resolve("server.properties")))throw new IOException("server.properties가 있는 실제 서버 폴더를 선택하세요.");if(Files.isSymbolicLink(root))throw new IOException("실제 서버 경로를 사용하세요.");}
    static Path backup(Path root)throws Exception {
        server(root);noLinks(root);Path dir=root.resolve(BACKUPS);Files.createDirectories(dir);Path zip=dir.resolve("전체서버-"+stamp()+".zip"),part=Path.of(zip+".part");Properties hashes=new Properties();
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(part))){
            try(var walk=Files.walk(root)){for(Path p:walk.filter(Files::isRegularFile).filter(p->!p.startsWith(dir)).sorted().toList()){
                String rel=root.relativize(p).toString().replace('\\','/');if(rel.endsWith("/session.lock")||rel.equals("session.lock")||rel.equals(".tycoon-install.lock"))continue;if(rel.equals("TYCOON-SHA256.properties"))throw new IOException("예약된 백업 파일 이름이 서버에 있습니다.");
                hashes.setProperty(rel,sha(p));out.putNextEntry(new ZipEntry(rel));Files.copy(p,out);out.closeEntry();if(!hashes.getProperty(rel).equals(sha(p)))throw new IOException("백업 도중 파일이 변경됐습니다. 서버를 완전히 종료하세요: "+rel);
            }}out.putNextEntry(new ZipEntry("TYCOON-SHA256.properties"));hashes.store(out,"Offline full-server backup SHA-256");out.closeEntry();
        }catch(Exception e){Files.deleteIfExists(part);throw e;}
        verify(part);Files.move(part,zip);System.out.println("전체 서버 백업 완료: "+zip);return zip;
    }
    static void verify(Path zip)throws Exception {try(ZipFile z=new ZipFile(zip.toFile())){ZipEntry manifest=z.getEntry("TYCOON-SHA256.properties");if(manifest==null)throw new IOException("검증 목록이 없는 백업입니다.");Properties hashes=properties(z.getInputStream(manifest));Set<String> seen=new HashSet<>();Enumeration<? extends ZipEntry> entries=z.entries();
        while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();String n=e.getName();safe(Path.of("/verify-root"),n);if(!seen.add(n))throw new IOException("중복 ZIP 항목");if(n.equals("TYCOON-SHA256.properties"))continue;if(e.isDirectory()||hashes.getProperty(n)==null)throw new IOException("검증되지 않은 ZIP 항목: "+n);MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=z.getInputStream(e)){byte[] b=new byte[65536];for(int len;(len=in.read(b))>0;)d.update(b,0,len);}if(!HexFormat.of().formatHex(d.digest()).equals(hashes.getProperty(n)))throw new IOException("백업 검증 실패: "+n);}
        if(seen.size()!=hashes.size()+1)throw new IOException("백업 파일 누락");}}
    static void install(Path root,Path payload)throws Exception {install(root,payload,false);}
    static void install(Path root,Path payload,boolean farming)throws Exception {
        server(root);if(payload.startsWith(root))throw new IOException("배포 ZIP은 서버 폴더 바깥에 풀어 실행하세요.");noLinks(root);noLinks(payload);
        Path legacy=root.resolve("plugins/KoreanEconomy/생활기록");if(Files.isDirectory(legacy))try(var files=Files.list(legacy)){for(Path f:files.filter(p->p.toString().endsWith(".properties")).toList()){Properties old=new Properties();try(Reader reader=Files.newBufferedReader(f,java.nio.charset.StandardCharsets.UTF_8)){old.load(reader);}if(old.containsKey("pending.id"))throw new IOException("1.2 미결 거래가 있습니다. 기존 1.2에서 /경제거래 "+f.getFileName().toString().replace(".properties","")+" 확인 으로 정리한 뒤 업그레이드하세요.");}}
        Properties hashes=properties(Files.newInputStream(payload.resolve("SHA256.properties")));if(hashes.isEmpty())throw new IOException("설치 파일 목록이 비어 있습니다.");
        Map<String,Path> incoming=new LinkedHashMap<>();for(String rel:new TreeSet<>(hashes.stringPropertyNames())){Path p=safe(payload,rel);if(!p.getFileName().toString().endsWith(".jar")||!sha(p).equalsIgnoreCase(hashes.getProperty(rel)))throw new IOException("플러그인 파일 검증 실패: "+rel);String name=name(p);if(name==null||incoming.put(name,p)!=null)throw new IOException("중복 또는 잘못된 플러그인: "+rel);}
        if(farming){
            if(!incoming.keySet().equals(Set.of("KoreanEconomy","KoreanFarming","KoreanCodex")))throw new IOException("농장 업그레이드는 경제·농사·도감 3개 JAR만 설치합니다.");
            if(!version(incoming.get("KoreanEconomy")).equals("2.0.2")||!version(incoming.get("KoreanFarming")).equals("1.0.0")||!version(incoming.get("KoreanCodex")).equals("1.1.0"))throw new IOException("농장 업그레이드 버전 조합이 다릅니다.");
            Map<String,Path> installed=new HashMap<>();try(var files=Files.list(root.resolve("plugins"))){for(Path file:files.filter(f->f.toString().endsWith(".jar")).toList()){String n=name(file);if(n!=null&&Set.of("KoreanEconomy","KoreanFarming","KoreanCodex","Vault").contains(n)&&installed.put(n,file)!=null)throw new IOException("중복된 기존 JAR를 먼저 확인하세요: "+n);}}
            if(!installed.containsKey("Vault")||!installed.containsKey("KoreanEconomy")||!Set.of("2.0.1","2.0.2").contains(version(installed.get("KoreanEconomy"))))throw new IOException("기존 생활타이쿤 2.0.1/2.0.2와 Vault가 필요합니다. 먼저 기존 생활서버를 업그레이드하세요.");
            if(installed.containsKey("KoreanCodex")&&!Set.of("1.0.0","1.0.1","1.1.0").contains(version(installed.get("KoreanCodex"))))throw new IOException("새로운 도감을 이전 버전으로 덮어쓰지 않습니다.");
            if(installed.containsKey("KoreanFarming")&&!version(installed.get("KoreanFarming")).equals("1.0.0"))throw new IOException("새로운 농장을 이전 버전으로 덮어쓰지 않습니다.");
        }else{
        if(!incoming.keySet().containsAll(Set.of("KoreanEconomy","KoreanMenu","KoreanCommands","KoreanPackSetup","Vault","Essentials","EssentialsSpawn","QuickShop-Hikari")))throw new IOException("필수 플러그인이 누락되었습니다. 공식 EssentialsX 다운로드 단계를 먼저 완료하세요.");
        if(version(incoming.get("Essentials")).isEmpty()||!version(incoming.get("Essentials")).equals(version(incoming.get("EssentialsSpawn"))))throw new IOException("Essentials 본체와 Spawn 버전이 다릅니다.");
        try(ZipFile core=new ZipFile(incoming.get("Essentials").toFile())){var e=core.getEntry("com/earth2me/essentials/utils/VersionUtil.class");if(e==null||!new String(core.getInputStream(e).readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1).contains("v26_3_R01"))throw new IOException("Paper 26.3 지원 EssentialsX가 필요합니다. 미지원 2.22.0을 재설치하지 않습니다.");}
        }
        Path backup=backup(root);Path plugins=root.resolve("plugins");Files.createDirectories(plugins);Path retired=root.resolve(BACKUPS).resolve("교체전-JAR-"+stamp());Files.createDirectories(retired);List<Path> moved=new ArrayList<>(),added=new ArrayList<>();
        try{try(var files=Files.list(plugins)){for(Path p:files.filter(f->f.getFileName().toString().endsWith(".jar")).toList())if(incoming.containsKey(name(p))){Files.move(p,retired.resolve(p.getFileName()));moved.add(p);}}
            for(Path p:incoming.values()){Path dest=plugins.resolve(p.getFileName());if(Files.exists(dest))throw new IOException("동일 파일명 충돌: "+dest);Files.copy(p,dest);added.add(dest);}
        }catch(Exception e){for(Path p:added)Files.deleteIfExists(p);for(Path p:moved)Files.move(retired.resolve(p.getFileName()),p);throw e;}
        System.out.println("설치 완료: "+plugins+" · "+incoming.size()+"개 플러그인");System.out.println("기존 데이터 폴더는 유지했습니다. 복원용 백업: "+backup);System.out.println("서버를 시작한 뒤 /plugins와 /경제팩설정, /직업을 확인하세요.");
    }
    static void restore(Path root,Path zip)throws Exception {
        server(root);verify(zip);Path current=backup(root);Path stage=Files.createTempDirectory(root.getParent(),"tycoon-restore-");Path retired=root.resolve(BACKUPS).resolve("복원전-"+stamp());Files.createDirectories(retired);List<Path> moved=new ArrayList<>(),added=new ArrayList<>();
        try{
            try(ZipFile z=new ZipFile(zip.toFile())){var entries=z.entries();while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();if(e.getName().equals("TYCOON-SHA256.properties"))continue;Path f=safe(stage,e.getName());Files.createDirectories(f.getParent());try(InputStream in=z.getInputStream(e)){Files.copy(in,f);}}}
            // Move whole top-level trees, so newer files cannot survive an older complete restore.
            try(var list=Files.list(root)){for(Path p:list.filter(p->!Set.of(BACKUPS,".tycoon-install.lock").contains(p.getFileName().toString())).toList()){Files.move(p,retired.resolve(p.getFileName()));moved.add(p);}}
            try(var list=Files.list(stage)){for(Path p:list.toList()){Path dest=root.resolve(p.getFileName());Files.move(p,dest);added.add(dest);}}
        }catch(Exception e){for(Path p:added)deleteTree(p);for(Path p:moved)Files.move(retired.resolve(p.getFileName()),p);throw e;}finally{deleteTree(stage);}
        System.out.println("전체 복원 완료. 복원 직전 백업: "+current);
    }
    static void deleteTree(Path root)throws IOException {if(!Files.exists(root))return;try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    public static void main(String[] args)throws Exception {
        if(args.length<2){System.out.println("사용법: java -jar 설치도구.jar 설치|농장설치|백업|검증|복원 서버폴더 [파일폴더/백업ZIP] --서버종료확인");return;}
        String op=args[0];Path root=Path.of(args[1]).toAbsolutePath().normalize();if(op.equals("검증")){verify(root);System.out.println("백업 SHA-256 검증 통과");return;}
        if(!Arrays.asList(args).contains("--서버종료확인"))throw new IOException("콘솔 stop 후 Java 프로세스가 종료됐는지 확인하고 --서버종료확인을 붙이세요.");server(root);
        try(FileChannel installChannel=FileChannel.open(root.resolve(".tycoon-install.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock installLock=installChannel.tryLock()){if(installLock==null)throw new IOException("다른 설치 도구가 실행 중입니다.");try(Locks locks=new Locks(root)){}switch(op){case "설치"->install(root,Path.of(args[2]).toAbsolutePath().normalize());case "농장설치"->install(root,Path.of(args[2]).toAbsolutePath().normalize(),true);case "백업"->backup(root);case "복원"->restore(root,Path.of(args[2]).toAbsolutePath().normalize());default->throw new IllegalArgumentException("알 수 없는 작업");}}
    }
}
