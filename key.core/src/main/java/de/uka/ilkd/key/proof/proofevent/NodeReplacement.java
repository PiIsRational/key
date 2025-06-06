/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.proof.proofevent;


import java.util.Iterator;

import de.uka.ilkd.key.proof.Node;

import org.key_project.logic.PosInTerm;
import org.key_project.prover.sequent.FormulaChangeInfo;
import org.key_project.prover.sequent.PosInOccurrence;
import org.key_project.prover.sequent.Semisequent;
import org.key_project.prover.sequent.Sequent;
import org.key_project.prover.sequent.SequentChangeInfo;
import org.key_project.prover.sequent.SequentFormula;
import org.key_project.util.collection.ImmutableList;
import org.key_project.util.collection.ImmutableSLList;


/**
 * Information about a node replacing its parent after a rule application, currently giving
 * information about added and removed formulas
 */
public class NodeReplacement {

    final Node node;
    final Node parent;
    ImmutableList<SequentChangeInfo> rawChanges;
    ImmutableList<NodeChange> changes = null;

    /**
     * @param p_node the node this object reports about
     * @param p_parent the parent node
     * @param p_changes the complete list of changes made to the original node, with the most recent
     *        change being the first element of the list
     */
    public NodeReplacement(Node p_node, Node p_parent,
            ImmutableList<SequentChangeInfo> p_changes) {
        node = p_node;
        parent = p_parent;
        rawChanges = p_changes;
    }

    private void addNodeChanges() {
        if (!rawChanges.isEmpty()) {
            SequentChangeInfo sci =
                rawChanges.head();
            rawChanges = rawChanges.tail();

            addNodeChanges();


            addNodeChange(sci);
        }
    }

    private void addNodeChange(
            SequentChangeInfo p_sci) {
        Iterator<SequentFormula> it;
        Iterator<FormulaChangeInfo> it2;

        // ---
        it = p_sci.removedFormulas(true).iterator();
        while (it.hasNext()) {
            addRemovedChange(it.next(), true);
        }

        it = p_sci.removedFormulas(false).iterator();
        while (it.hasNext()) {
            addRemovedChange(it.next(), false);
        }

        // Information about modified formulas is currently not used
        it2 = p_sci.modifiedFormulas(true).iterator();
        while (it2.hasNext()) {
            addRemovedChange(it2.next().positionOfModification().sequentFormula(),
                true);
        }

        // Information about modified formulas is currently not used
        it2 = p_sci.modifiedFormulas(false).iterator();
        while (it2.hasNext()) {
            addRemovedChange(it2.next().positionOfModification().sequentFormula(),
                false);
        }

        it = p_sci.addedFormulas(true).iterator();
        while (it.hasNext()) {
            addAddedChange(it.next(), true);
        }

        it = p_sci.addedFormulas(false).iterator();
        while (it.hasNext()) {
            addAddedChange(it.next(), false);
        }

        // Information about modified formulas is currently not used
        it2 = p_sci.modifiedFormulas(true).iterator();
        while (it2.hasNext()) {
            addAddedChange(it2.next().newFormula(), true);
        }

        // Information about modified formulas is currently not used
        it2 = p_sci.modifiedFormulas(false).iterator();
        while (it2.hasNext()) {
            addAddedChange(it2.next().newFormula(), false);
        }

        // Information about formulas that have not been added as equal or more general
        // formulas are already on the sequent
        it = p_sci.rejectedFormulas(true).iterator();
        while (it.hasNext()) {
            addAddedRedundantChange(it.next(), true);
        }


        it = p_sci.rejectedFormulas(false).iterator();
        while (it.hasNext()) {
            addAddedRedundantChange(it.next(), false);
        }


    }

    private void addAddedChange(SequentFormula p_cf,
            boolean p_inAntec) {
        Sequent oldS = parent.sequent();
        Semisequent oldSS = (p_inAntec ? oldS.antecedent() : oldS.succedent());
        Sequent newS = node.sequent();
        Semisequent newSS = (p_inAntec ? newS.antecedent() : newS.succedent());

        removeNodeChanges(p_cf, p_inAntec);

        if (!oldSS.contains(p_cf) && newSS.contains(p_cf)) {
            PosInOccurrence pio =
                new PosInOccurrence(p_cf, PosInTerm.getTopLevel(), p_inAntec);
            addNodeChange(new NodeChangeAddFormula(pio));
        }
    }


    /**
     * TODO comment
     *
     * @param p_cf
     * @param p_inAntec
     */
    private void addAddedRedundantChange(SequentFormula p_cf,
            boolean p_inAntec) {

        final PosInOccurrence pio =
            new PosInOccurrence(p_cf, PosInTerm.getTopLevel(), p_inAntec);
        addNodeChange(new NodeRedundantAddChange(pio));

    }



    private void addRemovedChange(SequentFormula p_cf,
            boolean p_inAntec) {
        Sequent oldS = parent.sequent();
        Semisequent oldSS = (p_inAntec ? oldS.antecedent() : oldS.succedent());

        removeNodeChanges(p_cf, p_inAntec);

        if (oldSS.contains(p_cf)) {
            PosInOccurrence pio =
                new PosInOccurrence(p_cf, PosInTerm.getTopLevel(), p_inAntec);
            addNodeChange(new NodeChangeRemoveFormula(pio));
        }
    }

    private void addNodeChange(NodeChange p_nc) {
        changes = changes.prepend(p_nc);
    }

    private void removeNodeChanges(SequentFormula p_cf, boolean p_inAntec) {
        Iterator<NodeChange> it = changes.iterator();
        changes = ImmutableSLList.nil();
        NodeChange oldNC;
        PosInOccurrence oldPio;

        while (it.hasNext()) {
            oldNC = it.next();

            if (oldNC instanceof NodeChangeARFormula) {
                oldPio = oldNC.getPos();
                if (oldPio.isInAntec() == p_inAntec && oldPio.sequentFormula().equals(p_cf)) {
                    continue;
                }
            }

            addNodeChange(oldNC);
        }
    }

    public Node getNode() {
        return node;
    }

    /**
     * @return Modifications that have been made to node
     */
    public Iterator<NodeChange> getNodeChanges() {
        if (changes == null) {
            changes = ImmutableSLList.nil();
            addNodeChanges();
        }
        return changes.iterator();
    }

    public String toString() {
        getNodeChanges();
        return "Changes: " + changes;
    }
}
