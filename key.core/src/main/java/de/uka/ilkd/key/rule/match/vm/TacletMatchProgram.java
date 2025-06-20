/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.rule.match.vm;

import java.util.ArrayList;

import de.uka.ilkd.key.java.JavaProgramElement;
import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.logic.Term;
import de.uka.ilkd.key.logic.op.ElementaryUpdate;
import de.uka.ilkd.key.logic.op.FormulaSV;
import de.uka.ilkd.key.logic.op.ModalOperatorSV;
import de.uka.ilkd.key.logic.op.Modality;
import de.uka.ilkd.key.logic.op.Operator;
import de.uka.ilkd.key.logic.op.ProgramSV;
import de.uka.ilkd.key.logic.op.QuantifiableVariable;
import de.uka.ilkd.key.logic.op.SortDependingFunction;
import de.uka.ilkd.key.logic.op.TermSV;
import de.uka.ilkd.key.logic.op.UpdateSV;
import de.uka.ilkd.key.logic.op.VariableSV;
import de.uka.ilkd.key.rule.MatchConditions;
import de.uka.ilkd.key.rule.match.vm.instructions.Instruction;
import de.uka.ilkd.key.rule.match.vm.instructions.MatchInstruction;
import de.uka.ilkd.key.rule.match.vm.instructions.MatchSchemaVariableInstruction;

import org.key_project.logic.LogicServices;
import org.key_project.logic.op.sv.SchemaVariable;
import org.key_project.util.collection.ImmutableArray;

/**
 * Instances of this class represent programs for matching a term against a given pattern. The
 * programs are specialised for a certain pattern.
 *
 * To create such a program use the static method {@link #createProgram(Term)} and provide as
 * argument the pattern for which you want to get a matcher.
 *
 * The program is executed by invoking
 * {@link TacletMatchProgram#match(Term, MatchConditions, Services)}.
 */
public class TacletMatchProgram {

    /**
     * creates a matcher for the given pattern
     *
     * @param pattern the {@link Term} specifying the pattern
     * @return the specialized matcher for the given pattern
     */
    public static TacletMatchProgram createProgram(Term pattern) {
        ArrayList<MatchInstruction> program = new ArrayList<>();
        createProgram(pattern, program);
        return new TacletMatchProgram(program.toArray(new MatchInstruction[0]));
    }

    /** the skip program (matches anything) */
    public static final TacletMatchProgram EMPTY_PROGRAM =
        new TacletMatchProgram(new MatchInstruction[0]);

    /** the instructions of the program */
    private final MatchInstruction[] instruction;

    /** creates an instance of the matcher consisting of the instruction */
    private TacletMatchProgram(MatchInstruction[] instruction) {
        this.instruction = instruction;
    }

    /**
     * returns the instruction for the specified variable
     *
     * @param op the {@link SchemaVariable} for which to get the instruction
     * @return the instruction for the specified variable
     */
    public static MatchSchemaVariableInstruction<? extends SchemaVariable> getMatchInstructionForSV(
            SchemaVariable op) {
        MatchSchemaVariableInstruction<? extends SchemaVariable> instruction;

        if (op instanceof FormulaSV formulaSV) {
            instruction = Instruction.matchFormulaSV(formulaSV);
        } else if (op instanceof TermSV termSV) {
            instruction = Instruction.matchTermSV(termSV);
        } else if (op instanceof VariableSV variableSV) {
            instruction = Instruction.matchVariableSV(variableSV);
        } else if (op instanceof ProgramSV programSV) {
            instruction = Instruction.matchProgramSV(programSV);
        } else if (op instanceof UpdateSV updateSV) {
            instruction = Instruction.matchUpdateSV(updateSV);
        } else {
            throw new IllegalArgumentException(
                "Do not know how to match " + op + " of type " + op.getClass());
        }
        return instruction;
    }



    /**
     * creates a matching program for the given pattern. It appends the necessary match instruction
     * to the given list of instructions
     *
     * @param pattern the Term used as pattern for which to create a matcher
     * @param program the list of {@link MatchInstruction} to which the instructions for matching
     *        {@code pattern} are added.
     */
    private static void createProgram(Term pattern, ArrayList<MatchInstruction> program) {
        final Operator op = pattern.op();

        final ImmutableArray<QuantifiableVariable> boundVars = pattern.boundVars();

        if (!boundVars.isEmpty()) {
            program.add(Instruction.matchAndBindVariables(boundVars));
        }

        if (pattern.hasLabels()) {
            program.add(Instruction.matchTermLabelSV(pattern.getLabels()));
        }

        if (op instanceof SchemaVariable) {
            program.add(getMatchInstructionForSV((SchemaVariable) op));
        } else if (op instanceof SortDependingFunction) {
            program.add(Instruction.matchSortDependingFunction((SortDependingFunction) op));
        } else if (op instanceof ElementaryUpdate) {
            program.add(Instruction.matchElementaryUpdate((ElementaryUpdate) op));
        } else if (op instanceof Modality mod) {
            final var kind = mod.kind();
            if (kind instanceof ModalOperatorSV sv) {
                program.add(Instruction.matchModalOperatorSV(sv));
            } else {
                program.add(Instruction.matchModalOperator(mod));
            }
            final JavaProgramElement patternPrg = pattern.javaBlock().program();
            program.add(Instruction.matchProgram(patternPrg));
        } else {
            program.add(Instruction.matchOp(op));
        }

        for (int i = 0; i < pattern.arity(); i++) {
            createProgram(pattern.sub(i), program);
        }

        if (!boundVars.isEmpty()) {
            program.add(Instruction.unbindVariables(boundVars));
        }

    }


    /**
     * executes the program and tries to match the provided term; additional restrictions are
     * provided via match conditions. The returned conditions are either {@code null} if no match is
     * possible or {@link MatchConditions} which extends the given conditions by additional
     * constraints (e.g., instantiations of schemavariables) such that they describe the found match
     *
     * @param p_toMatch the {@link Term} to match
     * @param p_matchCond the initial {@link MatchConditions} which have to be satisfied in addition
     *        to those generated by this match
     * @param services the {@link Services}
     * @return {@code null} if no match was found or the match result
     */
    public MatchConditions match(Term p_toMatch, MatchConditions p_matchCond,
            LogicServices services) {

        MatchConditions mc = p_matchCond;

        final TermNavigator navi = TermNavigator.get(p_toMatch);
        int instrPtr = 0;
        while (mc != null && instrPtr < instruction.length && navi.hasNext()) {
            mc = instruction[instrPtr].match(navi, mc, services);
            instrPtr++;
        }
        navi.release();
        return mc;
    }

    @Override
    public String toString() {
        String s = "MatchProgram {" + System.lineSeparator();
        for (var inst : instruction) {
            s += "    " + inst.toString() + System.lineSeparator();
        }

        s += "}" + System.lineSeparator();

        return s;
    }

}
