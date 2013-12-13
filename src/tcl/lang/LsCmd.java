/*
 * ListCmd.java
 *
 * Copyright (c) 1997 Cornell University.
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: ListCmd.java,v 1.2 2006/01/13 03:40:11 mdejong Exp $
 *
 */

package tcl.lang;


import java.util.ArrayList;
import drcl.ruv.Commands;
import drcl.ruv.Paths;

/**
 * This class implements the built-in "list" command in Tcl.
 */
class LsCmd implements Command {

    /**
     * See Tcl user documentation for details.
     */
    public void cmdProc(Interp interp, TclObject argv[])
	    throws TclException {
    	String soption_ = "";
    	ArrayList<Paths> pathss_ = new ArrayList<Paths>();
    	
    	
    	for(int i=1;i<argv.length;i++){
    		String token = argv[i].stringRep;
    		if(token.startsWith("-"))
    			soption_ += token.substring(1);
    		else
    			pathss_.add(new Paths((drcl.comp.Component)null, token));
    	}
    	
    	if(pathss_.isEmpty())
    	{
    		pathss_.add(new Paths(interp.getWorkingComponent(), "."));
    	}
    	
		Paths[] paths = pathss_.toArray(new Paths[pathss_.size()]);
		String result = Commands.ls(soption_, paths, null);
		interp.getShell().print(result);
    }
}

