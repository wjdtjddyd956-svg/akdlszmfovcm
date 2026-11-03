package kr.minecraft.rpg.koreancommands;

public final class CommandTranslatorTest {
    public static void main(String[] args) {
        CommandTranslator translator = new CommandTranslator();

        expect(translator, "//도끼", "//wand");
        expect(translator, "//채우기 돌", "//set stone");
        expect(translator, "//채우기 50%돌,50%흙", "//set 50%stone,50%dirt");
        expect(translator, "//바꾸기 minecraft:돌[axis=y] 잔디블록", "//replace minecraft:stone[axis=y] grass_block");
        expect(translator, "/영역 생성 마을", "/rg define 마을");
        expect(translator, "/영역 설정 마을 피브이피 거부", "/rg flag 마을 pvp deny");
        expect(translator, "/영역 설정 마을 입장메시지 '마을에 오신 것을 환영합니다'", "/rg flag 마을 greeting 마을에 오신 것을 환영합니다");
        ignore(translator, "//wand");
        ignore(translator, "/rg info 마을");
        ignore(translator, "/다른명령어");
        contains(translator.complete("//도"), "도끼");
        contains(translator.complete("//채우기 잔"), "잔디블록");
        contains(translator.complete("/영역 설"), "설정");
        contains(translator.complete("/영역 설정 마을 피브"), "피브이피");
        contains(translator.complete("/영역 설정 마을 피브이피 거"), "거부");

        System.out.println("CommandTranslator tests passed");
    }

    private static void expect(CommandTranslator translator, String input, String expected) {
        CommandTranslator.Result result = translator.translate(input);
        if (!result.handled() || !expected.equals(result.command())) {
            throw new AssertionError(input + " -> " + result + ", expected " + expected);
        }
    }

    private static void ignore(CommandTranslator translator, String input) {
        if (translator.translate(input).handled()) {
            throw new AssertionError(input + " should not be handled");
        }
    }

    private static void contains(java.util.List<String> values, String expected) {
        if (!values.contains(expected)) {
            throw new AssertionError(values + " should contain " + expected);
        }
    }
}
