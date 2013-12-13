/*
 * JavaCallCmd.java
 *
 *	Implements the built-in "java::call" command.
 *
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 * RCS: @(#) $Id: JavaCallCmd.java,v 1.2 2002/12/07 20:46:58 mdejong Exp $
 *
 */

package tcl.lang;

import drcl.ruv.Commands;

/*
 * Implements the built-in "inject" command.
 */

class InjectCmd implements Command {


/*----------------------------------------------------------------------
 *
 * cmdProc --
 *
 * 	This procedure is invoked to process the "java::call" Tcl
 * 	command. See the user documentation for details on what it
 * 	does.
 *
 * Results:
 *	None.
 *
 * Side effects:
 *	A standard Tcl result is stored in the interpreter.
 *
 *----------------------------------------------------------------------
 */

public void
cmdProc(
    Interp interp,			// Current interpreter.
    TclObject argv[])			// Argument list.
throws
    TclException			// A standard Tcl exception.
{
    boolean convert;

    if (argv.length < 3) {
	throw new TclNumArgsException(interp, 1, argv, 
		"?-noconvert? class signature ?arg arg ...?");
    }

    String arg1 = argv[1].toString();
    if ((arg1.length() >= 2) && ("-noconvert".startsWith(arg1))) {
    	convert = false;	
    } else {
    	convert = true;
    }
    
    int startIdx = 1;
    int count = argv.length - startIdx;
    TclObject cls = TclString.newInstance("drcl.comp.Util");
    TclObject mtd = TclString.newInstance("inject");
    // convert paths from TclString to port object
    Object[] objs =  Commands.toRef(interp.getWorkingComponent(), argv[startIdx+1].toString());
    argv[startIdx+1] = ReflectObject.newInstance(interp, drcl.comp.Port.class, objs[0]);
    
    TclObject result = JavaInvoke.callStaticMethod(interp, cls,
            mtd, argv, startIdx, count, convert);
    // TODO: inject multiple paths
    if (result == null)
        interp.resetResult();
    else
        interp.setResult(result);
}

} // end JavaCallCmd

