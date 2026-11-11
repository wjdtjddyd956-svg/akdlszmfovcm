package kr.minecraft.survival.economy.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Persist money in integer cents, never floating-point database values. */
public final class Money {
    private Money(){}
    public static long cents(double value){
        if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("금액은 0 이상의 유한한 수여야 합니다.");
        long result=BigDecimal.valueOf(value).setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        if(result>100_000_000_000L)throw new IllegalArgumentException("허용 금액을 초과했습니다.");
        return result;
    }
    public static double value(long cents){if(cents<0)throw new IllegalArgumentException("음수 금액");return BigDecimal.valueOf(cents,2).doubleValue();}
    public static long total(long unit,int amount){if(amount<1||amount>2304)throw new IllegalArgumentException("거래 수량 오류");return Math.multiplyExact(unit,amount);}
    public static String display(long cents){return BigDecimal.valueOf(cents,2).stripTrailingZeros().toPlainString()+"원";}
}
