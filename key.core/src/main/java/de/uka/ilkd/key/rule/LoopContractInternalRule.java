/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.rule;

import java.util.List;
import java.util.Map;

import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.logic.Term;
import de.uka.ilkd.key.logic.TermServices;
import de.uka.ilkd.key.logic.label.TermLabelState;
import de.uka.ilkd.key.logic.op.LocationVariable;
import de.uka.ilkd.key.proof.Goal;
import de.uka.ilkd.key.rule.AuxiliaryContractBuilders.ConditionsAndClausesBuilder;
import de.uka.ilkd.key.rule.AuxiliaryContractBuilders.GoalsConfigurator;
import de.uka.ilkd.key.rule.AuxiliaryContractBuilders.UpdatesBuilder;
import de.uka.ilkd.key.rule.AuxiliaryContractBuilders.VariablesCreatorAndRegistrar;
import de.uka.ilkd.key.speclang.AuxiliaryContract;
import de.uka.ilkd.key.speclang.LoopContract;
import de.uka.ilkd.key.util.MiscTools;

import org.key_project.logic.Name;
import org.key_project.logic.op.Function;
import org.key_project.prover.rules.RuleAbortException;
import org.key_project.prover.rules.RuleApp;
import org.key_project.prover.sequent.PosInOccurrence;
import org.key_project.util.collection.ImmutableList;
import org.key_project.util.collection.ImmutableSet;
import org.key_project.util.java.ArrayUtil;

import org.jspecify.annotations.NonNull;

/**
 * <p>
 * Rule for the application of {@link LoopContract}s.
 * </p>
 *
 * <p>
 * This splits the goal into two branches:
 * <ol>
 * <li>Validity</li>
 * <li>Precondition</li>
 * <li>Usage</li>
 * </ol>
 * </p>
 *
 * @see LoopContractInternalBuiltInRuleApp
 *
 * @author lanzinger
 */
public final class LoopContractInternalRule extends AbstractLoopContractRule {

    /**
     * The only instance of this class.
     */
    public static final LoopContractInternalRule INSTANCE = new LoopContractInternalRule();

    /**
     * This rule's name.
     */
    private static final Name NAME = new Name("Loop Contract (Internal)");

    /**
     * @see #getLastFocusTerm()
     */
    private Term lastFocusTerm;

    /**
     * @see #getLastInstantiation()
     */
    private Instantiation lastInstantiation;

    private LoopContractInternalRule() {
    }

    /**
     * Creates preconditions.
     *
     * @param selfTerm the self term.
     * @param contract the loop contract being applied.
     * @param heaps the heaps.
     * @param localInVariables all free program variables in the block.
     * @param conditionsAndClausesBuilder a ConditionsAndClausesBuilder.
     * @param services services.
     * @return the preconditions.
     */
    private static Term[] createPreconditions(final Term selfTerm, final LoopContract contract,
            final List<LocationVariable> heaps,
            final ImmutableSet<LocationVariable> localInVariables,
            final ConditionsAndClausesBuilder conditionsAndClausesBuilder,
            final Services services) {
        final Term precondition = conditionsAndClausesBuilder.buildPrecondition();
        final Term wellFormedHeapsCondition =
            conditionsAndClausesBuilder.buildWellFormedHeapsCondition();
        final Term reachableInCondition =
            conditionsAndClausesBuilder.buildReachableInCondition(localInVariables);
        final Term selfConditions = conditionsAndClausesBuilder.buildSelfConditions(heaps,
            contract.getMethod(), contract.getKJT(), selfTerm, services);
        return new Term[] { precondition, wellFormedHeapsCondition, reachableInCondition,
            selfConditions };
    }

    /**
     * Creates postconditions for the current loop iteration.
     *
     * @param modifiableClauses the loop's modifiable clauses.
     * @param freeModifiableClauses the loop's free modifiable clauses.
     * @param conditionsAndClausesBuilder ConditionsAndClausesBuilder.
     * @return the postconditions for the current loop iteration.
     */
    private static Term[] createPostconditions(
            final Map<LocationVariable, Term> modifiableClauses,
            final Map<LocationVariable, Term> freeModifiableClauses,
            final ConditionsAndClausesBuilder conditionsAndClausesBuilder) {
        final Term postcondition = conditionsAndClausesBuilder.buildPostcondition();
        final Term frameCondition =
            conditionsAndClausesBuilder.buildFrameCondition(
                modifiableClauses, freeModifiableClauses);
        return new Term[] { postcondition, frameCondition };
    }



