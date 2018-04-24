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
        expertMatch;

    Lock beginnerLock,
        intermediateLock,
        expertLock;

    // Holds number of player threads waiting in respective Conditions
    private int numBeginners,
        numIntermediates,
        numExperts;

    /**
     * Allocate a new GameMatch specifying the number of player
     * threads of the same ability required to form a match.  Your
     * implementation may assume this number is always greater than zero.
     */
    public GameMatch (int numPlayersInMatch) {
        matchSize = numPlayersInMatch;

        beginnerLock = new Lock();
        beginnerMatch = new Condition(beginnerLock);

        intermediateLock = new Lock();
        intermediateMatch = new Condition(intermediateLock);

        expertLock = new Lock();
        expertMatch = new Condition(expertLock);

        numBeginners = 0;
        numIntermediates = 0;
        numExperts = 0;


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
        switch(ability){
            case(abilityBeginner):
                beginnerLock.acquire();
                numBeginners++;
                if(numBeginners == matchSize) {
                    numBeginners = 0;
                    beginnerMatch.wakeAll();
                } else {
                    beginnerMatch.sleep();
                }

                beginnerLock.release();
                return 0;

            case(abilityIntermediate):
                intermediateLock.acquire();
                numIntermediates++;
                if(numIntermediates == matchSize){
                    numIntermediates = 0;
                    intermediateMatch.wakeAll();
                } else {
                    intermediateMatch.sleep();
                }

                intermediateLock.release();
                return 0;

            case(abilityExpert):
                expertLock.acquire();
                numExperts++;
                if(numExperts == matchSize){
                    numExperts = 0;
                    expertMatch.wakeAll();
                } else {
                    expertMatch.sleep();
                }

                expertLock.release();
                return 0;

            default:
                return -1;
        }
    }

    /**
     * Invoked in ThreadedKernel to run all test methods defined below
     */
    public static void selfTest(){
        playTestUnknownAbility();
//        playTestBeginnerMatch();
        playTestBeginnerMatch2();
        playTestIntermediateMatch2();
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
     * Deprecated test case for making a beginner match. playTestBeginnerMatch2 can be quickly modified
     * to alter the number of players in the match
     */
//    public static void playTestBeginnerMatch(){
//        GameMatch game = new GameMatch(3);
//        System.out.println("Test: playTestBeginnerMatch\n" +
//            "Verify that all players are in the same match, and that all players have queued before any player joins\n"+
//            "Note that the order in which players join is not guaranteed");
//        KThread player1 = new KThread(new Runnable(){
//            public void run(){
//                System.out.println("Player 1 entering queue");
//                System.out.println("Player 1 joining game " + game.play(abilityBeginner));
//            }
//        });
//        KThread player2 = new KThread(new Runnable(){
//            public void run(){
//                System.out.println("Player 2 entering queue");
//                System.out.println("Player 2 joining game " + game.play(abilityBeginner));
//            }
//        });
//
//        KThread player3 = new KThread(new Runnable(){
//            public void run(){
//                System.out.println("Player 3 entering queue");
//                System.out.println("Player 3 joining game " + game.play(abilityBeginner));
//            }
//        });
//        player1.setName("player1").fork();
//        player2.setName("player2").fork();
//        player3.setName("player3").fork();
//        player1.join();
//        player2.join();
//        player3.join();
//    }

    /**
     * Tests that beginner matches are set up correctly
     */
    public static void playTestBeginnerMatch2() {
        int numPlayers = 200;
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
        int numPlayers = 200;
        GameMatch game = new GameMatch(numPlayers);
        System.out.println("Test: playTestIntermediateMatch\n" +
                "Verify that all players are in the same match, and that all players have queued before any player joins\n" +
                "Note that the order in which players join is not guaranteed");
        LinkedList<KThread> players = new LinkedList<>();
        for (int i = 0; i < numPlayers; i++) {
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
        for (int i = 0; i < players.size(); i++){
            players.get(i).join();
        }

    }
}
