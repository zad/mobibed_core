/*
 * StrtoulResult.java
 *
 *	Stores the result of the Util.strtoul() method.
 *
 * Copyright (c) 1997 Cornell University.
 * Copyright (c) 1997 Sun Microsystems, Inc.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 * 
 * RCS: @(#) $Id: StrtoulResult.java,v 1.3 2005/09/30 02:12:17 mdejong Exp $
 *
 */

package tcl.lang;

/**
 * This class stores the result of the Util.strtoul() method.
 */

class StrtoulResult {

// If the conversion is successful, errno = 0;
//
// If the number cannot be converted to a valid unsigned 32-bit integer,
// contains the error code (TCL.INTEGER_RANGE or TCL.INVALID_INTEGER).

int errno = 0;

// If errno is 0, points to the character right after the number

int index = -1;

// If errno is 0, contains the value of the number.

long value = 0;

// Update a StrtoulResult. Note that there is typically
// just one StrtoulResult for each interp.

void update(
    long v,				// value for the value field.
    int i,				// value for the index field.
    int e)				// value for the errno field.
{
    value = v;
    index = i;
    errno = e;
}

} // end StrtoulResult