    /**
     * Creates postconditions for the next loop iteration.
     *
     * @param selfTerm the self term.
     * @param contract the loop contract being applied.
     * @param heaps the heaps.
     * @param nextVariables the variables for the next loop iteration.
     * @param modifiableClauses the modified clauses.
     * @param freeModifiableClauses the free modified clauses.
     * @param services services.
     *        * @return the postconditions for the next loop iteration.
     */
    private static Term[] createPostconditionsNext(
            final Term selfTerm,
            final LoopContract contract,
            final List<LocationVariable> heaps,
            final LoopContract.Variables nextVariables,
            final Map<LocationVariable, Term> modifiableClauses,
            final Map<LocationVariable, Term> freeModifiableClauses,
            final Services services) {
        final Term nextPostcondition =
            new ConditionsAndClausesBuilder(contract, heaps, nextVariables, selfTerm, services)
                    .buildPostcondition();
        final Term nextFrameCondition =
            new ConditionsAndClausesBuilder(contract, heaps, nextVariables, selfTerm, services)
                    .buildFrameCondition(modifiableClauses, freeModifiableClauses);
        return new Term[] { nextPostcondition, nextFrameCondition };
    }

    /**
     *
     * @param heaps the heaps.
     * @param updatesBuilder an update builder.
     * @param instantiation the instantiation for the current rule application.
     * @param services services.
     * @return the update for the validity branch.
     */
    private static Term createContext(final List<LocationVariable> heaps,
            final UpdatesBuilder updatesBuilder, final Instantiation instantiation,
            final Services services) {
        return services.getTermBuilder().sequential(updatesBuilder.buildOuterRemembranceUpdate(),
            instantiation.update());
    }

    /**
     *
     * @param postconditions the postconditions.
     * @param anonOutHeaps the heaps used in the anonOut update.
     * @param localOutVariables all free program variables modified by the block.
     * @param conditionsAndClausesBuilder a ConditionsAndClausesBuilder.
     * @return preconditions for the usage branch.
     */
    private static Term[] createUsageAssumptions(final Term[] postconditions,
            final Map<LocationVariable, Function> anonOutHeaps,
            final ImmutableSet<LocationVariable> localOutVariables,
            final ConditionsAndClausesBuilder conditionsAndClausesBuilder) {
        final Term wellFormedAnonymisationHeapsCondition =
            conditionsAndClausesBuilder.buildWellFormedAnonymisationHeapsCondition(anonOutHeaps);
        final Term reachableOutCondition =
            conditionsAndClausesBuilder.buildReachableOutCondition(localOutVariables);
        final Term atMostOneFlagSetCondition =
            conditionsAndClausesBuilder.buildAtMostOneFlagSetCondition();
        return new Term[] { postconditions[0], wellFormedAnonymisationHeapsCondition,
            reachableOutCondition, atMostOneFlagSetCondition };
    }

    /**
     *
     * @param instantiation the instantiation.
     * @param heaps the heaps.
     * @param anonOutHeaps the heaps used in the anonOut update.
     * @param modifiableClauses the modifiable clauses.
     * @param updatesBuilder an update builder
     * @return the updates for the usage branch.
     */
    private static Term[] createUpdates(final Instantiation instantiation,
            final List<LocationVariable> heaps,
            final Map<LocationVariable, Function> anonOutHeaps,
            final Map<LocationVariable, Term> modifiableClauses,
            final UpdatesBuilder updatesBuilder) {
        final Term contextUpdate = instantiation.update();
        final Term remembranceUpdate = updatesBuilder.buildRemembranceUpdate(heaps);
        final Term anonymisationUpdate =
            updatesBuilder.buildAnonOutUpdate(anonOutHeaps, modifiableClauses);
        return new Term[] { contextUpdate, remembranceUpdate, anonymisationUpdate };
    }

    /**
     * @param goal the current goal.
     * @param selfTerm the self term.
     * @param contract the contract being applied.
     * @return the variables for both the current and the next loop iteration.
     */
    private static AuxiliaryContract.Variables[] createVars(final Goal goal, final Term selfTerm,
            final LoopContract contract) {
        final LoopContract.Variables variables =
            new VariablesCreatorAndRegistrar(goal, contract.getPlaceholderVariables())
                    .createAndRegister(selfTerm, true);

        final LoopContract.Variables nextVariables =
            new VariablesCreatorAndRegistrar(goal, variables)
                    .createAndRegisterCopies("_NEXT");
        return new LoopContract.Variables[] { variables, nextVariables };
    }

    @Override
    public Term getLastFocusTerm() {
        return lastFocusTerm;
    }

    @Override
    protected void setLastFocusTerm(Term lastFocusTerm) {
        this.lastFocusTerm = lastFocusTerm;
    }

