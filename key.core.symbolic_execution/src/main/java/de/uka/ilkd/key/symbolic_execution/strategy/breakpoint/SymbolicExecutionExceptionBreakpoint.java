/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.symbolic_execution.strategy.breakpoint;

import java.util.HashSet;
import java.util.Set;

import de.uka.ilkd.key.java.JavaInfo;
import de.uka.ilkd.key.java.SourceElement;
import de.uka.ilkd.key.java.abstraction.KeYJavaType;
import de.uka.ilkd.key.java.statement.Throw;
import de.uka.ilkd.key.logic.op.LocationVariable;
import de.uka.ilkd.key.proof.Goal;
import de.uka.ilkd.key.proof.Node;
import de.uka.ilkd.key.proof.NodeInfo;
import de.uka.ilkd.key.proof.Proof;
import de.uka.ilkd.key.symbolic_execution.util.SymbolicExecutionUtil;

import org.key_project.prover.rules.RuleApp;
import org.key_project.util.collection.ImmutableList;

/**
 * This{@link SymbolicExecutionExceptionBreakpoint} represents an exception breakpoint and is
 * responsible to tell the debugger to stop execution when the respective breakpoint is hit.
 *
 * @author Marco Drebing
 */
public class SymbolicExecutionExceptionBreakpoint extends AbstractHitCountBreakpoint {
    /**
     * The exception to watch for
     */
    private final String exceptionName;

    /**
     * a Set of Nodes that represent exceptions
     */
    private final Set<Node> exceptionNodes;

    /**
     * a list of nodes of the Symbolic Execution Tree whose children represent exceptions
     */
    private final Set<Node> exceptionParentNodes;

    /**
     * a flag whether to watch for an uncaught exception
     */
    private boolean caught;

    /**
     * a flag whether to suspend on subclasses of the exception aswell
     */
    private boolean suspendOnSubclasses;

    /**
     * a flag to tell whether to stop at uncaught exceptions or not
     */
    private boolean uncaught;

    /**
     * Creates a new {@link AbstractHitCountBreakpoint}.
     *
     * @param proof the {@link Proof} that will be executed and should stop
     * @param exceptionName the name of the exception to watch for
     * @param caught flag to tell if caught exceptions lead to a stop
     * @param uncaught flag to tell if uncaught exceptions lead to a stop
     * @param suspendOnSubclasses flag to tell if the execution should suspend on subclasses of the
     *        exception aswell
     * @param enabled flag if the Breakpoint is enabled
     * @param hitCount the number of hits after which the execution should hold at this breakpoint
     */
    public SymbolicExecutionExceptionBreakpoint(Proof proof, String exceptionName, boolean caught,
            boolean uncaught, boolean suspendOnSubclasses, boolean enabled, int hitCount) {
        super(hitCount, proof, enabled);
        this.exceptionName = exceptionName;
        exceptionNodes = new HashSet<>();
        exceptionParentNodes = new HashSet<>();
        this.caught = caught;
        this.uncaught = uncaught;
        this.suspendOnSubclasses = suspendOnSubclasses;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateState(Goal goal, int maxApplications, long timeout, long startTime,
            int countApplied) {
        if (goal != null) {
            Node node = goal.node();
            // Check if goal is allowed
            RuleApp ruleApp = goal.getRuleAppManager().peekNext();
            SourceElement activeStatement = NodeInfo.computeActiveStatement(ruleApp);
            Node SETParent = SymbolicExecutionUtil.findParentSetNode(node);
            if (activeStatement instanceof Throw throwStatement && isEnabled()) {
                for (int i = 0; i < throwStatement.getChildCount(); i++) {
                    SourceElement childElement = throwStatement.getChildAt(i);
                    if (childElement instanceof LocationVariable locVar) {
                        if (locVar.getKeYJavaType().getSort().toString().equals(exceptionName)
                                && !exceptionParentNodes.contains(SETParent)) {
                            exceptionNodes.add(node);
                            exceptionParentNodes.add(SETParent);
                        } else if (suspendOnSubclasses) {
                            JavaInfo info = goal.proof().getJavaInfo();
                            KeYJavaType kjt = locVar.getKeYJavaType();
                            ImmutableList<KeYJavaType> kjts = info.getAllSupertypes(kjt);
                            for (KeYJavaType kjtloc : kjts) {
                                if (kjtloc.getSort().toString().equals(exceptionName)
                                        && !exceptionParentNodes.contains(SETParent)) {
                                    exceptionNodes.add(node);
                                    exceptionParentNodes.add(SETParent);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if the given node is a parent of the other given node.
     *
     * @param node The {@link Node} to start search in.
     * @param parent The {@link Node} that is thought to be the parent.
     * @return true if the parent node is one of the nodes parents
     */
    public boolean isParentNode(Node node, Node parent) {
        if (node != null) {
            Node parentIter = node.parent();
            boolean result = false;
            while (parentIter != null && !result) {
                if (parentIter.equals(parent)) {
                    result = true;
                } else {
                    parentIter = parentIter.parent();
                }
            }
            return result;
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBreakpointHit(SourceElement activeStatement, RuleApp ruleApp, Node node) {
        Node parent = null;
        for (Node parents : exceptionNodes) {
            if (isParentNode(node, parents)) {
                parent = parents;
            }
        }
        if (parent != null && SymbolicExecutionUtil.isSymbolicExecutionTreeNode(node, ruleApp)
                && !exceptionParentNodes.isEmpty()) {
            if (SymbolicExecutionUtil.isTerminationNode(node, ruleApp) && uncaught) {
                if (hitcountExceeded(node)) {
                    exceptionNodes.remove(parent);
                    return true;
                }
            } else if (!SymbolicExecutionUtil.isTerminationNode(node, ruleApp) && caught) {
                if (hitcountExceeded(node)) {
                    exceptionNodes.remove(parent);
                    return true;
                }
            }
            exceptionNodes.remove(parent);
        }
        return false;
    }

    /**
     * @return the isCaught
     */
    public boolean isCaught() {
        return caught;
    }

    /**
     * @param isCaught the isCaught to set
     */
    public void setCaught(boolean isCaught) {
        this.caught = isCaught;
    }

    /**
     * @return the isUncaught
     */
    public boolean isUncaught() {
        return uncaught;
    }

    /**
     * @param isUncaught the isUncaught to set
     */
    public void setUncaught(boolean isUncaught) {
        this.uncaught = isUncaught;
    }

    /**
     * @return the suspendOnSubclasses
     */
    public boolean isSuspendOnSubclasses() {
        return suspendOnSubclasses;
    }

    /**
     * @param suspendOnSubclasses the suspendOnSubclasses to set
     */
    public void setSuspendOnSubclasses(boolean suspendOnSubclasses) {
        this.suspendOnSubclasses = suspendOnSubclasses;
    }
}
