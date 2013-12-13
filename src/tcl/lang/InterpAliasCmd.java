/*
 * InterpAliasCmd.java --
 *
 *	Implements the built-in "interp" Tcl command.
 *
 * Copyright (c) 2000 Christian Krone.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: InterpAliasCmd.java,v 1.6 2006/08/03 23:24:02 mdejong Exp $
 *
 */

package tcl.lang;

import java.util.*;

/**
 * This class implements the alias commands, which are created
 * in response to the built-in "interp alias" command in Tcl.
 *
 */

class InterpAliasCmd implements CommandWithDispose {

    // Name of alias command in slave interp.

    TclObject name;

    // Interp in which target command will be invoked.

    private Interp targetInterp;

    // Tcl list making up the prefix of the target command to be invoked in
    // the target interpreter. Additional arguments specified when calling
    // the alias in the slave interp will be appended to the prefix before
    // the command is invoked.

    private TclObject prefix;

    // Source command in slave interpreter, bound to command that invokes
    // the target command in the target interpreter.

    private WrappedCommand slaveCmd;

    // Entry for the alias hash table in slave.
    // This is used by alias deletion to remove the alias from the slave
    // interpreter alias table.

    private String aliasEntry;

    // Interp in which the command is defined.
    // This is the interpreter with the aliasTable in Slave.

