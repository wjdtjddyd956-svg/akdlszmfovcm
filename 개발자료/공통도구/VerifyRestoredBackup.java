import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Verify a cold restore against every file in the installer's original manifest. */
public final class VerifyRestoredBackup {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[1]).toAbsolutePath().normalize();int checked=0;
        try(ZipFile zip=new ZipFile(args[0])) {
            Properties manifest=new Properties();try(var in=zip.getInputStream(zip.getEntry("TYCOON-SHA256.properties"))){manifest.load(in);}
            for(String name:manifest.stringPropertyNames()) {
                Path file=root.resolve(name).normalize();if(!file.startsWith(root))throw new IOException("Unsafe manifest path");
                if(!Files.isRegularFile(file))throw new IOException("Missing restored file: "+name);
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(var in=Files.newInputStream(file)){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))>0;)digest.update(buffer,0,n);}
                if(!HexFormat.of().formatHex(digest.digest()).equals(manifest.getProperty(name)))throw new IOException("Restored hash mismatch: "+name);
                checked++;
            }
        }
        System.out.println("PASS: "+checked+" restored server files match the original full-backup SHA-256 manifest");
    }
}
