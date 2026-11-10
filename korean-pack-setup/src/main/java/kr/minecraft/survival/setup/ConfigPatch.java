package kr.minecraft.survival.setup;

import java.util.*;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

/** Patch only named settings; reject ambiguous or malformed YAML before writing. */
final class ConfigPatch {
    private static Yaml yaml() {
        LoaderOptions loader=new LoaderOptions();loader.setAllowDuplicateKeys(false);
        DumperOptions dumper=new DumperOptions();
        dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);dumper.setAllowUnicode(true);dumper.setIndent(2);
        return new Yaml(new SafeConstructor(loader),new Representer(dumper),dumper,loader);
    }
    @SuppressWarnings("unchecked") static Map<String,Object> read(String source) {
        Object value=yaml().load(source);
        if(value==null)return new LinkedHashMap<>();
        if(!(value instanceof Map))throw new IllegalArgumentException("설정 파일의 최상위 항목은 목록이 아닌 키·값이어야 합니다.");
        return (Map<String,Object>)value;
    }
    @SuppressWarnings("unchecked") private static void set(Map<String,Object> map,String path,Object value) {
        String[] parts=path.split("\\.");
        for(int i=0;i<parts.length-1;i++) {
            Object next=map.get(parts[i]);
            if(next==null) {next=new LinkedHashMap<String,Object>();map.put(parts[i],next);}
            if(!(next instanceof Map))throw new IllegalArgumentException("설정 항목이 올바른 구역이 아닙니다: "+parts[i]);
            map=(Map<String,Object>)next;
        }
        map.put(parts[parts.length-1],value);
    }
    static String essentials(String source) {
        Map<String,Object> map=read(source);
        set(map,"locale","ko");set(map,"per-player-locale",false);
        set(map,"starting-balance",1000);set(map,"currency-symbol","원");set(map,"currency-symbol-suffix",true);
        return yaml().dump(map);
    }
    static String quickshop(String source) {
        Map<String,Object> map=read(source);
        set(map,"game-language","ko_kr");set(map,"enabled-languages",new ArrayList<>(List.of("ko_kr")));
        set(map,"use-crowdin-ota",false);set(map,"shop.display-items",true);set(map,"shop.display-type",3);
        return yaml().dump(map);
    }
}