    private Interp slaveInterp;

/**
 *----------------------------------------------------------------------
 *
 * AliasObjCmd -> cmdProc
 *
 *	This is the procedure that services invocations of aliases in a
 *	slave interpreter. One such command exists for each alias. When
 *	invoked, this procedure redirects the invocation to the target
 *	command in the master interpreter as designated by the Alias
 *	record associated with this command.
 *
 * Results:
 *	A standard Tcl result.
 *
 * Side effects:
 *	Causes forwarding of the invocation; all possible side effects
 *	may occur as a result of invoking the command to which the
 *	invocation is forwarded.
 *
 *----------------------------------------------------------------------
 */

public void
cmdProc(
    Interp interp,		// Current interpreter.
    TclObject[] argv)		// Argument list.
throws 
    TclException 		// A standard Tcl exception.
{
    targetInterp.preserve();

    try {
        targetInterp.nestLevel++;

        targetInterp.resetResult();
        targetInterp.allowExceptions();

        // Append the arguments to the command prefix and invoke the command
        // in the target interp's global namespace.

        TclObject[] prefv = TclList.getElements(interp, prefix);
        TclObject cmd = TclList.newInstance();
        cmd.preserve();
        TclList.replace(interp, cmd, 0, 0, prefv, 0, prefv.length-1);
        TclList.replace(interp, cmd, prefv.length, 0, argv, 1, argv.length-1);
        TclObject[] cmdv = TclList.getElements(interp, cmd);

        int result = targetInterp.invoke(cmdv, Interp.INVOKE_NO_TRACEBACK);

        cmd.release();
        targetInterp.nestLevel--;

        // Check if we are at the bottom of the stack for the target interpreter.
        // If so, check for special return codes.

        if (targetInterp.nestLevel == 0) {
            if (result == TCL.RETURN) {
                result = targetInterp.updateReturnInfo();
            }
            if (result != TCL.OK && result != TCL.ERROR) {
                try {
                    targetInterp.processUnexpectedResult(result);
                } catch (TclException e) {
                    result = e.getCompletionCode();
                }
            }
        }

        interp.transferResult(targetInterp, result);
    } finally {
        targetInterp.release();
    }
}

/**
 *----------------------------------------------------------------------
 *
 * AliasObjCmdDeleteProc -> disposeCmd
 *
 *	Is invoked when an alias command is deleted in a slave. Cleans up
 *	all storage associated with this alias.
 *
 * Results:
 *	None.
 *
 * Side effects:
 *	Deletes the alias record and its entry in the alias table for
 *	the interpreter.
 *
 *----------------------------------------------------------------------
 */

public void
disposeCmd()
{
    if (aliasEntry != null) {
	slaveInterp.aliasTable.remove(aliasEntry);
    }

    if (slaveCmd != null) {
	targetInterp.targetTable.remove(slaveCmd);
    }

    name.release();
    prefix.release();
}

/**
 *----------------------------------------------------------------------
 *
 * AliasCreate -> create
 *
 *	Helper function to do the work to actually create an alias.
 *
 * Results:
 *	A standard Tcl result.
 *
 * Side effects:
 *	An alias command is created and entered into the alias table
 *	for the slave interpreter.
 *
 *----------------------------------------------------------------------
 */

static void
create(
    Interp interp,		// Interp for error reporting.
    Interp slaveInterp,		// Interp where alias cmd will live or from
				// which alias will be deleted.
    Interp masterInterp,	// Interp in which target command will be
				// invoked. 
    TclObject name,		// Name of alias cmd.
    TclObject targetName,	// Name of target cmd.
    int objIx,			// Offset of first element in objv.
    TclObject[] objv)		// Additional arguments to store with alias
throws
    TclException
{
    String string = name.toString();

    InterpAliasCmd alias = new InterpAliasCmd();

    alias.name = name;
    name.preserve();

    alias.slaveInterp = slaveInterp;
    alias.targetInterp = masterInterp;

    alias.prefix = TclList.newInstance();
    alias.prefix.preserve();
    TclList.append(interp, alias.prefix, targetName);
    TclList.insert(interp, alias.prefix, 1, objv, objIx, objv.length-1);

    slaveInterp.createCommand(string, alias);
    alias.slaveCmd = Namespace.findCommand(slaveInterp, string, null, 0);

    try {
	interp.preventAliasLoop(slaveInterp, alias.slaveCmd);
    } catch (TclException e) {
	// Found an alias loop!  The last call to Tcl_CreateObjCommand made
	// the alias point to itself.  Delete the command and its alias
	// record.  Be careful to wipe out its client data first, so the
	// command doesn't try to delete itself.

	slaveInterp.deleteCommandFromToken(alias.slaveCmd);
	throw e;
    }

    // Make an entry in the alias table. If it already exists delete
    // the alias command. Then retry.

    if (slaveInterp.aliasTable.containsKey(string)) {
	InterpAliasCmd oldAlias =
	    (InterpAliasCmd) slaveInterp.aliasTable.get(string);
	slaveInterp.deleteCommandFromToken(oldAlias.slaveCmd);
    }

    alias.aliasEntry = string;
    slaveInterp.aliasTable.put(string, alias);
    
    // Create the new command. We must do it after deleting any old command,
    // because the alias may be pointing at a renamed alias, as in:
    //
    // interp alias {} foo {} bar		# Create an alias "foo"
    // rename foo zop				# Now rename the alias
    // interp alias {} foo {} zop		# Now recreate "foo"...

    masterInterp.targetTable.put(alias.slaveCmd, slaveInterp);

    interp.setResult(name);
}

/**
 *----------------------------------------------------------------------
 *
 * AliasDelete -> delete
 *
 *	Deletes the given alias from the slave interpreter given.
 *
 * Results:
 *	A standard Tcl result.
 *
 * Side effects:
 *	Deletes the alias from the slave interpreter.
 *
 *----------------------------------------------------------------------
 */

static void
delete(
    Interp interp,		// Interp for error reporting.
    Interp slaveInterp,		// Interp where alias cmd will live or from
				// which alias will be deleted.
    TclObject name)		// Name of alias to delete.
throws
    TclException
{
    // If the alias has been renamed in the slave, the master can still use
    // the original name (with which it was created) to find the alias to
    // delete it.

    String string = name.toString();
    if (!slaveInterp.aliasTable.containsKey(string)){ 
	throw new TclException(interp, "alias \"" + string + "\" not found");
    }

    InterpAliasCmd alias = (InterpAliasCmd) slaveInterp.aliasTable.get(string);
    slaveInterp.deleteCommandFromToken(alias.slaveCmd);
}

/*
 *----------------------------------------------------------------------
 *
 * AliasDescribe -> describe --
 *
 *	Sets the interpreter's result object to a Tcl list describing
 *	the given alias in the given interpreter: its target command
 *	and the additional arguments to prepend to any invocation
 *	of the alias.
 *
 * Results:
 *	A standard Tcl result.
 *
 * Side effects:
 *	None.
 *
 *----------------------------------------------------------------------
 */

static void
describe(
    Interp interp,		// Interp for error reporting.
    Interp slaveInterp,		// Interp where alias cmd will live or from
				// which alias will be deleted.
    TclObject name)		// Name of alias to delete.
throws
    TclException
{
    // If the alias has been renamed in the slave, the master can still use
    // the original name (with which it was created) to find the alias to
    // describe it.

    String string = name.toString();
    if (slaveInterp.aliasTable.containsKey(string)){ 
	InterpAliasCmd alias =
	    (InterpAliasCmd) slaveInterp.aliasTable.get(string);
	interp.setResult(alias.prefix);

    }
}

/**
 *----------------------------------------------------------------------
 *
 * AliasList -> list
 *
 *	Computes a list of aliases defined in a slave interpreter.
 *
 * Results:
 *	A standard Tcl result.
 *
 * Side effects:
 *	None.
 *
 *----------------------------------------------------------------------
 */

static void
list(
    Interp interp,		// Interp for error reporting.
    Interp slaveInterp)		// Interp whose aliases to compute.
throws
    TclException
{
    TclObject result = TclList.newInstance();
    for (Iterator iter = slaveInterp.aliasTable.entrySet().iterator(); iter.hasNext() ; ) {
	Map.Entry entry = (Map.Entry) iter.next();
	InterpAliasCmd alias = (InterpAliasCmd) entry.getValue();
	TclList.append(interp, result, alias.name);
    }
    interp.setResult(result);
}

/**
 *----------------------------------------------------------------------
 *
 * getTargetCmd --
 *
 *	helper function, that returns the WrappedCommand of the target
 *	command (i.e. the command which is called in the master interpreter).
 *
 * Results:
 *	The wrapped command.
 *
 * Side effects:
 *	None.
 *
 *----------------------------------------------------------------------
 */

WrappedCommand
getTargetCmd(
    Interp interp)		// Interp for error reporting.
throws
    TclException
{
  TclObject objv[] = TclList.getElements(interp, prefix);
  String targetName = objv[0].toString();
  return Namespace.findCommand(targetInterp, targetName, null, 0);
}

/**
 *----------------------------------------------------------------------
 *
 * getTargetInterp --
 *
 *	static helper function, that returns the target interpreter of
 *	an alias with the given name in the given slave interpreter.
 *
 * Results:
 *	The target interpreter, or null if no alias was found.
 *
 * Side effects:
 *	None.
 *
 *----------------------------------------------------------------------
 */

static Interp
getTargetInterp(
    Interp slaveInterp,
    String aliasName)
{
    if (!slaveInterp.aliasTable.containsKey(aliasName)) {
	return null;
    }

    InterpAliasCmd alias =
	(InterpAliasCmd) slaveInterp.aliasTable.get(aliasName);

    return alias.targetInterp;
}

} // end InterpAliasCmd
