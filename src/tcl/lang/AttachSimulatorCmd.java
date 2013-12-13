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

import drcl.comp.ACARuntime;
import drcl.comp.Component;
import drcl.ruv.Commands;
import drcl.ruv.Paths;
import drcl.sim.SimulatorAssistant;

// This class implements the built-in "cd" command in Tcl.

class AttachSimulatorCmd implements Command {


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

    if (argv.length > 2) {
	throw new TclNumArgsException(interp, 1, argv, "?dirName?");
    }

    if (argv.length == 2) {
		pathss_[0] = new Paths(interp.getWorkingComponent(), argv[1].toString());
    } else {
    	// to be implemented
    }

    Object[] objs = Commands.toRef("", pathss_, false, interp.getShell());
    if(objs[0] instanceof Component){
    	Component comp = (Component) objs[0];
    	ACARuntime sim = SimulatorAssistant.onSingleMachine();
    	sim.takeover(comp);
    	
    	interp.setResult(ReflectObject.newInstance(interp, sim.getClass(), sim));
    }
    else
    	interp.getShell().print("attach simulator error\n");
}

} // end CdCmd class


