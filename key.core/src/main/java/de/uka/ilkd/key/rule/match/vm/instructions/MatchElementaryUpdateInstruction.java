/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.rule.match.vm.instructions;

import de.uka.ilkd.key.logic.Term;
import de.uka.ilkd.key.logic.op.ElementaryUpdate;
import de.uka.ilkd.key.logic.op.LocationVariable;
import de.uka.ilkd.key.logic.op.Operator;
import de.uka.ilkd.key.logic.op.ProgramSV;
import de.uka.ilkd.key.rule.MatchConditions;
import de.uka.ilkd.key.rule.match.vm.TacletMatchProgram;
import de.uka.ilkd.key.rule.match.vm.TermNavigator;

import org.key_project.logic.LogicServices;

public class MatchElementaryUpdateInstruction extends Instruction<ElementaryUpdate> {

    private final MatchOperatorInstruction leftHandSide;

    protected MatchElementaryUpdateInstruction(ElementaryUpdate op) {
        super(op);
        if (op.lhs() instanceof LocationVariable) {
            leftHandSide =
                new MatchOpIdentityInstruction<>((LocationVariable) op.lhs());
        } else {
            assert op.lhs() instanceof ProgramSV;
            leftHandSide = (MatchOperatorInstruction) TacletMatchProgram
                    .getMatchInstructionForSV((ProgramSV) op.lhs());
        }
    }

    @Override
    public MatchConditions match(Term instantiationCandidate, MatchConditions matchCond,
            LogicServices services) {
        final Operator instantiationCandidateOp = instantiationCandidate.op();
        if (instantiationCandidateOp != op) {
            if (instantiationCandidateOp instanceof ElementaryUpdate instElUpdate) {
                matchCond = leftHandSide.match(instElUpdate.lhs(), matchCond, services);
            } else {
                matchCond = null;
            }
        }
        return matchCond;
    }

    @Override
    public MatchConditions match(TermNavigator termPosition, MatchConditions matchConditions,
            LogicServices services) {
        final MatchConditions result =
            match(termPosition.getCurrentSubterm(), matchConditions, services);
        if (result != null) {
            termPosition.gotoNext();
        }
        return result;
    }

    @Override
    public String toString() {
        return "MatchElemantaryUpdateInstruction";
    }
}
