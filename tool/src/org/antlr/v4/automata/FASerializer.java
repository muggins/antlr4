package org.antlr.v4.automata;

import org.antlr.v4.tool.Grammar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A FA (finite automata) walker that knows how to dump them to serialized
 *  strings.
 */
public class FASerializer {
	List<State> work;
	Set<State> marked;
	Grammar g;
	State start;

	public FASerializer(Grammar g, State start) {
		this.g = g;
		this.start = start;
	}

	public String toString() {
		if ( start==null ) return null;
		work = new ArrayList<State>();
		marked = new HashSet<State>();
		work.add(start);

		StringBuilder buf = new StringBuilder();
		State s = null;
		while ( work.size()>0 ) {
			s = work.remove(0);
			if ( marked.contains(s) ) continue; 
			int n = s.getNumberOfTransitions();
			//System.out.println("visit "+getStateString(s)+"; edges="+n);
			marked.add(s);
			for (int i=0; i<n; i++) {
				Transition t = s.transition(i);
				if ( !(s instanceof RuleStopState) ) { // don't add follow states to work
					if ( t instanceof RuleTransition ) work.add(((RuleTransition)t).followState);
					else work.add( t.target );
				}
				buf.append(getStateString(s));
				if ( t instanceof EpsilonTransition ) {
					buf.append("->"+getStateString(t.target)+'\n');
				}
				else if ( t instanceof RuleTransition ) {
					buf.append("->"+getStateString(t.target)+'\n');
				}
				else if ( t instanceof ActionTransition ) {
					ActionTransition a = (ActionTransition)t;
					buf.append("-"+a.actionAST.getText()+"->"+getStateString(t.target)+'\n');
				}
				else if ( t instanceof AtomTransition ) {
					AtomTransition a = (AtomTransition)t;
					buf.append("-"+a.toString(g)+"->"+getStateString(t.target)+'\n');
				}
				else {
					buf.append("-"+t.toString()+"->"+getStateString(t.target)+'\n');					
				}
			}
		}
		return buf.toString();
	}

	String getStateString(State s) {
		int n = s.stateNumber;
		String stateStr = "s"+n;
//		if ( s instanceof DFAState ) {
//			stateStr = ":s"+n+"=>"+((DFAState)s).getUniquelyPredictedAlt();
//		}
//		else
		if ( s instanceof StarBlockStartState ) stateStr = "StarBlockStart_"+n;
		else if ( s instanceof PlusBlockStartState ) stateStr = "PlusBlockStart_"+n;
		else if ( s instanceof StarBlockStartState ) stateStr = "StarBlockStart_"+n;
		else if ( s instanceof BlockStartState ) stateStr = "BlockStart_"+n;
		else if ( s instanceof BlockEndState ) stateStr = "BlockEnd_"+n;
		else if ( s instanceof RuleStartState ) stateStr = "RuleStart_"+((RuleStartState)s).rule.name+"_"+n;
		else if ( s instanceof RuleStopState ) stateStr = "RuleStop_"+((RuleStopState)s).rule.name+"_"+n;
		else if ( s instanceof LoopbackState ) stateStr = "LoopBack_"+n;
		return stateStr;
	}
}