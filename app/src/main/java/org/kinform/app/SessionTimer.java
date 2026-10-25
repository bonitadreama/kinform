package org.kinform.app;

/** Main-thread timer with renewable, bounded wake leases and cancelable callbacks. */
final class SessionTimer {
    interface Scheduler {void post(Runnable task,long delay);void remove(Runnable task);}
    interface Lease {void acquire(long timeout);void release();}
    static final long LEASE_MS=120_000L,RENEW_MS=60_000L;
    private final Scheduler scheduler;
    private final Lease lease;
    private Runnable expiry,renew;
    private long generation;
    private boolean running;
    SessionTimer(Scheduler scheduler,Lease lease){this.scheduler=scheduler;this.lease=lease;}
    void start(int minutes,Runnable onExpired,Runnable onLeaseFailure){
        long duration=SessionDuration.millis(minutes);
        stop();running=true;final long current=generation;
        try{lease.acquire(LEASE_MS);}catch(RuntimeException e){stop();throw e;}
        renew=new Runnable(){public void run(){
            if(!running || generation!=current)return;
            try{lease.acquire(LEASE_MS);}catch(RuntimeException e){stop();onLeaseFailure.run();return;}
            scheduler.post(this,RENEW_MS);
        }};
        scheduler.post(renew,RENEW_MS);
        if(duration>0){
            expiry=()->{if(!running || generation!=current)return;stop();onExpired.run();};
            scheduler.post(expiry,duration);
        }
    }
    void stop(){
        generation++;
        if(expiry!=null)scheduler.remove(expiry);
        if(renew!=null)scheduler.remove(renew);
        expiry=null;renew=null;
        if(running){running=false;lease.release();}
    }
}
