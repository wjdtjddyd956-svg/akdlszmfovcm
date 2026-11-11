package kr.minecraft.rpg.koreancommands;

public final class CommandTranslatorTest {
    static int assertions;
    public static void main(String[] args) {
        CommandTranslator translator = new CommandTranslator();
        expect(translator, "/돈", "/essentials:balance");
        expect(translator, "/송금 Steve 100", "/essentials:pay Steve 100");
        expect(translator, "/집", "/essentials:home default");
        expect(translator, "/집저장", "/essentials:sethome default");
        expect(translator, "/집저장 농장", "/essentials:sethome 농장");
        expect(translator, "/스폰", "/essentialsspawn:spawn");
        expect(translator, "/이동요청 Steve", "/essentials:tpa Steve");
        expect(translator, "/귓속말 Steve  안녕  \"친구야\"", "/essentials:msg Steve  안녕  \"친구야\"");
        expect(translator, "/거래소", "/quickshop-hikari:quickshop help");
        expect(translator, "/거래소 생성 100", "/quickshop-hikari:quickshop create 100");
        expect(translator, "/거래소 직원 추가 Steve", "/quickshop-hikari:quickshop staff add Steve");
        expect(translator, "/거래소 직원 삭제 Steve", "/quickshop-hikari:quickshop staff del Steve");
        expect(translator, "/거래소 이름 농산물", "/quickshop-hikari:quickshop name 농산물");
        expect(translator, "/거래소 양도 수락", "/quickshop-hikari:quickshop transferownership accept");
        expect(translator, "/권한 사용자 Steve 권한 설정 essentials.pay 참", "/luckperms:lp user Steve permission set essentials.pay true");
        expect(translator, "/땅초대 Steve", "/koreaneconomy:땅보호 초대 Steve");
        expect(translator, "/돈관리 지급 Steve 100", "/essentials:eco give Steve 100");
        expect(translator,"/땅목록","/koreaneconomy:땅보호 목록");
        expect(translator,"/땅확인","/koreaneconomy:땅보호 확인");
        expect(translator,"/땅포기 확인","/koreaneconomy:땅보호 포기 확인");
        if (!"KoreanEconomy".equals(CommandTranslator.requiredPlugin("/koreaneconomy:땅보호 초대 Steve"))) throw new AssertionError("land dependency");
        ignore(translator, "/상점");
        ignore(translator, "/일일보상");
        ignore(translator, "/돈관리자");
        contains(translator.complete("/거래소 생"), "생성");
        contains(translator.complete("/거래소 직원 추"), "추가");
        contains(translator.complete("/송"), "송금");
        if (!"Essentials".equals(CommandTranslator.requiredPlugin("/essentials:pay Steve 100"))) throw new AssertionError("dependency routing");

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

        System.out.println("PASS: " + assertions + " command translator assertions");
    }

    private static void expect(CommandTranslator translator, String input, String expected) {
        assertions++;
        CommandTranslator.Result result = translator.translate(input);
        if (!result.handled() || !expected.equals(result.command())) {
            throw new AssertionError(input + " -> " + result + ", expected " + expected);
        }
    }

    private static void ignore(CommandTranslator translator, String input) {
        assertions++;
        if (translator.translate(input).handled()) {
            throw new AssertionError(input + " should not be handled");
        }
    }

    private static void contains(java.util.List<String> values, String expected) {
        assertions++;
        if (!values.contains(expected)) {
            throw new AssertionError(values + " should contain " + expected);
        }
    }
}
