/*
 * TimeCmd.java
 *
 * Copyright (c) 1997 Cornell University.
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: TimeCmd.java,v 1.1.1.1 1998/10/14 21:09:18 cvsadmin Exp $
 *
 */

package tcl.lang;

/**
 * This class implements the built-in "time" command in Tcl.
 */

class TimeCmd implements Command {
    /**
     * See Tcl user documentation for details.
     */

    public void cmdProc(Interp interp, TclObject argv[])
	    throws TclException {
	if ((argv.length < 2) || (argv.length > 3)) {
	    throw new TclNumArgsException(interp, 1, argv, 
		    "script ?count?");
	}

	int count;
	if (argv.length == 2) {
	    count = 1;
	} else {
	    count = TclInteger.get(interp, argv[2]);
	}

	long startTime = System.currentTimeMillis();
	for (int i=0; i<count; i++) {
	    interp.eval(argv[1], 0);
	}
	long endTime = System.currentTimeMillis();

	int uSecs = (int)(((endTime - startTime) * 1000) / count);
	if (uSecs == 1) {
	    interp.setResult(TclString.newInstance(
		    "1 microsecond per iteration"));
	} else {
	    interp.setResult(TclString.newInstance(
	    	    uSecs + " microseconds per iteration"));
	}
    }
}

