package org.kinform.app;

/** App-owned duration choices; zero means no local deadline, not a provider guarantee. */
final class SessionDuration {
    static final int DEFAULT_MINUTES=5;
    static int[] choices(){return new int[]{5,15,30,60,0};}
    static boolean valid(int minutes){for(int choice:choices())if(choice==minutes)return true;return false;}
    static int restored(int minutes){return valid(minutes)?minutes:DEFAULT_MINUTES;}
    static String label(int minutes){return minutes==0?"Unlimited":minutes+" minutes";}
    static long millis(int minutes){if(!valid(minutes))throw new IllegalArgumentException("Invalid session duration");return minutes*60_000L;}
}
