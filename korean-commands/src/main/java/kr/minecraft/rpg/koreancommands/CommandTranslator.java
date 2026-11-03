package kr.minecraft.rpg.koreancommands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CommandTranslator {
    record Result(boolean handled, String command) {
        static Result ignored() {
            return new Result(false, null);
        }
    }

    private static final Map<String, String> WORLD_EDIT = aliases(
            "도끼", "wand", "첫점", "pos1", "둘째점", "pos2",
            "보는첫점", "hpos1", "보는둘째점", "hpos2", "선택", "sel",
            "채우기", "set", "바꾸기", "replace", "교체", "replace",
            "벽", "walls", "테두리", "outline", "속비우기", "hollow",
            "복사", "copy", "잘라내기", "cut", "붙여넣기", "paste",
            "되돌리기", "undo", "다시하기", "redo", "이동", "move",
            "쌓기", "stack", "회전", "rotate", "뒤집기", "flip",
            "확장", "expand", "축소", "contract", "위로", "up",
            "천장", "ceil", "통과", "thru", "청크", "chunk",
            "생물군계", "setbiome", "삭제", "remove", "도움말", "help"
    );

    private static final Map<String, String> REGION = aliases(
            "생성", "define", "지정", "define", "재설정", "redefine",
            "삭제", "remove", "정보", "info", "목록", "list",
            "선택", "select", "우선순위", "setpriority", "부모", "setparent",
            "구성원추가", "addmember", "구성원삭제", "removemember",
            "소유자추가", "addowner", "소유자삭제", "removeowner",
            "설정", "flag", "플래그", "flag", "설정목록", "flags",
            "플래그목록", "flags", "이동", "tp", "우회", "bypass",
            "새로고침", "reload", "저장", "save", "불러오기", "load"
    );

    private static final Map<String, String> FLAGS = aliases(
            "피브이피", "pvp", "전투", "pvp", "건축", "build",
            "블록파괴", "block-break", "블록설치", "block-place",
            "상호작용", "interact", "상자열기", "chest-access",
            "사용", "use", "입장", "entry", "퇴장", "exit",
            "입장메시지", "greeting", "퇴장메시지", "farewell",
            "몬스터생성", "mob-spawning", "동물생성", "deny-spawn",
            "티엔티", "tnt", "폭발", "other-explosion", "불번짐", "fire-spread",
            "용암불", "lava-fire", "번개", "lightning", "무적", "invincible",
            "낙하피해", "fall-damage", "굶주림", "hunger-drain",
            "체력회복", "heal-amount", "게임모드", "game-mode",
            "순간이동", "teleport", "스폰", "spawn"
    );

    private static final Map<String, String> VALUES = aliases(
            "허용", "allow", "거부", "deny", "기본", "none", "없음", "none",
            "켜기", "allow", "끄기", "deny", "참", "true", "거짓", "false",
            "생존", "survival", "창작", "creative", "모험", "adventure",
            "관전자", "spectator", "모두", "all", "구성원", "members",
            "비구성원", "nonmembers", "소유자", "owners", "비소유자", "nonowners"
    );

    private static final Map<String, String> BLOCKS = aliases(
            "공기", "air", "돌", "stone", "조약돌", "cobblestone",
            "흙", "dirt", "잔디블록", "grass_block", "모래", "sand",
            "자갈", "gravel", "기반암", "bedrock", "유리", "glass",
            "참나무판자", "oak_planks", "참나무원목", "oak_log",
            "가문비나무판자", "spruce_planks", "가문비나무원목", "spruce_log",
            "자작나무판자", "birch_planks", "자작나무원목", "birch_log",
            "정글나무판자", "jungle_planks", "정글나무원목", "jungle_log",
            "아카시아판자", "acacia_planks", "아카시아원목", "acacia_log",
            "짙은참나무판자", "dark_oak_planks", "짙은참나무원목", "dark_oak_log",
            "석재벽돌", "stone_bricks", "금블록", "gold_block",
            "철블록", "iron_block", "다이아몬드블록", "diamond_block",
            "에메랄드블록", "emerald_block", "물", "water", "용암", "lava",
            "눈", "snow", "눈블록", "snow_block", "얼음", "ice",
            "발광석", "glowstone", "횃불", "torch", "잔디", "short_grass"
    );

    Result translate(String message) {
        if (message == null || message.length() < 2 || message.charAt(0) != '/') {
            return Result.ignored();
        }
        if (message.startsWith("//")) {
            return translateWorldEdit(message.substring(2));
        }
        List<String> tokens = tokenize(message.substring(1));
        if (tokens.isEmpty() || !tokens.get(0).equals("영역")) {
            return Result.ignored();
        }
        return translateRegion(tokens);
    }

    List<String> complete(String buffer) {
        if (buffer == null) {
            return List.of();
        }
        if (buffer.startsWith("//")) {
            String body = buffer.substring(2);
            List<String> tokens = tokenize(body);
            if (!body.contains(" ")) {
                return matches(WORLD_EDIT.keySet(), body);
            }
            String command = tokens.isEmpty() ? "" : tokens.get(0);
            if (command.equals("채우기") || command.equals("바꾸기") || command.equals("교체")
                    || command.equals("벽") || command.equals("테두리") || command.equals("속비우기")) {
                return matches(BLOCKS.keySet(), currentToken(body));
            }
            return List.of();
        }
        if (!buffer.startsWith("/영역")) {
            return List.of();
        }
        String body = buffer.substring(1);
        List<String> tokens = tokenize(body);
        if (tokens.size() <= 1) {
            return matches(REGION.keySet(), "");
        }
        if (tokens.size() == 2 && !body.endsWith(" ")) {
            return matches(REGION.keySet(), tokens.get(1));
        }
        if (tokens.size() >= 4 && (tokens.get(1).equals("설정") || tokens.get(1).equals("플래그"))) {
            if (tokens.size() == 4 && !body.endsWith(" ")) {
                return matches(FLAGS.keySet(), tokens.get(3));
            }
            if (tokens.size() == 3 || (tokens.size() == 4 && body.endsWith(" "))) {
                return matches(FLAGS.keySet(), "");
            }
            return matches(VALUES.keySet(), currentToken(body));
        }
        return List.of();
    }

    private Result translateWorldEdit(String input) {
        List<String> tokens = tokenize(input);
        if (tokens.isEmpty()) {
            return Result.ignored();
        }
        String translated = WORLD_EDIT.get(tokens.get(0));
        if (translated == null) {
            return Result.ignored();
        }
        tokens.set(0, translated);
        translateWorldEditArguments(tokens, translated);
        return new Result(true, "//" + join(tokens));
    }

    private Result translateRegion(List<String> tokens) {
        if (tokens.size() == 1) {
            return new Result(true, "/rg help");
        }
        String action = REGION.get(tokens.get(1));
        if (action == null) {
            return Result.ignored();
        }
        List<String> output = new ArrayList<>();
        output.add("rg");
        output.add(action);
        output.addAll(tokens.subList(2, tokens.size()));
        if (action.equals("flag") && output.size() >= 4) {
            output.set(3, FLAGS.getOrDefault(output.get(3), output.get(3)));
            if (output.size() >= 5) {
                output.set(4, VALUES.getOrDefault(output.get(4), output.get(4)));
            }
            for (int i = 5; i < output.size(); i++) {
                output.set(i, VALUES.getOrDefault(output.get(i), output.get(i)));
            }
        }
        return new Result(true, "/" + join(output));
    }

    private void translateWorldEditArguments(List<String> tokens, String command) {
        if (tokens.size() < 2) {
            return;
        }
        switch (command) {
            case "set", "walls", "outline", "hollow" ->
                    tokens.set(1, translatePattern(tokens.get(1)));
            case "replace" -> {
                tokens.set(1, translatePattern(tokens.get(1)));
                if (tokens.size() >= 3) {
                    tokens.set(2, translatePattern(tokens.get(2)));
                }
            }
            default -> {
            }
        }
    }

    private String translatePattern(String pattern) {
        String[] parts = pattern.split(",", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            int prefixEnd = 0;
            while (prefixEnd < part.length()
                    && (Character.isDigit(part.charAt(prefixEnd)) || part.charAt(prefixEnd) == '%')) {
                prefixEnd++;
            }
            String prefix = part.substring(0, prefixEnd);
            String block = part.substring(prefixEnd);
            int stateStart = block.indexOf('[');
            String state = stateStart >= 0 ? block.substring(stateStart) : "";
            String base = stateStart >= 0 ? block.substring(0, stateStart) : block;
            String namespace = base.startsWith("minecraft:") ? "minecraft:" : "";
            String key = namespace.isEmpty() ? base : base.substring("minecraft:".length());
            parts[i] = prefix + namespace + BLOCKS.getOrDefault(key, key) + state;
        }
        return String.join(",", parts);
    }

    private static List<String> tokenize(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '\'' || c == '"')) {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (Character.isWhitespace(c) && !quoted) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private static String join(List<String> tokens) {
        return String.join(" ", tokens);
    }

    private static String currentToken(String input) {
        if (input.endsWith(" ")) {
            return "";
        }
        int separator = input.lastIndexOf(' ');
        return separator < 0 ? input : input.substring(separator + 1);
    }

    private static List<String> matches(Iterable<String> aliases, String prefix) {
        List<String> result = new ArrayList<>();
        for (String alias : aliases) {
            if (alias.startsWith(prefix)) {
                result.add(alias);
            }
        }
        return result.stream().sorted().toList();
    }

    private static Map<String, String> aliases(String... entries) {
        Map<String, String> result = new LinkedHashMap<>();
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("별칭은 한글과 영문 쌍으로 지정해야 합니다.");
        }
        for (int i = 0; i < entries.length; i += 2) {
            result.put(entries[i].toLowerCase(Locale.ROOT), entries[i + 1]);
        }
        return Map.copyOf(result);
    }
}
