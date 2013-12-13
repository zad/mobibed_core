/*
 * CdCmd.java
 *
 *	This file contains the Jacl implementation of the built-in Tcl "cd"
 *	command.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: CdCmd.java,v 1.2 1999/05/08 23:53:08 dejong Exp $
 *
 */

package tcl.lang;

import java.io.*;

import drcl.comp.Component;
import drcl.ruv.Commands;
import drcl.ruv.Paths;

// This class implements the built-in "cd" command in Tcl.

class RefCmd implements Command {


/*
 *-----------------------------------------------------------------------------
 *
 * cmdProc --
 *
 *	This procedure is invoked to process the "cd" Tcl command.
 *	See the user documentation for details on what it does.
 *
 * Results:
 *	None.
 *
 * Side effects:
 *	See the user documentation.
 *
 *-----------------------------------------------------------------------------
 */

public void
cmdProc(
    Interp interp,  			// Current interp to eval the file cmd.
    TclObject argv[])			// Args passed to the file command.
throws
    TclException
{
    Paths[] pathss_ = new Paths[1];

    if (argv.length < 2) {
	throw new TclNumArgsException(interp, 1, argv, "no argument");
    }

	pathss_[0] = new Paths(interp.getWorkingComponent(), argv[1].toString());
	Object[] newComp = Commands.toRef("", pathss_, false, interp.getShell());
	Object newObj = newComp[0];
	TclObject reflectObj = ReflectObject.newInstance(interp, newObj.getClass(), newObj);
    if (argv.length == 2) {

		
		
		interp.setResult(reflectObj);
    } else {
    	int classIdx = 2; 
    	int startIdx = classIdx + 1;
    	int count = argv.length - startIdx;
    	TclObject obj = JavaInvoke.callMethod(interp, reflectObj, argv[classIdx], argv, startIdx, count, false);
//    	interp.getShell().print(obj.toString());
//    	interp.callJSIMMethod(newComp[0], argv[classIdx], argv, startIdx, count);
//    	interp.resetResult();
    }

    

    
}

} // end CdCmd class


