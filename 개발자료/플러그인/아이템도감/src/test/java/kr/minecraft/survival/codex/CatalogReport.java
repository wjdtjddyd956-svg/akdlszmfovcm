package kr.minecraft.survival.codex;
import java.nio.file.*;
import java.util.*;

/** Runs with the production JAR alone: exact table, caps, bundled SQLite, cold reopen. */
public final class CatalogReport {
    public static void main(String[] args)throws Exception {
        Catalog c=new Catalog(CatalogReport.class.getResourceAsStream("/catalog.tsv"));
        for(var category:c.categories)for(var e:category.entries())System.out.println(String.join("\t","ENTRY",category.name(),e.name(),e.rarity(),category.stat().label+" "+category.stat().format(c.entryUnits(e))));
        for(var award:c.achievements){List<String> effects=new ArrayList<>();for(var stat:Catalog.Stat.values())if(award.reward().getOrDefault(stat,0)>0)effects.add(stat.label+" "+stat.format(award.reward().get(stat)));System.out.println(String.join("\t","AWARD",award.name(),award.description(),String.join(", ",effects)));}
        for(var stat:Catalog.Stat.values())if(c.bonuses(c.entries.keySet()).get(stat)!=stat.cap)throw new AssertionError(stat);
        UUID id=UUID.randomUUID();Path file=Path.of(args[0]);String pending;
        try(var db=new CodexStore(file)){db.player(id,"Artifact");db.finish(db.reserve(id,"oak_log",0,2,"b","a").id(),c,false);pending=db.reserve(id,"diamond",0,2,"b","a").id();db.snapshot(Path.of(file+".snapshot"));}
        try(var db=new CodexStore(file)){if(!db.registered(id).equals(Set.of("oak_log"))||!db.receipt(pending).state().equals("REVIEW"))throw new AssertionError("restart journal");}
        try(var db=new CodexStore(Path.of(file+".snapshot"))){if(!db.registered(id).equals(Set.of("oak_log")))throw new AssertionError("snapshot");}
        System.out.println("PACKAGED_SQLITE_OK");
    }
}
