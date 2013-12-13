/*
 * IdleHandler.java --
 *
 *	The API for defining idle event handler.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: IdleHandler.java,v 1.3 2006/06/12 04:00:18 mdejong Exp $
 *
 */

package tcl.lang;

/*
 * This abstract class is used to define idle handlers.
 */

public abstract class IdleHandler {

/*
 * Back pointer to the notifier that will fire this idle.
 */

Notifier notifier;

/*
 * True if the cancel() method has been called.
 */

boolean isCancelled;

/*
 * Used to distinguish older idle handlers from recently-created ones.
 */

int generation;


/*
 *----------------------------------------------------------------------
 *
 * IdleHandler --
 *
 *	Create a idle handler to be fired when the notifier is idle.
 *
 * Results:
 *	None.
 *
 * Side effects:
 *	The idle is registered in the list of idle handlers in the
 *	given notifier. When the notifier is idle, the
 *	processIdleEvent() method will be invoked exactly once inside
 *	the primary thread of the notifier.
 *
 *----------------------------------------------------------------------
 */

public
IdleHandler(
    Notifier n)			// The notifier to fire the event.
{
    notifier = (Notifier)n;
    isCancelled = false;

    synchronized(notifier) {
	notifier.idleList.add(this);
	generation = notifier.idleGeneration;
	if (Thread.currentThread() != notifier.primaryThread) {
	    notifier.notifyAll();
	}
    }
}

/*
 *----------------------------------------------------------------------
 *
 * cancel --
 *
 *	Mark this idle handler as cancelled so that it won't be invoked.
 *
 * Results:
 *	None.
 *
 * Side effects:
 *	The idle handler is marked as cancelled so that its
 *	processIdleEvent() method will not be called. If the idle
 *	event has already fired, then nothing this call has no effect.
 *
 *----------------------------------------------------------------------
 */

public synchronized void
cancel()
{
    if (isCancelled) {
	return;
    }

    isCancelled = true;

    synchronized(notifier) {
	for (int i = 0; i < notifier.idleList.size(); i++) {
	    if (notifier.idleList.get(i) == this) {
		notifier.idleList.remove(i);

		/*
		 * We can return now because the same idle handler can
		 * be registered only once in the list of idles.
		 */

		return;
	    }
	}
    }
}

/*
 *----------------------------------------------------------------------
 *
 * invoke --
 *
 *	Execute the idle handler if it has not been cancelled. This
 *	method should be called by the notifier only.
 *
 *	Because the idle handler may be being cancelled by another
 *	thread, both this method and cancel() must be synchronized to
 *	ensure correctness.
 *
 * Results:
 *	0 if the handler was not executed because it was already
 *	cancelled, 1 otherwise.
 *
 * Side effects:
 *	The idle handler may have arbitrary side effects.
 *
 *----------------------------------------------------------------------
 */

synchronized final int
invoke()
{
    /*
     * The idle handler may be cancelled after it was registered in
     * the notifier. Check the isCancelled field to make sure it's not
     * cancelled.
     */

    if (!isCancelled) {
	processIdleEvent();
	return 1;
    } else {
	return 0;
    }
}

/*
 *----------------------------------------------------------------------
 *
 * processIdleEvent --
 *
 *	This method is called when the idle is expired. Override
 *	This method to implement your own idle handlers.
 *
 * Results:
 *	None.
 *
 * Side effects:
 *	It can do anything.
 *
 *----------------------------------------------------------------------
 */

abstract public void
processIdleEvent();

/*
 *----------------------------------------------------------------------
 *
 * toString --
 *
 *	This method prints a text description of the event for debugging.
 *
 *----------------------------------------------------------------------
 */

public
String toString() {
    StringBuffer sb = new StringBuffer(64);
    sb.append("IdleHandler.generation is " + generation + "\n");
    return sb.toString();
}

} // end IdleHandler

