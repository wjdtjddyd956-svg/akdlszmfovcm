package kr.minecraft.survival.economy.domain;

import java.nio.file.*;
import java.util.*;

public final class ActionTest {
    static int checks;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static class Wallet implements ActionEngine.Wallet {
        long balance=10000;boolean refuseCredit,refuseDebit,throwCredit,throwDebit;
        public boolean credit(UUID player,long cents){if(throwCredit){balance+=cents;throw new IllegalStateException("provider threw after commit");}if(refuseCredit)return false;balance+=cents;return true;}
        public boolean debit(UUID player,long cents){if(throwDebit)throw new IllegalStateException("uncertain debit");if(refuseDebit||balance<cents)return false;balance-=cents;return true;}
    }
    static class Inventory implements ActionEngine.Change {
        int count=2,after;boolean failApply,failRestore;
        Inventory(int after){this.after=after;}
        public void apply(){count=after;if(failApply)throw new IllegalStateException("inventory setter failure");}
        public void restore(){if(failRestore)throw new IllegalStateException("inventory rollback failure");count=2;}
    }
    static UUID user(StateStore s){UUID id=UUID.randomUUID();s.player(id,id.toString());return id;}
    public static void main(String[] args)throws Exception {
        try(StateStore s=new StateStore(Files.createTempDirectory("tycoon-action-").resolve("db.sqlite"))){
            List<Throwable> errors=new ArrayList<>();Wallet w=new Wallet();ActionEngine engine=new ActionEngine(s,w,errors::add);
            UUID buyer=user(s);Inventory buy=new Inventory(3);
            var bought=engine.execute(buyer,"BUY","buy:1",1000,ActionEngine.Direction.DEBIT,"",null,buy,()->{});
            ok(bought.status()==ActionEngine.Status.DONE&&w.balance==9000&&buy.count==3,"purchase moves exact money and inventory");
            UUID poor=user(s);Inventory untouched=new Inventory(3);w.balance=0;
            var refused=engine.execute(poor,"BUY","buy:2",1000,ActionEngine.Direction.DEBIT,"",null,untouched,()->{});
            ok(refused.status()==ActionEngine.Status.REJECTED&&untouched.count==2,"failed debit does not grant items");
            UUID seller=user(s);Inventory sale=new Inventory(1);w.refuseCredit=true;
            var rejected=engine.execute(seller,"SELL","sell:1",1000,ActionEngine.Direction.CREDIT,"",new StateStore.Quota("day","wheat",1,1),sale,()->{});
            ok(rejected.status()==ActionEngine.Status.REJECTED&&sale.count==2&&s.used(seller,"day","wheat")==0,"failed credit preserves items and restores quota");w.refuseCredit=false;
            UUID compensate=user(s);Inventory broken=new Inventory(1);broken.failApply=true;w.balance=5000;
            var compensated=engine.execute(compensate,"SELL","sell:2",1000,ActionEngine.Direction.CREDIT,"",new StateStore.Quota("day","wheat",1,1),broken,()->{});
            ok(compensated.status()==ActionEngine.Status.REJECTED&&broken.count==2&&w.balance==5000,"partial inventory failure compensates money and items");
            ok(s.used(compensate,"day","wheat")==0,"compensated failure releases cap");
            UUID uncertain=user(s);w.throwCredit=true;Inventory unissued=new Inventory(1);
            var unknown=engine.execute(uncertain,"SELL","sell:3",1000,ActionEngine.Direction.CREDIT,"",new StateStore.Quota("day","wheat",1,1),unissued,()->{});
            ok(unknown.status()==ActionEngine.Status.REVIEW&&unissued.count==2&&s.unresolved(uncertain),"provider exception never guesses success or retries");
            ok(s.used(uncertain,"day","wheat")==1,"uncertain credit keeps cap reserved");w.throwCredit=false;
            try{engine.execute(uncertain,"SELL","sell:4",1000,ActionEngine.Direction.CREDIT,"",null,new Inventory(1),()->{});throw new AssertionError();}catch(StateStore.Denied expected){checks++;}
            UUID rollback=user(s);w.refuseDebit=true;Inventory restore=new Inventory(1);restore.failApply=true;
            var refund=engine.execute(rollback,"SELL","sell:5",1000,ActionEngine.Direction.CREDIT,"",null,restore,()->{});
            ok(refund.status()==ActionEngine.Status.REVIEW&&s.unresolved(rollback),"rejected refund needs manual settlement");w.refuseDebit=false;
            UUID storage=user(s);Inventory committed=new Inventory(3);w.balance=10000;
            var failure=engine.execute(storage,"BUY","buy:3",1000,ActionEngine.Direction.DEBIT,"",null,committed,()->{throw new IllegalStateException("database effect failed");});
            ok(failure.status()==ActionEngine.Status.REVIEW&&committed.count==3&&w.balance==9000,"completion uncertainty does not reverse an already applied trade blindly");
            UUID repeat=user(s);StateStore.Receipt receipt=s.reserve(repeat,"QUEST","quest:once",1000,"",null);
            var once=engine.reserved(receipt,ActionEngine.Direction.CREDIT,ActionEngine.NO_ITEMS,()->{});long balance=w.balance;
            ok(once.status()==ActionEngine.Status.DONE,"reserved transaction can complete");
            try{engine.reserved(receipt,ActionEngine.Direction.CREDIT,ActionEngine.NO_ITEMS,()->{});throw new AssertionError();}catch(StateStore.Denied expected){checks++;}
            ok(w.balance==balance,"same receipt cannot credit twice");
            UUID craft=user(s);Inventory craftItems=new Inventory(4);long before=w.balance;
            var crafted=engine.execute(craft,"COOK","cook:1",0,ActionEngine.Direction.NONE,"",null,craftItems,()->{});
            ok(crafted.status()==ActionEngine.Status.DONE&&craftItems.count==4&&w.balance==before,"zero-currency item action is journaled without a wallet call");
            ok(!errors.isEmpty(),"compensation and review errors reported to operator log");
        }
        System.out.println("PASS: "+checks+" action fault-injection assertions");
    }
}
