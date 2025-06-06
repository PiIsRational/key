/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.strategy;

import de.uka.ilkd.key.proof.Goal;
import de.uka.ilkd.key.rule.TacletApp;
import de.uka.ilkd.key.strategy.feature.NonDuplicateAppFeature;

import org.key_project.logic.Name;
import org.key_project.prover.proof.ProofGoal;
import org.key_project.prover.proof.rulefilter.RuleFilter;
import org.key_project.prover.proof.rulefilter.TacletFilter;
import org.key_project.prover.rules.RuleApp;
import org.key_project.prover.sequent.PosInOccurrence;
import org.key_project.prover.strategy.costbased.MutableState;
import org.key_project.prover.strategy.costbased.NumberRuleAppCost;
import org.key_project.prover.strategy.costbased.RuleAppCost;
import org.key_project.prover.strategy.costbased.TopRuleAppCost;

import org.jspecify.annotations.NonNull;

/**
 * Trivial implementation of the Strategy interface that uses only the goal time to determine the
 * cost of a RuleApp. A TacletFilter is used to filter out RuleApps.
 */
public class SimpleFilteredStrategy implements Strategy<Goal> {

    private static final Name NAME = new Name("Simple ruleset");

    private final RuleFilter ruleFilter;

    private static final long IF_NOT_MATCHED_MALUS = 0; // this should be a feature

    public SimpleFilteredStrategy() {
        this(TacletFilter.TRUE);
    }

    public SimpleFilteredStrategy(RuleFilter p_ruleFilter) {
        ruleFilter = p_ruleFilter;
    }

    @Override
    public Name name() {
        return NAME;
    }

    /**
     * Evaluate the cost of a <code>RuleApp</code>.
     *
     * @return the cost of the rule application expressed as a <code>RuleAppCost</code> object.
     *         <code>TopRuleAppCost.INSTANCE</code> indicates that the rule shall not be applied at
     *         all (it is discarded by the strategy).
     */
    @Override
    public <Goal extends ProofGoal<@NonNull Goal>> RuleAppCost computeCost(RuleApp app,
            PosInOccurrence pio,
            Goal goal,
            MutableState mState) {
        if (app instanceof TacletApp && !ruleFilter.filter(app.rule())) {
            return TopRuleAppCost.INSTANCE;
        }

        RuleAppCost res = NonDuplicateAppFeature.INSTANCE.computeCost(app, pio, goal, mState);
        if (res == TopRuleAppCost.INSTANCE) {
            return res;
        }

        long cost = ((de.uka.ilkd.key.proof.Goal) goal).getTime();
        if (app instanceof TacletApp tacletApp && !tacletApp.assumesInstantionsComplete()) {
            cost += IF_NOT_MATCHED_MALUS;
        }

        return NumberRuleAppCost.create(cost);
    }

    /**
     * Re-Evaluate a <code>RuleApp</code>. This method is called immediately before a rule is really
     * applied
     *
     * @return true iff the rule should be applied, false otherwise
     */
    @Override
    public boolean isApprovedApp(RuleApp app, PosInOccurrence pio,
            Goal goal) {
        // do not apply a rule twice
        return !(app instanceof TacletApp) || NonDuplicateAppFeature.INSTANCE.computeCost(app, pio,
            goal, new MutableState()) != TopRuleAppCost.INSTANCE;
    }

    @Override
    public void instantiateApp(RuleApp app, PosInOccurrence pio, Goal goal,
            RuleAppCostCollector collector) {}

    @Override
    public boolean isStopAtFirstNonCloseableGoal() {
        return false;
    }
}
