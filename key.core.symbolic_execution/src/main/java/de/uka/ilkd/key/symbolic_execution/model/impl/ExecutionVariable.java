/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.symbolic_execution.model.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.logic.Term;
import de.uka.ilkd.key.logic.TermBuilder;
import de.uka.ilkd.key.logic.op.IProgramVariable;
import de.uka.ilkd.key.logic.op.Operator;
import de.uka.ilkd.key.proof.Goal;
import de.uka.ilkd.key.proof.Node;
import de.uka.ilkd.key.proof.Proof;
import de.uka.ilkd.key.proof.init.InitConfig;
import de.uka.ilkd.key.proof.init.ProofInputException;
import de.uka.ilkd.key.proof.mgt.ProofEnvironment;
import de.uka.ilkd.key.strategy.StrategyProperties;
import de.uka.ilkd.key.symbolic_execution.model.IExecutionNode;
import de.uka.ilkd.key.symbolic_execution.model.IExecutionValue;
import de.uka.ilkd.key.symbolic_execution.model.IExecutionVariable;
import de.uka.ilkd.key.symbolic_execution.util.SymbolicExecutionSideProofUtil;
import de.uka.ilkd.key.symbolic_execution.util.SymbolicExecutionUtil;
import de.uka.ilkd.key.symbolic_execution.util.SymbolicExecutionUtil.SiteProofVariableValueInput;

import org.key_project.prover.engine.impl.ApplyStrategyInfo;
import org.key_project.prover.sequent.PosInOccurrence;
import org.key_project.util.collection.ImmutableList;

/**
 * The default implementation of {@link IExecutionVariable}.
 *
 * @author Martin Hentschel
 */
public class ExecutionVariable extends AbstractExecutionVariable {
    /**
     * The parent {@link IExecutionNode} which provides this {@link ExecutionVariable}.
     */
    private final IExecutionNode<?> parentNode;

    /**
     * The {@link ExecutionValue} from which the array length was computed.
     */
    private final ExecutionValue lengthValue;

    /**
     * The possible values of this {@link IExecutionValue}.
     */
    private ExecutionValue[] values;

    /**
     * Constructor for a "normal" value.
     *
     * @param parentNode The parent {@link IExecutionNode} which provides this
     *        {@link ExecutionVariable}.
     * @param programVariable The represented {@link IProgramVariable} which value is shown.
     * @param additionalCondition An optional additional condition to consider.
     */
    public ExecutionVariable(IExecutionNode<?> parentNode, Node proofNode,
            PosInOccurrence modalityPIO,
            IProgramVariable programVariable,
            Term additionalCondition) {
        this(parentNode, proofNode, modalityPIO, null, programVariable, additionalCondition);
    }

    /**
     * Constructor for a "normal" child value.
     *
     * @param parentNode The parent {@link IExecutionNode} which provides this
     *        {@link ExecutionVariable}.
     * @param parentValue The parent {@link ExecutionValue} or {@code null} if not available.
     * @param programVariable The represented {@link IProgramVariable} which value is shown.
     * @param additionalCondition An optional additional condition to consider.
     */
    public ExecutionVariable(IExecutionNode<?> parentNode, Node proofNode,
            PosInOccurrence modalityPIO, ExecutionValue parentValue,
            IProgramVariable programVariable, Term additionalCondition) {
        super(parentNode.getSettings(), proofNode, programVariable, parentValue, null,
            additionalCondition, modalityPIO);
        assert programVariable != null;
        assert modalityPIO != null;
        this.parentNode = parentNode;
        this.lengthValue = null;
    }

