/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.rule.match.vm.instructions;

import de.uka.ilkd.key.rule.MatchConditions;
import de.uka.ilkd.key.rule.match.vm.TermNavigator;

import org.key_project.logic.LogicServices;

public class UnbindVariablesInstruction implements MatchInstruction {

    @Override
    public MatchConditions match(TermNavigator termPosition, MatchConditions matchConditions,
            LogicServices services) {
        return matchConditions.shrinkRenameTable();
    }

    @Override
    public String toString() {
        return "UnbindVariablesInstruction";
    }
}
