/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.java.recoderext;

import recoder.java.*;
import recoder.java.reference.ReferencePrefix;
import recoder.java.reference.ThisReference;
import recoder.java.reference.TypeReference;

public class SpecialReferenceWrapper extends ThisReference
        implements KeYRecoderExtension {

    /**
     *
     */
    private static final long serialVersionUID = -8843308796536009121L;
    protected TypeReference typeRef;
    protected ReferencePrefix myprefix;


    protected StatementContainer statementParent = null;

    public SpecialReferenceWrapper() {
        expressionParent = null;
    }


    public SpecialReferenceWrapper(TypeReference typeRef, ReferencePrefix myprefix) {
        this.typeRef = typeRef;
        this.myprefix = myprefix;
        expressionParent = null;
    }

    protected SpecialReferenceWrapper(SpecialReferenceWrapper proto) {
        super(proto);
        expressionParent = null;
    }


    /**
     * Make parent role valid.
     */
    public void makeParentRoleValid() {
    }

    /**
     * Get AST parent.
     *
     * @return the non terminal program element.
     */
    public NonTerminalProgramElement getASTParent() {
        return expressionParent;
    }


    // don't think we need it
    public void accept(SourceVisitor v) {
    }

    public SpecialReferenceWrapper deepClone() {
        return new SpecialReferenceWrapper(typeRef, myprefix);
    }

    /**
     * Get statement container.
     *
     * @return the statement container.
     */
    public StatementContainer getStatementContainer() {
        return statementParent;
    }

    /**
     * Set statement container.
     *
     * @param c a statement container.
     */
    public void setStatementContainer(StatementContainer c) {
        statementParent = c;
    }

    public TypeReference getTypeReference() {
        return typeRef;
    }

    /**
     * Set type reference
     */
    public void setTypeReference(TypeReference ref) {
        this.typeRef = ref;
    }

    public ReferencePrefix getReferencePrefix() {
        return myprefix;
    }

    /**
     * Set reference suffix.
     *
     * @param myprefix a reference prefix.
     */
    public void setReferencePrefix(ReferencePrefix myprefix) {
        this.myprefix = myprefix;
    }
}
