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

import drcl.ruv.Commands;



// This class implements the built-in "cd" command in Tcl.

class ManCmd implements Command {


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
//    String dirName;
//
	if(argv.length == 1)
	{
		String result = Commands.getAllCommands();
		System.out.println(result);
		return;
	}
    if (argv.length > 2) {
    	throw new TclNumArgsException(interp, 1, argv, "?dirName?");
    }
//
//    if (argv.length == 1) {
//	dirName = "~";
//    } else {
//	dirName = argv[1].toString();
//    }
//    if ((JACL.PLATFORM == JACL.PLATFORM_WINDOWS) 
//	    && (dirName.length() == 2) && (dirName.charAt(1) == ':')) {
//	dirName = dirName + "/";
//    }
//
//    // Set the interp's working dir.
//
//    interp.setWorkingDir(dirName);
	
	Commands.man(argv[1].toString());
}

} // end CdCmd class


