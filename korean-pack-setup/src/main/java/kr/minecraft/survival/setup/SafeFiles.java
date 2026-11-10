package kr.minecraft.survival.setup;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Back up every original first; if an apply fails, restore already changed files. */
final class SafeFiles {
    interface Writer {void write(Path path,byte[] bytes)throws IOException;}
    static void atomic(Path path,byte[] bytes)throws IOException {
        Files.createDirectories(path.getParent());
        Path temp=Files.createTempFile(path.getParent(),"korean-setup-",".tmp");
        try {
            Files.write(temp,bytes);
            try {Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }
    static void apply(Path root,Path backup,Map<String,byte[]> files)throws IOException {apply(root,backup,files,SafeFiles::atomic);}
    static void apply(Path root,Path backup,Map<String,byte[]> files,Writer writer)throws IOException {
        root=root.toAbsolutePath().normalize();
        Map<Path,byte[]> originals=new LinkedHashMap<>();
        Set<Path> missing=new HashSet<>();
        for(String relative:files.keySet()) {
            Path target=root.resolve(relative).normalize();
            if(!target.startsWith(root))throw new IOException("허용되지 않는 파일 경로: "+relative);
            if(Files.exists(target)) {
                byte[] original=Files.readAllBytes(target);originals.put(target,original);
                Path dest=backup.resolve(relative);Files.createDirectories(dest.getParent());Files.write(dest,original,StandardOpenOption.CREATE_NEW);
            } else {originals.put(target,null);missing.add(target);}
        }
        List<Path> attempted=new ArrayList<>();
        try {
            for(Map.Entry<String,byte[]> entry:files.entrySet()) {
                Path target=root.resolve(entry.getKey()).normalize();attempted.add(target);writer.write(target,entry.getValue());
            }
        } catch(IOException|RuntimeException failure) {
            Collections.reverse(attempted);
            for(Path target:attempted) {
                try {if(missing.contains(target))Files.deleteIfExists(target);else atomic(target,originals.get(target));}
                catch(IOException rollback){failure.addSuppressed(rollback);}
            }
            throw failure;
        }
    }
}
