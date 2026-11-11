package kr.minecraft.survival.bridge;

import java.util.*;

/** Immutable display values: safe for PlaceholderAPI's asynchronous consumers. */
public record DisplayProfile(Map<String,String> values) {
    public DisplayProfile { values=Map.copyOf(values); }
    public String value(String key){return values.get(key);}
    public static String won(double value){
        if(!Double.isFinite(value)||value<0)return "확인 중";
        return String.format(Locale.KOREA,"%,.2f",value).replaceAll("\\.00$","")+"원";
    }
    public static String rank(String group){return switch(group){case "default"->"주민";case "resident"->"단골";case "admin"->"운영자";default->"이용자";};}
}
