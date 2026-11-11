package kr.minecraft.survival.economy;

import org.bukkit.Material;

enum ShopCategory {
    ALL("전체", "잡화 상점"), CROPS("농산물", "농산물 상점"), MINERALS("광물", "광물 상점"), MATERIALS("재료", "재료 상점"),
    QUEST("일일임무", "일일 임무");
    final String label, title;
    ShopCategory(String label,String title){this.label=label;this.title=title;}
    static ShopCategory parse(String input){for(ShopCategory c:values())if(c.label.equals(input))return c;return null;}
    boolean includes(Material type){return switch(this){
        case ALL -> true;
        case CROPS -> type==Material.WHEAT||type==Material.CARROT||type==Material.POTATO;
        case MINERALS -> type==Material.COAL||type==Material.IRON_INGOT||type==Material.DIAMOND;
        case MATERIALS -> type==Material.COBBLESTONE||type==Material.OAK_LOG;
        case QUEST -> false;
    };}
}
