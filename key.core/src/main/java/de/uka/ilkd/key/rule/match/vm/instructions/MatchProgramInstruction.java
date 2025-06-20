/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.rule.match.vm.instructions;

import de.uka.ilkd.key.java.ProgramElement;
import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.java.SourceData;
import de.uka.ilkd.key.rule.MatchConditions;
import de.uka.ilkd.key.rule.match.vm.TermNavigator;

import de.uka.ilkd.key.java.StatementBlock;
import de.uka.ilkd.key.java.declaration.JavaDeclaration;

import org.key_project.logic.LogicServices;

public class MatchProgramInstruction implements MatchInstruction {

    private final ProgramElement pe;

    public MatchProgramInstruction(ProgramElement pe) {
        this.pe = pe;
    }

    @Override
    public MatchConditions match(TermNavigator termPosition, MatchConditions matchConditions,
            LogicServices services) {
        final MatchConditions result = pe.match(
            new SourceData(termPosition.getCurrentSubterm().javaBlock().program(), -1,
                (Services) services),
            matchConditions);
        if (result != null) {
            termPosition.gotoNext();
        }
        return result;
    }

    @Override
    public String toString() {
        var add = "";

        if (pe instanceof StatementBlock) {
            var body = ((StatementBlock)pe).getBody();
            var first = body.get(0);

            add += " " + first.toString() + " " + first.getClass() + " " + ((JavaDeclaration)first).getModifiers();
        }

        return "MatchProgramInstruction(pe: " + pe.toString() + ", " + pe.getClass() + ")" + add;
    }
}
