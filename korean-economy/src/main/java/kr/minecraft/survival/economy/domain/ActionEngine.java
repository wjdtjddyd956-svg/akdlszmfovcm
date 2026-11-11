package kr.minecraft.survival.economy.domain;

import java.util.UUID;
import java.util.function.Consumer;

/** Coordinates explicit compensation, never pretends Vault and player data form one DB transaction. */
public final class ActionEngine {
    public enum Direction {CREDIT,DEBIT,NONE}
    public enum Status {DONE,REJECTED,REVIEW}
    public record Outcome(Status status,String receipt,String message){}
    public interface Wallet {boolean credit(UUID player,long cents);boolean debit(UUID player,long cents);}
    public interface Change {void apply();void restore();}
    public static final Change NO_ITEMS=new Change(){public void apply(){}public void restore(){}};
    private final StateStore store;
    private final Wallet wallet;
    private final Consumer<Throwable> errors;
    private boolean healthy=true,executing;
    public ActionEngine(StateStore store,Wallet wallet,Consumer<Throwable> errors){this.store=store;this.wallet=wallet;this.errors=errors;}
    public synchronized boolean healthy(){return healthy;}
    public synchronized Outcome execute(UUID player,String kind,String reference,long cents,Direction direction,String payload,StateStore.Quota quota,Change items,Runnable databaseEffect){
        checkReady();StateStore.Receipt receipt=store.reserve(player,kind,reference,cents,payload,quota);
        return finish(receipt,direction,items,databaseEffect);
    }
    public synchronized Outcome reserved(StateStore.Receipt receipt,Direction direction,Change items,Runnable databaseEffect){checkReady();return finish(receipt,direction,items,databaseEffect);}
    private void checkReady(){if(!healthy)throw new StateStore.Denied("저장소 확인이 필요해 경제 기능을 일시 중지했습니다. 운영자에게 문의하세요.");if(executing)throw new StateStore.Denied("다른 거래를 처리 중입니다. 잠시 후 다시 시도하세요.");}
    private Outcome finish(StateStore.Receipt receipt,Direction direction,Change items,Runnable databaseEffect){
        if(!store.receipt(receipt.id()).state().equals("PENDING"))throw new StateStore.Denied("이미 처리한 거래입니다.");
        UUID actor=UUID.fromString(receipt.actor());executing=true;
        try {
            boolean paid;
            try {paid=transfer(actor,receipt.cents(),direction);}
            catch(RuntimeException e){return uncertain(receipt,"돈 처리 결과 확인 필요",e);}
            if(!paid){
                try {store.reject(receipt.id(),"경제 제공자가 거래를 거절했습니다.");}
                catch(RuntimeException e){return uncertain(receipt,"거절 기록 저장 실패",e);}
                return new Outcome(Status.REJECTED,receipt.id(),"잔액이 부족하거나 돈 관리 기능이 거래를 거절했습니다.");
            }
            try {items.apply();}
            catch(RuntimeException applyFailure){
                try {items.restore();}
                catch(RuntimeException restoreFailure){applyFailure.addSuppressed(restoreFailure);return uncertain(receipt,"아이템 복구 확인 필요",applyFailure);}
                boolean refunded;
                try {refunded=transfer(actor,receipt.cents(),reverse(direction));}
                catch(RuntimeException refundFailure){applyFailure.addSuppressed(refundFailure);return uncertain(receipt,"환불 결과 확인 필요",applyFailure);}
                if(!refunded)return uncertain(receipt,"경제 제공자가 환불을 거절했습니다.",applyFailure);
                try {store.reject(receipt.id(),"아이템 적용 실패 후 원본·잔액 복구 완료");}
                catch(RuntimeException e){applyFailure.addSuppressed(e);return uncertain(receipt,"복구 결과 저장 실패",applyFailure);}
                errors.accept(applyFailure);return new Outcome(Status.REJECTED,receipt.id(),"거래를 완료하지 못해 원래 아이템과 잔액으로 되돌렸습니다.");
            }
            try {store.complete(receipt.id(),databaseEffect);}
            catch(RuntimeException e){return uncertain(receipt,"최종 기록 저장 실패: 적용 결과 수동 확인 필요",e);}
            return new Outcome(Status.DONE,receipt.id(),"처리했습니다.");
        }finally{executing=false;}
    }
    private boolean transfer(UUID actor,long cents,Direction direction){
        if(direction==Direction.NONE||cents==0)return true;
        return direction==Direction.CREDIT?wallet.credit(actor,cents):wallet.debit(actor,cents);
    }
    private static Direction reverse(Direction direction){return direction==Direction.CREDIT?Direction.DEBIT:direction==Direction.DEBIT?Direction.CREDIT:Direction.NONE;}
    private Outcome uncertain(StateStore.Receipt receipt,String note,RuntimeException cause){
        try {store.review(receipt.id(),note);}
        catch(RuntimeException failure){healthy=false;cause.addSuppressed(failure);}
        // A database completion error might itself mean the connection is unusable.
        if(cause instanceof StateStore.StorageFailure)healthy=false;
        errors.accept(new IllegalStateException(note+" | "+receipt.id(),cause));
        return new Outcome(Status.REVIEW,receipt.id(),"거래 확인이 필요합니다. 자동으로 다시 지급하지 않습니다. 운영자에게 번호 "+receipt.id().substring(0,8)+"을 알려주세요.");
    }
}
