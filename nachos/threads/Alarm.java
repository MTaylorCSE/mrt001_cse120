package nachos.threads;

import nachos.machine.*;
import java.util.*;
/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

	private static LinkedList<KThread> waitQueue = new LinkedList<>();
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {

			ListIterator<KThread> queueIterator = waitQueue.listIterator();
			while (queueIterator.hasNext()) {
				KThread current = queueIterator.next();
				long curWakeTime = current.getWakeTime();
				if (curWakeTime <= Machine.timer().getTime()) {
					current.ready();
					queueIterator.remove();
				}
			}

		KThread.yield();

	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {

		Machine.interrupt().disable();
		long wakeTime = Machine.timer().getTime() + x;

		KThread.currentThread().setWakeTime(wakeTime);

		waitQueue.add(KThread.currentThread());

		KThread.sleep();

	}
	/**
	 * Test whether the threads waits for approximately the time that was requested
	 */
	public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;

		for (int d: durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();

			System.out.println ("alarmTest1: waited for " + (t1 - t0) + "ticks");
		}
	}

	/**
	 * Test if thread does not wait when wait parameter is negative
	 */
	public static void alarmTest2() {
		long t0, t1;

		for(int i = -10; i < 1 ; ++i){
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(i);
			t1 = Machine.timer().getTime();

			// we are told timer interrupts around 500 ticks so anything less than
			// 600 means the machine did not wait
			if( (t1-t0) < 600) {
				System.out.println("alarmTest2: dope machine did not wait" );
			}
		}
	}


	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {
		alarmTest1();
		alarmTest2();
	}
}
