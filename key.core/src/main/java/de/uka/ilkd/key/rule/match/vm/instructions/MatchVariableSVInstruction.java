/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.rule.match.vm.instructions;

import de.uka.ilkd.key.logic.Term;
import de.uka.ilkd.key.logic.op.QuantifiableVariable;
import de.uka.ilkd.key.logic.op.VariableSV;
import de.uka.ilkd.key.rule.MatchConditions;
import de.uka.ilkd.key.rule.match.vm.TermNavigator;

import org.key_project.logic.LogicServices;

public class MatchVariableSVInstruction extends MatchSchemaVariableInstruction<VariableSV> {

    protected MatchVariableSVInstruction(VariableSV op) {
        super(op);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MatchConditions match(Term subst, MatchConditions mc, LogicServices services) {
        if (subst.op() instanceof QuantifiableVariable) {
            final Term foundMapping = (Term) mc.getInstantiations().getInstantiation(op);
            if (foundMapping == null) {
                return addInstantiation(subst, mc, services);
            } else if (foundMapping.op() == subst.op()) {
                return mc;
            }
        }
        return null;
    }

    @Override
    public MatchConditions match(TermNavigator termPosition, MatchConditions mc,
            LogicServices services) {
        final MatchConditions result = match(termPosition.getCurrentSubterm(), mc, services);
        if (result != null) {
            termPosition.gotoNextSibling();
        }
        return result;
    }

    @Override
    public String toString() {
        return "MatchVariableSVInstruction";
    }

}
