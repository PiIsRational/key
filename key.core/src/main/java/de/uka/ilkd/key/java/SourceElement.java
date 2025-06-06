/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.java;

import de.uka.ilkd.key.java.visitor.Visitor;
import de.uka.ilkd.key.logic.equality.EqualsModProperty;

import org.key_project.logic.Property;
import org.key_project.logic.SyntaxElement;

/**
 * A source element is a piece of syntax. It does not necessarily have a semantics, at least none
 * that is machinable, for instance a {@link recoder.java.Comment}. taken from RECODER and changed
 * to achieve an immutable structure
 */

public interface SourceElement extends SyntaxElement, EqualsModProperty<SourceElement> {


    /**
     * Finds the source element that occurs first in the source.
     *
     * @return the first source element in the syntactical representation of this element, may be
     *         equals to this element.
     * @see #getStartPosition()
     */
    SourceElement getFirstElement();


    /**
     * Finds the source element that occurs first in the source.
     *
     * @return the first source element in the syntactical representation of this element, may be
     *         equals to this element.
     * @see #getStartPosition()
     */
    SourceElement getFirstElementIncludingBlocks();


    /**
     * Finds the source element that occurs last in the source.
     *
     * @return the last source element in the syntactical representation of this element, may be
     *         equals to this element.
     * @see #getEndPosition()
     */
    SourceElement getLastElement();

    /**
     * Returns the start position of the primary token of this element. To get the start position of
     * the syntactical first token, call the corresponding method of <CODE>getFirstElement()</CODE>.
     *
     * @return the start position of the primary token.
     */
    Position getStartPosition();

    /**
     * Returns the end position of the primary token of this element. To get the end position of the
     * syntactical first token, call the corresponding method of <CODE>getLastElement()</CODE>.
     *
     * @return the end position of the primary token.
     */
    Position getEndPosition();

    /**
     * Returns the relative position (number of blank heading lines and columns) of the primary
     * token of this element. To get the relative position of the syntactical first token, call the
     * corresponding method of <CODE>getFirstElement()</CODE>.
     *
     * @return the relative position of the primary token.
     */
    recoder.java.SourceElement.Position getRelativePosition();

    /** complete position information */
    PositionInfo getPositionInfo();


    /**
     * calls the corresponding method of a visitor in order to perform some action/transformation on
     * this element
     *
     * @param v the Visitor
     */
    void visit(Visitor v);

    /**
     * This method returns true if two program parts are equal modulo renaming. The equality is
     * mainly a syntactical equality with some exceptions: if a variable is declared we abstract
     * from the name of the variable, so the first declared variable gets e.g. the name decl_1, the
     * second declared decl_2 and so on. Look at the following programs: {int i; i=0;} and { int j;
     * j=0;} these would be seen like {int decl_1; decl_1=0;} and {int decl_1; decl_1=0;} which are
     * syntactical equal and therefore true is returned (same thing for labels). But {int i; i=0;}
     * and {int j; i=0;} (in the second block the variable i is declared somewhere outside) would be
     * seen as {int decl_1; decl_1=0;} for the first one but {int decl_1; i=0;} for the second one.
     * These are not syntactical equal, therefore false is returned.
     */
    @Override
    default <V> boolean equalsModProperty(Object o, Property<SourceElement> property, V... v) {
        if (!(o instanceof SourceElement)) {
            return false;
        }
        return property.equalsModThisProperty(this, (SourceElement) o, v);
    }

    @Override
    default int hashCodeModProperty(Property<? super SourceElement> property) {
        return property.hashCodeModThisProperty(this);
    }
}
