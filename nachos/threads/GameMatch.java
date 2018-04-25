package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

/**
 * A <i>GameMatch</i> groups together player threads of the same
 * ability into fixed-sized groups to play matches with each other.
 * Implement the class <i>GameMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into groups.
 */
public class GameMatch {
    
    /* Three levels of player ability. */
    public static final int abilityBeginner = 1,
	abilityIntermediate = 2,
	abilityExpert = 3;

    // Number of players required to begin match
    private int matchSize;

    // Conditions and locks to hold players for each match type
    Condition beginnerMatch,
        intermediateMatch,
        expertMatch,
        fallthroughCondition;

    Lock playLock;

    // Holds number of player threads waiting in respective Conditions
    private int numBeginners,
        numIntermediates,
        numExperts,
        matchNum,
        beginnerMatchNum,
        intermediateMatchNum,
        expertMatchNum;

    private boolean fallthrough;

    /**
     * Allocate a new GameMatch specifying the number of player
     * threads of the same ability required to form a match.  Your
     * implementation may assume this number is always greater than zero.
     */
    public GameMatch (int numPlayersInMatch) {

        matchSize = numPlayersInMatch;

        playLock = new Lock();


        beginnerMatch = new Condition(playLock);

        intermediateMatch = new Condition(playLock);

        expertMatch = new Condition(playLock);

        numBeginners = 0;
        numIntermediates = 0;
        numExperts = 0;

        matchNum = 1;

        beginnerMatchNum = -2;
        expertMatchNum = -2;
        intermediateMatchNum = -2;

        fallthrough = false;
        fallthroughCondition = new Condition(playLock);
    }

    /**
     * Wait for the required number of player threads of the same
     * ability to form a game match, and only return when a game match
     * is formed.  Many matches may be formed over time, but any one
     * player thread can be assigned to only one match.
     *
     * Returns the match number of the formed match.  The first match
     * returned has match number 1, and every subsequent match
     * increments the match number by one, independent of ability.  No
     * two matches should have the same match number, match numbers
     * should be strictly monotonically increasing, and there should
     * be no gaps between match numbers.
     * 
     * @param ability should be one of abilityBeginner, abilityIntermediate,
     * or abilityExpert; return -1 otherwise.
     */
    public int play (int ability) {

        playLock.acquire();
        while(fallthrough){ fallthroughCondition.sleep();}

        switch(ability){
            case(abilityBeginner):
                numBeginners++;
                if(numBeginners == matchSize) {
                    beginnerMatchNum = matchNum;
                    matchNum++;
                    fallthrough = true;
                    beginnerMatch.wakeAll();
                } else {
                    beginnerMatch.sleep();
                }
                numBeginners--;
                if(numBeginners == 0){
                    fallthrough = false;
                    fallthroughCondition.wakeAll();
                }
                return unlockAndReturn(playLock,beginnerMatchNum);

            case(abilityIntermediate):
                numIntermediates++;
                if(numIntermediates == matchSize){
                    intermediateMatchNum = matchNum;
                    matchNum++;
                    fallthrough = true;
                    intermediateMatch.wakeAll();
                } else {
                    intermediateMatch.sleep();
                }
                numIntermediates--;
                if(numIntermediates == 0){
                    fallthrough = false;
                    fallthroughCondition.wakeAll();
                }
                return unlockAndReturn(playLock,intermediateMatchNum);

            case(abilityExpert):
                numExperts++;
                if(numExperts == matchSize){
                    expertMatchNum = matchNum;
                    matchNum++;
                    fallthrough = true;
                    expertMatch.wakeAll();
                } else {
                    expertMatch.sleep();
                }
                numExperts--;
                if(numExperts == 0){
                    fallthrough = false;
                    fallthroughCondition.wakeAll();
                }

                return unlockAndReturn(playLock,expertMatchNum);

            default:
                playLock.release();
                return -1;
        }
    }

    private int unlockAndReturn(Lock cvLock,int value){
        cvLock.release();
        return value;
    }

    /**
     * Invoked in ThreadedKernel to run all test methods defined below
     */
    public static void selfTest(){
        playTestUnknownAbility();
//        playTestBeginnerMatch();
        playTestBeginnerMatch2();
        playTestIntermediateMatch2();
        matchTest4();
    }
    /**
     * Test method for the fail case of play()
     *
     * If play() is called with an unknown ability value, player1's call to play() should return -1
     */
    public static void playTestUnknownAbility(){
        GameMatch game = new GameMatch(2);

        System.out.println("Test: playTestUnknownAbility");
        KThread player1 = new KThread(new Runnable(){
            public void run(){
                System.out.println("Return value should be -1: " + game.play(0));
            }
        });

        player1.setName("player1").fork();
        player1.join();
    }

    /**
     * Tests that beginner matches are set up correctly
     */
    public static void playTestBeginnerMatch2() {
        int numPlayers = 3;
        GameMatch game = new GameMatch(numPlayers);
        System.out.println("Test: playTestBeginnerMatch\n" +
                "Verify that all players are in the same match, and that all players have queued before any player joins\n" +
                "Note that the order in which players join is not guaranteed");
        LinkedList<KThread> players = new LinkedList<>();
        for (int i = 0; i < numPlayers; i++) {
            int myNum = i;
            players.add(new KThread(new Runnable(){
                public void run(){
                    System.out.println("Player " + myNum + " entering queue");
                    System.out.println("Player " + myNum + " joining game " + game.play(abilityBeginner));
                }
            }));
        }
        for (int i = 0; i < players.size(); i++){
            players.get(i).fork();
        }
        for (int i = 0; i < players.size(); i++){
            players.get(i).join();
        }

    }

    /**
     * Tests that intermediate matches are set up correctly
     */
    public static void playTestIntermediateMatch2() {
        int matchSize = 2;
        int numPlayers = matchSize * 10;
        GameMatch game = new GameMatch(matchSize);
        System.out.println("Test: playTestIntermediateMatch\n" +
                "Verify that all players are in the same match, and that all players have queued before any player joins\n" +
                "Note that the order in which players join is not guaranteed");
        LinkedList<KThread> players = new LinkedList<>();
        for (int i = 1; i < numPlayers + 1; i++) {
            int myNum = i;
            players.add(new KThread(new Runnable(){
                public void run(){
                    System.out.println("Player " + myNum + " entering queue");
                    System.out.println("Player " + myNum + " joining game " + game.play(abilityIntermediate));
                }
            }));
        }
        for (int i = 0; i < players.size(); i++){
            players.get(i).fork();
        }
//        players.get(1).join();
        for (int i = 0; i < players.size(); i++){
            players.get(i).join();
        }
    }
    // Place GameMatch test code inside of the GameMatch class.

    public static void matchTest4 () {
        final GameMatch match = new GameMatch(2);

        // Instantiate the threads
        KThread beg1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg1 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg1.setName("B1");

        KThread beg2 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg2 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg2.setName("B2");

        KThread int1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                Lib.assertNotReached("int1 should not have matched!");
            }
        });
        int1.setName("I1");

        KThread exp1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                Lib.assertNotReached("exp1 should not have matched!");
            }
        });
        exp1.setName("E1");

        // Run the threads.  The beginner threads should successfully
        // form a match, the other threads should not.  The outcome
        // should be the same independent of the order in which threads
        // are forked.
        beg1.fork();
        int1.fork();
        exp1.fork();
        beg2.fork();

        // Assume join is not implemented, use yield to allow other
        // threads to run
        for (int i = 0; i < 10; i++) {
            KThread.currentThread().yield();
        }
    }
}
