/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.strategy.quantifierHeuristics;

import java.util.Iterator;

import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.ldt.IntegerLDT;
import de.uka.ilkd.key.logic.op.Equality;
import de.uka.ilkd.key.logic.op.Junctor;
import de.uka.ilkd.key.proof.Goal;
import de.uka.ilkd.key.rule.TacletApp;
import de.uka.ilkd.key.rule.metaconstruct.arith.Polynomial;
import de.uka.ilkd.key.strategy.feature.SmallerThanFeature;

import org.key_project.logic.Term;
import org.key_project.prover.sequent.PosInOccurrence;
import org.key_project.prover.strategy.costbased.MutableState;
import org.key_project.prover.strategy.costbased.feature.Feature;
import org.key_project.prover.strategy.costbased.termProjection.ProjectionToTerm;

public class LiteralsSmallerThanFeature extends SmallerThanFeature {

    private final ProjectionToTerm<Goal> left, right;
    private final IntegerLDT numbers;

    private final QuanEliminationAnalyser quanAnalyser = new QuanEliminationAnalyser();


    private LiteralsSmallerThanFeature(ProjectionToTerm<Goal> left, ProjectionToTerm<Goal> right,
            IntegerLDT numbers) {
        this.left = left;
        this.right = right;
        this.numbers = numbers;
    }

    public static Feature create(ProjectionToTerm<Goal> left, ProjectionToTerm<Goal> right,
            IntegerLDT numbers) {
        return new LiteralsSmallerThanFeature(left, right, numbers);
    }

    @Override
    protected boolean filter(TacletApp app, PosInOccurrence pos,
            Goal goal, MutableState mState) {
        final Term leftTerm = left.toTerm(app, pos, goal, mState);
        final Term rightTerm = right.toTerm(app, pos, goal, mState);

        return compareTerms(leftTerm, rightTerm, pos, goal);
    }

    protected boolean compareTerms(Term leftTerm, Term rightTerm,
            PosInOccurrence pos, Goal goal) {
        final LiteralCollector m1 = new LiteralCollector();
        m1.collect(leftTerm);
        final LiteralCollector m2 = new LiteralCollector();
        m2.collect(rightTerm);

        return lessThan(m1.getResult(), m2.getResult(), pos, goal);
    }

    /**
     * this overwrites the method of <code>SmallerThanFeature</code>
     */
    @Override
    protected boolean lessThan(Term t1, Term t2, PosInOccurrence focus, Goal goal) {
        final int t1Def = quanAnalyser.eliminableDefinition(t1, focus);
        final int t2Def = quanAnalyser.eliminableDefinition(t2, focus);

        if (t1Def > t2Def) {
            return true;
        }
        if (t1Def < t2Def) {
            return false;
        }

        // HACK: we move literals that do not contain any variables to the left,
        // so that they can be moved out of the scope of the quantifiers
        if (t1.freeVars().isEmpty()) {
            if (!t2.freeVars().isEmpty()) {
                return false;
            }
        } else {
            if (t2.freeVars().isEmpty()) {
                return true;
            }
        }

        t1 = discardNegation(t1);
        t2 = discardNegation(t2);

        if (isBinaryIntRelation(t2)) {
            if (!isBinaryIntRelation(t1)) {
                return true;
            }

            int c = compare(t1.sub(0), t2.sub(0));
            if (c < 0) {
                return true;
            }
            if (c > 0) {
                return false;
            }

            c = comparePolynomials(t1.sub(1), t2.sub(1));
            if (c < 0) {
                return true;
            }
            if (c > 0) {
                return false;
            }

            final Services services = goal.proof().getServices();
            final Polynomial t1RHS = Polynomial.create(t1.sub(1), services);
            final Polynomial t2RHS = Polynomial.create(t2.sub(1), services);
            if (t1RHS.valueLess(t2RHS)) {
                return true;
            }
            if (t2RHS.valueLess(t1RHS)) {
                return false;
            }

            c = formulaKind(t1) - formulaKind(t2);
            if (c < 0) {
                return true;
            }
            if (c > 0) {
                return false;
            }
        } else {
            if (isBinaryIntRelation(t1)) {
                return false;
            }
        }

        return super.lessThan(t1, t2, focus, goal);
    }

    private int comparePolynomials(Term t1, Term t2) {
        final Iterator<Term> it1 = new MonomialIterator(t1);
        final Iterator<Term> it2 = new MonomialIterator(t2);

        while (true) {
            if (it1.hasNext()) {
                if (!it2.hasNext()) {
                    return 1;
                }
            } else {
                if (it2.hasNext()) {
                    return -1;
                } else {
                    return 0;
                }
            }

            final int c = compare(it1.next(), it2.next());
            if (c != 0) {
                return c;
            }
        }
    }

    private Term discardNegation(Term t) {
        while (t.op() == Junctor.NOT) {
            t = t.sub(0);
        }
        return t;
    }

    private boolean isBinaryIntRelation(Term t) {
        return formulaKind(t) >= 0;
    }

    private int formulaKind(Term t) {
        final var op = t.op();
        if (op == numbers.getLessOrEquals()) {
            return 1;
        }
        if (op == numbers.getGreaterOrEquals()) {
            return 2;
        }
        if (op == Equality.EQUALS && t.sub(0).sort() == numbers.targetSort()
                && t.sub(1).sort() == numbers.targetSort()) {
            return 3;
        }
        return -1;
    }

    private class MonomialIterator implements Iterator<Term> {
        private Term polynomial;
        private Term nextMonomial = null;

        private MonomialIterator(Term polynomial) {
            this.polynomial = polynomial;
            findNextMonomial();
        }

        private void findNextMonomial() {
            while (nextMonomial == null && polynomial != null) {
                if (polynomial.op() == numbers.getAdd()) {
                    nextMonomial = polynomial.sub(1);
                    polynomial = polynomial.sub(0);
                } else {
                    nextMonomial = polynomial;
                    polynomial = null;
                }

                if (nextMonomial.op() == numbers.getNumberSymbol()) {
                    nextMonomial = null;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return nextMonomial != null;
        }

        @Override
        public Term next() {
            final Term res = nextMonomial;
            nextMonomial = null;
            findNextMonomial();
            return res;
        }

        /**
         * throw an unsupported operation exception
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class LiteralCollector extends Collector {
        protected void collect(Term te) {
            final var op = te.op();
            if (op == Junctor.OR) {
                collect(te.sub(0));
                collect(te.sub(1));
            } else {
                addTerm(te);
            }
        }
    }

}