    @Override
    public Instantiation getLastInstantiation() {
        return lastInstantiation;
    }

    @Override
    public Name name() {
        return NAME;
    }

    @Override
    protected void setLastInstantiation(Instantiation lastInstantiation) {
        this.lastInstantiation = lastInstantiation;
    }

    @Override
    public LoopContractInternalBuiltInRuleApp createApp(final PosInOccurrence occurrence,
            TermServices services) {
        return new LoopContractInternalBuiltInRuleApp(this, occurrence);
    }

    @Override
    public @NonNull ImmutableList<Goal> apply(final Goal goal,
            final RuleApp ruleApp) throws RuleAbortException {
        assert ruleApp instanceof LoopContractInternalBuiltInRuleApp;
        LoopContractInternalBuiltInRuleApp application =
            (LoopContractInternalBuiltInRuleApp) ruleApp;

        final Instantiation instantiation =
            instantiate((Term) application.posInOccurrence().subTerm(), goal);
        LoopContract contract = application.getContract();

        assert contract.isOnBlock() && contract.getBlock().equals(instantiation.statement())
                || !contract.isOnBlock() && contract.getLoop().equals(instantiation.statement());

        var services = goal.getOverlayServices();
        contract = contract.replaceEnhancedForVariables(contract.getBlock(), services);
        contract.setInstantiationSelf(instantiation.self());

        final List<LocationVariable> heaps = application.getHeapContext();
        final ImmutableSet<LocationVariable> localInVariables =
            MiscTools.getLocalIns(instantiation.statement(), services);
        final ImmutableSet<LocationVariable> localOutVariables =
            MiscTools.getLocalOuts(instantiation.statement(), services);
        final Map<LocationVariable, Function> anonOutHeaps =
            createAndRegisterAnonymisationVariables(heaps, contract, services);
        final LoopContract.Variables[] vars =
            createVars(goal, instantiation.self(), contract);

        final ConditionsAndClausesBuilder conditionsAndClausesBuilder =
            new ConditionsAndClausesBuilder(contract, heaps, vars[0], instantiation.self(),
                services);
        final Map<LocationVariable, Term> modifiableClauses =
            conditionsAndClausesBuilder.buildModifiableClauses();
        final Map<LocationVariable, Term> freeModifiableClauses =
            conditionsAndClausesBuilder.buildFreeModifiableClauses();
        final Term[] assumptions = createPreconditions(instantiation.self(), contract, heaps,
            localInVariables, conditionsAndClausesBuilder, services);
        final Term freePrecondition = conditionsAndClausesBuilder.buildFreePrecondition();
        final Term[] postconditions =
            createPostconditions(modifiableClauses, freeModifiableClauses,
                conditionsAndClausesBuilder);
        final Term freePostcondition = conditionsAndClausesBuilder.buildFreePostcondition();
        final Term[] usageAssumptions = createUsageAssumptions(postconditions, anonOutHeaps,
            localOutVariables, conditionsAndClausesBuilder);
        final Term decreasesCheck = conditionsAndClausesBuilder.buildDecreasesCheck();
        final Term[] postconditionsNext = createPostconditionsNext(
            instantiation.self(), contract,
            heaps, vars[1], modifiableClauses, freeModifiableClauses, services);
        final UpdatesBuilder updatesBuilder = new UpdatesBuilder(vars[0], services);
        final Term[] updates =
            createUpdates(instantiation, heaps, anonOutHeaps, modifiableClauses, updatesBuilder);
        final Term nextRemembranceUpdate =
            new UpdatesBuilder(vars[1], services).buildRemembranceUpdate(heaps);
        final Term context = createContext(heaps, updatesBuilder, instantiation, services);
        final GoalsConfigurator configurator =
            new GoalsConfigurator(application, new TermLabelState(), instantiation,
                contract.getLabels(), vars[0], application.posInOccurrence(), services, this);
        final ImmutableList<Goal> result = goal.split(3);

        configurator.setUpPreconditionGoal(result.tail().head(), updates[0], assumptions);
        configurator.setUpUsageGoal(result.head(), updates,
            ArrayUtil.add(usageAssumptions, freePostcondition));
        final LocationVariable exceptionParameter =
            createLocalVariable("e", vars[0].exception.getKeYJavaType(), services);
        configurator.setUpLoopValidityGoal(goal, contract, context, updates[1],
            nextRemembranceUpdate, anonOutHeaps, modifiableClauses,
            freeModifiableClauses,
            ArrayUtil.add(assumptions, freePrecondition), decreasesCheck, postconditions,
            postconditionsNext, exceptionParameter, vars[0].termify(instantiation.self()), vars[1]);

        return result;
    }
}
