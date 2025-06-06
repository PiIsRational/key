/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.rule;

import java.util.Iterator;

import de.uka.ilkd.key.logic.op.VariableSV;

import org.key_project.logic.op.sv.SchemaVariable;
import org.key_project.util.collection.ImmutableSet;

/**
 * This class contains the prefix for a Taclet according to M.Gieses paper "Taclet mit
 * Schemavariablen und lokalen Deklarationen" It is used as a data container for the set of all
 * variables bound above the appearance of a SchemaVariable v in a Taclet without all those x not
 * free in v variables
 */
public class TacletPrefix implements org.key_project.prover.rules.TacletPrefix {

    /** the prefix of the taclet */
    private final ImmutableSet<SchemaVariable> prefix;
    /** used by rewrite taclets to mark the context */
    private final boolean context;

    /**
     * creates the prefix
     *
     * @param prefix the SetOf<SchemaVariable> that is the prefix of a termsv or formulasv
     * @param context a boolean marker
     */
    public TacletPrefix(ImmutableSet<SchemaVariable> prefix,
            boolean context) {
        this.prefix = prefix;
        this.context = context;
    }

    /**
     * returns the prefix
     *
     * @return the prefix
     */
    public ImmutableSet<SchemaVariable> prefix() {
        return prefix;
    }

    public Iterator<SchemaVariable> iterator() {
        return prefix().iterator();
    }

    /**
     * returns the context marker
     *
     * @return the context marker
     */
    public boolean context() {
        return context;
    }

    /**
     * returns a new TacletPrefix with the context flag set to the given boolean value
     *
     * @param setTo the boolean to which the TacletPrefix is set to
     * @return a newly created TacletPrefix
     */
    public TacletPrefix setContext(boolean setTo) {
        return new TacletPrefix(prefix, setTo);
    }

    /**
     * creates a new TacletPrefix with a new prefix entry
     *
     * @param var the SchemaVariable to be added
     * @return the new prefix
     */
    public TacletPrefix put(SchemaVariable var) {
        if (!(var instanceof VariableSV)) {
            throw new RuntimeException("var can match more than " + "bound variables");
        }
        return new TacletPrefix(prefix.add(var), context);
    }

    /**
     * removes a SchemaVariable from the prefix
     *
     * @param var the SchemaVariable to be removed
     * @return the new prefix
     */
    public TacletPrefix remove(SchemaVariable var) {
        return new TacletPrefix(prefix.remove(var), context);
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof TacletPrefix other)) {
            return false;
        }
        return (other.prefix().equals(prefix())) && (other.context() == context());
    }

    public int hashCode() {
        int result = 17;
        result = 37 * result + prefix().hashCode();
        result = 37 * result + (context() ? 0 : 1);
        return result;
    }

    public String toString() {
        return "TacletPrefix: " + prefix + (context() ? "+ { K }" : "");
    }
}
