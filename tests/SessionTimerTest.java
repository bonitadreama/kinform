package org.kinform.app;

import java.util.*;

public final class SessionTimerTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static final class Clock implements SessionTimer.Scheduler {
        long now;final Map<Runnable,Long> tasks=new LinkedHashMap<>();
        public void post(Runnable task,long delay){tasks.put(task,now+delay);}
        public void remove(Runnable task){tasks.remove(task);}
        void advance(long delay){long end=now+delay;
            while(true){Runnable next=null;long at=Long.MAX_VALUE;
                for(Map.Entry<Runnable,Long> e:tasks.entrySet())if(e.getValue()<at){next=e.getKey();at=e.getValue();}
                if(next==null || at>end)break;
                now=at;tasks.remove(next);next.run();
            }now=end;
        }
    }
    static final class Wake implements SessionTimer.Lease {
        boolean held,fail;int acquired,released;
        public void acquire(long timeout){check(timeout==120_000L,"bounded wake lease");if(fail)throw new IllegalStateException();held=true;acquired++;}
        public void release(){held=false;released++;}
    }
    public static void main(String[] args){
        check(SessionDuration.restored(-1)==5 && SessionDuration.restored(Integer.MAX_VALUE)==5,"invalid persisted values use original default");
        check(SessionDuration.restored(0)==0,"Unlimited survives restore");
        for(int minutes:SessionDuration.choices()){
            Clock clock=new Clock();Wake wake=new Wake();SessionTimer timer=new SessionTimer(clock,wake);int[] expired={0};
            timer.start(minutes,()->expired[0]++,()->{throw new AssertionError("unexpected wake failure");});
            if(minutes==0){
                clock.advance(24*60*60_000L);check(expired[0]==0 && wake.held,"Unlimited remains active beyond original cap and a day");
                check(clock.tasks.size()==1,"only one renewal remains queued");
            }else{
                clock.advance(minutes*60_000L-1);check(expired[0]==0 && wake.held,"no early stop");
                clock.advance(1);check(expired[0]==1 && !wake.held,"selected deadline stops exactly once");
                check(clock.tasks.isEmpty(),"deadline removes renewal");
                clock.advance(120_000);check(expired[0]==1,"no duplicate timeout");
            }
            timer.stop();check(!wake.held && clock.tasks.isEmpty(),"Stop clears lease and callbacks");
        }
        Clock clock=new Clock();Wake wake=new Wake();SessionTimer timer=new SessionTimer(clock,wake);int[] expired={0},failed={0};
        timer.start(5,()->expired[0]++,()->failed[0]++);
        List<Runnable> stale=new ArrayList<>(clock.tasks.keySet());
        timer.stop();timer.stop();check(wake.released==1,"Stop is idempotent");
        timer.start(0,()->expired[0]++,()->failed[0]++);
        for(Runnable task:stale)task.run();
        clock.advance(300_001);check(expired[0]==0 && wake.held,"old deadline cannot end new unlimited session");
        wake.fail=true;clock.advance(60_000);check(failed[0]==1 && !wake.held && clock.tasks.isEmpty(),"renewal failure stops without leaking callbacks");
        boolean rejected=false;try{timer.start(-1,()->{},()->{});}catch(IllegalArgumentException e){rejected=true;}
        check(rejected && clock.tasks.isEmpty(),"invalid duration rejected before scheduling");
        Wake bad=new Wake();bad.fail=true;SessionTimer broken=new SessionTimer(clock,bad);
        try{broken.start(0,()->{},()->{});throw new AssertionError("initial lease failure must propagate");}catch(IllegalStateException expected){}
        check(clock.tasks.isEmpty() && !bad.held,"initial failure cleans up");
        System.out.println("Session duration checks passed: finite deadlines, Unlimited, renewal, Stop, stale events, defaults and failures");
    }
}