    /**
     * Constructor for an array cell value.
     *
     * @param parentNode The parent {@link IExecutionNode} which provides this
     *        {@link ExecutionVariable}.
     * @param parentValue The parent {@link ExecutionValue} or {@code null} if not available.
     * @param arrayIndex The index in the parent array.
     * @param lengthValue The {@link ExecutionValue} from which the array length was computed.
     * @param additionalCondition An optional additional condition to consider.
     */
    public ExecutionVariable(IExecutionNode<?> parentNode, Node proofNode,
            PosInOccurrence modalityPIO, ExecutionValue parentValue, Term arrayIndex,
            ExecutionValue lengthValue, Term additionalCondition) {
        super(parentNode.getSettings(), proofNode, null, parentValue, arrayIndex,
            additionalCondition, modalityPIO);
        assert modalityPIO != null;
        this.parentNode = parentNode;
        this.lengthValue = lengthValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExecutionValue[] getValues() throws ProofInputException {
        synchronized (this) {
            if (values == null) {
                values = lazyComputeValues();
            }
            return values;
        }
    }

    /**
     * Computes the value for {@link #getValues()} lazily when the method is called the first time.
     *
     * @throws ProofInputException Occurred Exception.
     */
    protected ExecutionValue[] lazyComputeValues() throws ProofInputException {
        InitConfig initConfig = getInitConfig();
        if (initConfig != null) { // Otherwise proof is disposed.
            // New OneStepSimplifier is required because it has an internal state and the default
            // instance can't be used parallel.
            final ProofEnvironment sideProofEnv = SymbolicExecutionSideProofUtil
                    .cloneProofEnvironmentWithOwnOneStepSimplifier(initConfig, true);
            final Services services = sideProofEnv.getServicesForEnvironment();
            final TermBuilder tb = services.getTermBuilder();
            // Start site proof to extract the value of the result variable.
            SiteProofVariableValueInput sequentToProve;
            Term siteProofSelectTerm = null;
            Term siteProofCondition;
            if (getAdditionalCondition() != null) {
                siteProofCondition = getAdditionalCondition();
            } else {
                siteProofCondition = tb.tt();
            }
            if (getParentValue() != null
                    || SymbolicExecutionUtil.isStaticVariable(getProgramVariable())) {
                siteProofSelectTerm = createSelectTerm();
                if (getParentValue() != null) { // Is null at static variables
                    siteProofCondition =
                        tb.and(siteProofCondition, getParentValue().getCondition());
                }
                if (lengthValue != null) {
                    siteProofCondition = tb.and(siteProofCondition, lengthValue.getCondition());
                }
                sequentToProve =
                    SymbolicExecutionUtil.createExtractTermSequent(services, getProofNode(),
                        getModalityPIO(), siteProofCondition, siteProofSelectTerm, true);
            } else {
                sequentToProve = SymbolicExecutionUtil.createExtractVariableValueSequent(services,
                    getProofNode(), getModalityPIO(), siteProofCondition, getProgramVariable());
            }
            ApplyStrategyInfo info = SymbolicExecutionSideProofUtil.startSideProof(getProof(),
                sideProofEnv, sequentToProve.getSequentToProve(), StrategyProperties.METHOD_NONE,
                StrategyProperties.LOOP_NONE, StrategyProperties.QUERY_OFF,
                StrategyProperties.SPLITTING_DELAYED);
            try {
                return instantiateValuesFromSideProof(initConfig, services, tb, info,
                    sequentToProve.getOperator(), siteProofSelectTerm, siteProofCondition);
            } finally {
                SymbolicExecutionSideProofUtil.disposeOrStore(
                    "Value computation on node " + getProofNode().serialNr(), info);
            }
        } else {
            return null;
        }
    }

    /**
     * Analyzes the side proof defined by the {@link ApplyStrategyInfo} and creates
     * {@link ExecutionValue}s from it.
     *
     * @param initConfig The {@link InitConfig} of the side proof.
     * @param services The {@link Services} of the side proof.
     * @param tb The {@link TermBuilder} of the side proof.
     * @param info The side proof.
     * @param resultOperator The {@link Operator} of the result predicate.
     * @param siteProofSelectTerm The queried value.
     * @param siteProofCondition The condition under which the value is queried.
     * @return The created {@link ExecutionValue} instances.
     * @throws ProofInputException Occurred Exception.
     */
    protected ExecutionValue[] instantiateValuesFromSideProof(InitConfig initConfig,
            Services services, TermBuilder tb, ApplyStrategyInfo<Proof, Goal> info,
            Operator resultOperator,
            Term siteProofSelectTerm, Term siteProofCondition) throws ProofInputException {
        List<ExecutionValue> result =
            new ArrayList<>(info.getProof().openGoals().size());
        // Group values of the branches
        Map<Term, List<Goal>> valueMap = new LinkedHashMap<>();
        List<Goal> unknownValues = new LinkedList<>();
        groupGoalsByValue(info.getProof().openGoals(), resultOperator, siteProofSelectTerm,
            siteProofCondition, valueMap, unknownValues, services);
        // Instantiate child values
        for (Entry<Term, List<Goal>> valueEntry : valueMap.entrySet()) {
            Term value = valueEntry.getKey();
            if (isValidValue(value)) {
                // Format return vale
                String valueString = formatTerm(value, services);
                // Determine type
                String typeString = value.sort().toString();
                // Compute value condition
                Term condition = computeValueCondition(tb, valueEntry.getValue(), initConfig);
                String conditionString = null;
                if (condition != null) {
                    conditionString = formatTerm(condition, services);
                }
                // Update result
                result.add(new ExecutionValue(getProofNode(), this, false, value, valueString,
                    typeString, condition, conditionString));
            }
        }
        // Instantiate unknown child values
        if (!unknownValues.isEmpty()) {
            // Compute value condition
            Term condition = computeValueCondition(tb, unknownValues, initConfig);
            String conditionString = null;
            if (condition != null) {
                conditionString = formatTerm(condition, services);
            }
            // Update result
            result.add(new ExecutionValue(getProofNode(), this, true,
                null, null, null, condition, conditionString));
        }
        // Return child values as result
        return result.toArray(new ExecutionValue[0]);
    }

    /**
     * Checks if the given {@link Term} represents a valid value.
     *
     * @param value The value to check.
     * @return {@code true} valid value, {@code false} invalid value to be ignored.
     */
    protected boolean isValidValue(Term value) {
        return true;
    }

    /**
     * Groups all {@link Goal}s which provides the same value.
     *
     * @param goals All available {@link Goal}s to group.
     * @param operator The {@link Operator} of the {@link Term} which provides the value.
     * @param services The {@link Services} to use.
     */
    protected void groupGoalsByValue(ImmutableList<Goal> goals, Operator operator,
            Term siteProofSelectTerm, Term siteProofCondition, Map<Term, List<Goal>> valueMap,
            List<Goal> unknownValues, Services services) throws ProofInputException {
        for (Goal goal : goals) {
            // Extract value
            Term value = SymbolicExecutionSideProofUtil.extractOperatorValue(goal, operator);
            assert value != null;
            value = SymbolicExecutionUtil.replaceSkolemConstants(goal.sequent(), value, services);
            // Compute unknown flag if required
            boolean unknownValue = false;
            if (siteProofSelectTerm != null) {
                if (SymbolicExecutionUtil.isNullSort(value.sort(), services)) {
                    unknownValue = SymbolicExecutionUtil.isNull(getProofNode(), siteProofCondition,
                        siteProofSelectTerm); // Check if the symbolic value is not null, if it
                                              // fails the value is treated as unknown
                } else {
                    unknownValue = SymbolicExecutionUtil.isNotNull(getProofNode(),
                        siteProofCondition, siteProofSelectTerm); // Check if the symbolic value is
                                                                  // not null, if it fails the value
                                                                  // is treated as unknown
                }
            }
            // Add to result list
            if (unknownValue) {
                unknownValues.add(goal);
            } else {
                List<Goal> valueList = valueMap.computeIfAbsent(value, k -> new LinkedList<>());
                valueList.add(goal);
            }
        }
    }

    /**
     * Computes the combined path condition of all {@link Goal}s which is the or combination of each
     * path condition per {@link Goal}.
     *
     * @param tb The {@link TermBuilder} to use passed to ensure that it is still available even if
     *        the {@link Proof} is disposed in between.
     * @param valueGoals The {@link Goal}s to compute combined path condition for.
     * @param initConfig The {@link InitConfig} to use.
     * @return The combined path condition.
     * @throws ProofInputException Occurred Exception.
     */
    protected Term computeValueCondition(TermBuilder tb, List<Goal> valueGoals,
            InitConfig initConfig) throws ProofInputException {
        if (!valueGoals.isEmpty()) {
            List<Term> pathConditions = new LinkedList<>();
            Proof proof = null;
            for (Goal valueGoal : valueGoals) {
                pathConditions.add(SymbolicExecutionUtil.computePathCondition(valueGoal.node(),
                    getSettings().simplifyConditions(), false));
                proof = valueGoal.node().proof();
            }
            Term comboundPathCondition = tb.or(pathConditions);
            if (getSettings().simplifyConditions()) {
                comboundPathCondition =
                    SymbolicExecutionUtil.simplify(initConfig, proof, comboundPathCondition);
            }
            comboundPathCondition = SymbolicExecutionUtil.improveReadability(comboundPathCondition,
                initConfig.getServices());
            return comboundPathCondition;
        } else {
            return tb.tt();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Term createSelectTerm() {
        return SymbolicExecutionUtil.createSelectTerm(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExecutionValue getParentValue() {
        return (ExecutionValue) super.getParentValue();
    }

    /**
     * Returns the parent {@link IExecutionNode} which provides this {@link ExecutionVariable}.
     *
     * @return The parent {@link IExecutionNode} which provides this {@link ExecutionVariable}.
     */
    public IExecutionNode<?> getParentNode() {
        return parentNode;
    }

    /**
     * Returns the {@link ExecutionValue} from which the array length was computed.
     *
     * @return The {@link ExecutionValue} from which the array length was computed.
     */
    public ExecutionValue getLengthValue() {
        return lengthValue;
    }
}
