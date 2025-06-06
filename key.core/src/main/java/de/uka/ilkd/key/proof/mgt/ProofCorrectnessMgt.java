/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.proof.mgt;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import de.uka.ilkd.key.java.abstraction.KeYJavaType;
import de.uka.ilkd.key.logic.op.IObserverFunction;
import de.uka.ilkd.key.proof.Proof;
import de.uka.ilkd.key.proof.ProofEvent;
import de.uka.ilkd.key.proof.ProofTreeAdapter;
import de.uka.ilkd.key.proof.ProofTreeEvent;
import de.uka.ilkd.key.proof.RuleAppListener;
import de.uka.ilkd.key.proof.init.ContractPO;
import de.uka.ilkd.key.proof.reference.ClosedBy;
import de.uka.ilkd.key.speclang.Contract;

import org.key_project.prover.rules.RuleApp;
import org.key_project.util.collection.DefaultImmutableSet;
import org.key_project.util.collection.ImmutableList;
import org.key_project.util.collection.ImmutableSLList;
import org.key_project.util.collection.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class ProofCorrectnessMgt {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProofCorrectnessMgt.class);

    private final Proof proof;
    private final SpecificationRepository specRepos;
    private final DefaultMgtProofListener proofListener = new DefaultMgtProofListener();
    private final DefaultMgtProofTreeListener proofTreeListener = new DefaultMgtProofTreeListener();

    private final Set<RuleApp> cachedRuleApps = new LinkedHashSet<>();
    private ProofStatus proofStatus = ProofStatus.OPEN;


    // -------------------------------------------------------------------------
    // constructors
    // -------------------------------------------------------------------------

    public ProofCorrectnessMgt(Proof p) {
        this.proof = p;
        this.specRepos = p.getServices().getSpecificationRepository();
        proof.addProofTreeListener(proofTreeListener);
        proof.addRuleAppListener(proofListener);
    }



    // -------------------------------------------------------------------------
    // internal methods
    // -------------------------------------------------------------------------


    private boolean allHaveMeasuredBy(ImmutableList<Contract> contracts) {
        for (Contract contract : contracts) {
            if (!contract.hasMby()) {
                return false;
            }
        }
        return true;
    }



    // -------------------------------------------------------------------------
    // public interface
    // -------------------------------------------------------------------------

    public RuleJustification getJustification(RuleApp r) {
        return proof.getInitConfig().getJustifInfo().getJustification(r, proof.getServices());
    }


    /**
     * Tells whether a contract for the passed target may be applied in the passed goal without
     * creating circular dependencies.
     */
    public boolean isContractApplicable(Contract contract) {
        // get the contract which is being verified in our current proof
        final ContractPO po = specRepos.getContractPOForProof(proof);
        if (po == null) {
            return true;
        }
        final Contract originalContract = po.getContract();

        // get initial contracts
        ImmutableSet<Contract> contractsToBeApplied = specRepos.splitContract(contract);
        assert contractsToBeApplied != null;
        contractsToBeApplied = specRepos.getInheritedContracts(contractsToBeApplied);

        // initial paths
        ImmutableSet<ImmutableList<Contract>> newPaths = DefaultImmutableSet.nil();
        for (Contract c : contractsToBeApplied) {
            newPaths = newPaths.add(ImmutableSLList.<Contract>nil().prepend(c));
        }

        // look for cycles
        while (!newPaths.isEmpty()) {
            final Iterator<ImmutableList<Contract>> it = newPaths.iterator();
            newPaths = DefaultImmutableSet.nil();

            while (it.hasNext()) {
                final ImmutableList<Contract> path = it.next();
                final Contract end = path.head();
                if (end.equals(originalContract)) {
                    if (!allHaveMeasuredBy(path.prepend(originalContract))) {
                        return false;
                    }
                } else {
                    final ImmutableSet<Proof> proofsForEnd = specRepos.getProofs(end);
                    for (Proof proofForEnd : proofsForEnd) {
                        final ImmutableSet<Contract> contractsUsedForEnd =
                            proofForEnd.mgt().getUsedContracts();
                        for (Contract contractUsedForEnd : contractsUsedForEnd) {
                            if (!path.contains(contractUsedForEnd)) {
                                final ImmutableList<Contract> extendedPath =
                                    path.prepend(contractUsedForEnd);
                                newPaths = newPaths.add(extendedPath);
                            }
                        }
                    }
                }
            }
        }

        return true;
    }


    @Deprecated
    public boolean contractApplicableFor(KeYJavaType kjt, IObserverFunction target) {
        target = specRepos.unlimitObs(target);

        // get the target which is being verified in the passed goal
        final IObserverFunction targetUnderVerification = specRepos.getTargetOfProof(proof);
        if (targetUnderVerification == null) {
            return true;
        }

        // collect all proofs on which the specification of the
        // passed target may depend
        ImmutableSet<Proof> proofs = specRepos.getProofs(kjt, target);
        ImmutableSet<Proof> newProofs = proofs;
        while (newProofs.size() > 0) {
            final Iterator<Proof> it = newProofs.iterator();
            newProofs = DefaultImmutableSet.nil();

            while (it.hasNext()) {
                final Proof p = it.next();
                for (Contract contract : p.mgt().getUsedContracts()) {
                    ImmutableSet<Proof> proofsForContractTarget =
                        specRepos.getProofs(contract.getKJT(), contract.getTarget());
                    newProofs = newProofs.union(proofsForContractTarget);
                    proofs = proofs.union(proofsForContractTarget);
                }
            }
        }

        // is one of those proofs about the target under verification?
        for (Proof p : proofs) {
            final IObserverFunction target2 = specRepos.getTargetOfProof(p);
            if (target2.equals(targetUnderVerification)) {
                return false;
            }
        }

        return true;
    }


    public void updateProofStatus() {
        final ImmutableSet<Proof> all = specRepos.getAllProofs();

        // mark open proofs as open, all others as presumably closed
        ImmutableSet<Proof> presumablyClosed = DefaultImmutableSet.nil();
        for (Proof p : all) {
            if (!p.isDisposed()) {
                // some branch is closed via cache:
                if (p.openGoals().size() == 0 && p.closedGoals().stream()
                        .anyMatch(goal -> goal.node().lookup(ClosedBy.class) != null)) {
                    p.mgt().proofStatus = ProofStatus.CLOSED_BY_CACHE;
                } else if (p.openGoals().size() > 0) {
                    // some branch is open
                    p.mgt().proofStatus = ProofStatus.OPEN;
                } else {
                    // all branches are properly closed
                    p.mgt().proofStatus = ProofStatus.CLOSED;
                    presumablyClosed = presumablyClosed.add(p);
                }
            }
        }

        // revert status of all "presumably closed" proofs for which at least one
        // used contract is definitely not proven to "lemmas left"
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Proof p : presumablyClosed) {
                for (Contract usedContract : p.mgt().getUsedContracts()) {
                    final ImmutableSet<Proof> usedProofs = specRepos.getProofs(usedContract);
                    if (usedProofs.isEmpty()) {
                        p.mgt().proofStatus = ProofStatus.CLOSED_BUT_LEMMAS_LEFT;
                        presumablyClosed = presumablyClosed.remove(p);
                        changed = true;
                    } else {
                        for (Proof usedProof : usedProofs) {
                            if (usedProof.mgt().proofStatus != ProofStatus.CLOSED) {
                                p.mgt().proofStatus = ProofStatus.CLOSED_BUT_LEMMAS_LEFT;
                                presumablyClosed = presumablyClosed.remove(p);
                                changed = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }


    public void ruleApplied(RuleApp r) {
        RuleJustification rj = getJustification(r);
        if (rj == null) {
            LOGGER.debug("No justification found for rule " + r.rule().name());
            return;
        }
        if (!rj.isAxiomJustification()) {
            cachedRuleApps.add(r);
        }
    }


    public void ruleUnApplied(RuleApp r) {
        cachedRuleApps.remove(r);
    }


    public ImmutableSet<Contract> getUsedContracts() {
        ImmutableSet<Contract> result = DefaultImmutableSet.nil();
        for (RuleApp ruleApp : cachedRuleApps) {
            RuleJustification ruleJusti = getJustification(ruleApp);
            if (ruleJusti instanceof RuleJustificationBySpec) {
                Contract contract = ((RuleJustificationBySpec) ruleJusti).spec();
                ImmutableSet<Contract> atomicContracts = specRepos.splitContract(contract);
                assert atomicContracts != null;
                atomicContracts = specRepos.getInheritedContracts(atomicContracts);
                result = result.union(atomicContracts);
            }
        }
        return result;
    }

    public void removeProofListener() {
        proof.removeRuleAppListener(proofListener);
    }


    public ProofStatus getStatus() {
        return proofStatus;
    }

    // -------------------------------------------------------------------------
    // inner classes
    // -------------------------------------------------------------------------

    private class DefaultMgtProofListener implements RuleAppListener {
        @Override
        public void ruleApplied(ProofEvent e) {
            ProofCorrectnessMgt.this.ruleApplied(e.getRuleAppInfo().getRuleApp());
        }
    }


    private class DefaultMgtProofTreeListener extends ProofTreeAdapter {
        @Override
        public void proofClosed(ProofTreeEvent e) {
            updateProofStatus();
        }

        @Override
        public void proofStructureChanged(ProofTreeEvent e) {
            updateProofStatus();
        }
    }
}
