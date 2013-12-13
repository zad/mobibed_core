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
import java.util.ArrayList;

import drcl.comp.Component;
import drcl.ruv.Commands;
import drcl.ruv.Paths;

// This class implements the built-in "cd" command in Tcl.

class MkdirCmd implements Command {


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
	ArrayList<Paths> pathss_ = new ArrayList<Paths>();
	 String classInfo_ = "";
	if (argv.length <= 1)
		interp.getShell().print("mkdir: no arugment error\n");
	
	String soption_ = "";
	int classIdx = 1;
	if(argv[1].toString().startsWith("-")){
		soption_ = argv[1].toString().substring(1);
		classIdx++;
	}

	if(argv[classIdx].toString().contains("@")){
		// mkdir ?-aq? <port_path1> ?<port_path2>...?
		classInfo_ = "drcl.comp.Port";
		for(int i=classIdx;i<argv.length;i++)
    		pathss_.add(new Paths(interp.getWorkingComponent(), argv[i].toString()));
	}else if (classIdx == argv.length-1){
		// mkdir ?-aq? <component_path>
		classInfo_ = "drcl.comp.Component";
		pathss_.add(new Paths(interp.getWorkingComponent(), argv[classIdx].toString()));
	}else{
		// mkdir ?-aq? <Java_class_name> <path1> ?<path2>...?
		// mkdir ?-aq? <Java_object_ref> <path1> ?<path2>...?
		classInfo_ = argv[classIdx].toString();
    	for(int i=classIdx+1;i<argv.length;i++)
    		pathss_.add(new Paths(interp.getWorkingComponent(), argv[i].toString()));
	}


    Commands.mkdir(soption_, classInfo_, pathss_.toArray(new Paths[pathss_.size()]), false, interp.getShell());

}

} 


