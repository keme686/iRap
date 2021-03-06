/**
 * 
 */
package de.unibonn.iai.eis.irap.evaluation;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.sparql.core.TriplePath;

import de.unibonn.iai.eis.irap.helper.InterestExprGraph;
import de.unibonn.iai.eis.irap.model.Changeset;
import de.unibonn.iai.eis.irap.model.EvaluationResultModel;
import de.unibonn.iai.eis.irap.model.Interest;
import de.unibonn.iai.eis.irap.model.Subscriber;
import de.unibonn.iai.eis.irap.sparql.QueryDecomposer;
import de.unibonn.iai.eis.irap.sparql.QueryPatternExtractor;
import de.unibonn.iai.eis.irap.sparql.SPARQLExecutor;

/**
 * @author Kemele M. Endris
 *
 */
public class InterestingChangesetGenerator {

	private static final Logger logger = LoggerFactory.getLogger(InterestingChangesetGenerator.class);
	private Subscriber subscriber;
	private Changeset changeset;
	public InterestingChangesetGenerator(Subscriber subscriber, Changeset changeset) {
		this.subscriber = subscriber;
		this.changeset = changeset;
	}
	/**
	 * evaluate each interests of a subscriber sequentially
	 */
	public void start(){
		List<Interest> interests = subscriber.getInterestExpressions();
		for (Interest i : interests) {
			evaluate(i);
		}
	}
	
	private void evaluate(final Interest interest) {
		evaluateRemoved(interest);
		evaluateAdded(interest);
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * 
	 * @param interest
	 */
	private EvaluationResultModel evaluateAdded(final Interest interest) {
		EvaluationResultModel evaluationResult = processAdditions(interest);
		return evaluationResult;
	}
	
	/**
	 * 
	 * @param interest
	 * @return
	 */
	private EvaluationResultModel processAdditions(final Interest interest){
		Model added = ModelFactory.createDefaultModel().add(changeset.getAddedTriples());
		EvaluationResultModel result = new EvaluationResultModel();
		
		// Interesting added triples
		Model bgpMatching = getMatching(interest.getBgp(), added);
		if(!bgpMatching.isEmpty()){
			Model matching = PIManager.getMissingFromPI(subscriber,	interest.getSourceEndpoint(), interest.getBgp(), interest.getOgp(), interest.getBgp(), bgpMatching);
			bgpMatching.add(matching);
		
			// remove matchings found from PI
			Model cleaning = matching.remove(bgpMatching);
			if (!cleaning.isEmpty())
				// TODO: do not remove cleaning from PI, just keep it as PI changeset
				PIManager.removeFromPI(cleaning, subscriber, interest);

			// result.getInterestingTriples().add(bgpMatching);

			added.remove(bgpMatching);
			result = getBGPCombinationsForAdded(interest, added);
			result.addInterestingTriples(bgpMatching);
		}	
		
		// Interesting optional removed triples
		Model ogpMatching = getMatching(interest.getOgp(), added);
		if(!ogpMatching.isEmpty()){
			Model missing = TargetManager.getMissingFromTarget(subscriber,interest.getBgp(), interest.getOgp(), interest.getOgp(),	ogpMatching);
			if (!missing.isEmpty()) {
				result.addInterestingTriples(missing);
				result.addPotentiallyInterestingTriples(ogpMatching.remove(missing));
			} else
				result.addPotentiallyInterestingTriples(ogpMatching);
		}
		return result;
	}
	/**
	 * 
	 * @param interest
	 * @param added
	 * @return
	 */
	private EvaluationResultModel getBGPCombinationsForAdded(final Interest interest, Model added){
		EvaluationResultModel result = new EvaluationResultModel();
		List<TriplePath> paths = interest.getBgp();
		
		//Combinations - BGP
		for (int i = paths.size() - 1; i > 0; i--) {
			// 2^b ask queries
			List<Query> askQueries = QueryDecomposer.composeAskQueries(paths, i);
			InterestExprGraph g = new InterestExprGraph();
			for (Query q : askQueries) {
				List<TriplePath> askPaths = QueryPatternExtractor.getBGPTriplePaths(q);
				// if q contains disjoint pattern
				if (askPaths.size() > 1 && !g.isNonDisjoint(q)) {
					logger.info("SKIPPING: Disjoint Query: \n" + q);
					continue;
				}

				if (SPARQLExecutor.executeAsk(added, q)) {
					Query cq = QueryDecomposer.toConstructQuery(askPaths);
					// extract c_i
					Model r = SPARQLExecutor.executeConstruct(added, cq);
					// Interesting added triples (combined with triples in PI)
					Model m = PIManager.getMissingFromPI(subscriber, interest.getSourceEndpoint(), paths, interest.getOgp(), askPaths, r);
					if (!m.isEmpty()) {
						result.addInterestingTriples(m);
						Model dif = ModelFactory.createDefaultModel().add(m).remove(r);
						if(!PIManager.removeFromPI(dif, subscriber, interest)){
							logger.info("Cannot remove triples that become interesting from PI");
						}
						r.remove(m);
						added.remove(m);
						if (r.isEmpty()) {
							continue;
						}
					}
					
					Model partials = assertAdded(interest, askPaths, r);
					
					if(partials.isEmpty()){
						result.addPotentiallyInterestingTriples(r);
					}
					else{
						result.addPotentiallyInterestingTriples(r.remove(partials));
						result.addInterestingTriples(partials);
					}
					added.remove(r);
				}
			}
		}
		
		return result;
	}
	/**
	 * 
	 * @param interest
	 * @param askPaths
	 * @param res
	 * @return
	 */
	private Model assertAdded(final Interest interest, List<TriplePath> askPaths, Model res){
		InterestExprGraph g = new InterestExprGraph();
		Model interestingTriples = ModelFactory.createDefaultModel();
		Model potentiallyInterestingTriples = ModelFactory.createDefaultModel();
		
		Model partial = ModelFactory.createDefaultModel().add(res);
		// find partially matching from PI and check the rest from target
		List<TriplePath> askDiff = new ArrayList<TriplePath>();
		askDiff.addAll(interest.getBgp());
		askDiff.removeAll(askPaths);
		
		for (int j = askDiff.size()-1 ; j > 0; j--) {
			List<Query> consQueries = QueryDecomposer.composeConstructQueries(askDiff, j);

			for (Query qc : consQueries) {
				List<TriplePath> diffAndAsk = new ArrayList<TriplePath>();
				diffAndAsk.addAll(askPaths);
				diffAndAsk.addAll(QueryPatternExtractor.getBGPTriplePaths(qc));
				
				if(!g.isNonDisjoint(QueryDecomposer.toAskQuery(diffAndAsk))){
					//logger.info("DISJOINT delta: \n" + QueryDecomposer.toAskQuery(diffAndAsk));					
					continue;
				}
				//candidate C_i from Delta = A and pi
				Model piR = PIManager.getMissingFromPI(subscriber, interest.getSourceEndpoint(), diffAndAsk, interest.getOgp(), askPaths, partial);
				if (piR.isEmpty()) {
					diffAndAsk = askPaths;
				}
				piR.add(partial);
				Model m = getPartialsFromTarget(interest, diffAndAsk, piR);
				if(!m.isEmpty()){
					interestingTriples.add(m);
					partial.remove(m);
				}
			}
		}
		if(interestingTriples.isEmpty()){
			potentiallyInterestingTriples.add(partial);
		}else{
			Model pire = ModelFactory.createDefaultModel().add(interestingTriples).remove(partial);
			if(!PIManager.removeFromPI(pire, subscriber, interest)){
				logger.info("Cannot remove triples from PIs that becomes interesting!");
			}
		}
		
		return interestingTriples;
	}
	/**
	 * 
	 * @param interest
	 * @param askPaths
	 * @param candidateModel
	 * @return
	 */
	private Model getPartialsFromTarget(final Interest interest, List<TriplePath> askPaths, Model candidateModel){
		
		Model result = ModelFactory.createDefaultModel();
		Model missing= TargetManager.getMissingFromTarget(subscriber, interest.getBgp(), interest.getOgp(), askPaths, candidateModel);
		if(!missing.isEmpty()){
			result.add(missing);
			Model prime = missing.remove(candidateModel);
			return result.remove(prime);
		}
		
		return result;
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	
	
	
	/**
	 * 
	 * @param interest
	 * @return
	 */
	private EvaluationResultModel evaluateRemoved(final Interest interest) {
		//make a copy of removed triples from changeset
		//Model removed = ModelFactory.createDefaultModel().add(changeset.getRemovedTriples());
		// Evaluate interest expression directly on target endpoint of a subscriber process 
		EvaluationResultModel  removeResult = processRemove(interest);
		
		return removeResult;
	}
	
	/**
	 * 
	 * @param interest
	 * @return 
	 */
	private EvaluationResultModel processRemove(final Interest interest){
		
		Model removed = ModelFactory.createDefaultModel().add(changeset.getRemovedTriples());
				
		//TODO: include filters with BGP
		
		//Interesting removed triples
		Model bgpMatching = getMatching(interest.getBgp(), removed);
		removed.remove(bgpMatching);
		
		//Interesting optional removed triples
		Model ogpMatching = getMatching(interest.getOgp(), removed);
		removed.remove(ogpMatching);
		
		//Interesting removed triples and potentially interesting triples (because of partially removed pattern)
		EvaluationResultModel result = getBGPCombinationsForRemove(interest, removed);
		
		
		//add bgp and ogp matchings to interesting removed triples
		result.addInterestingTriples(bgpMatching);
		//TODO: why is OGP matchings added to potentially interested triples
		result.addPotentiallyInterestingTriples(ogpMatching);
		return result;
	}
	
	/**
	 * 
	 * @param paths
	 * @param model
	 * @return
	 */
	private Model getMatching(List<TriplePath> paths, Model model){		
		Query query = QueryDecomposer.toConstructQuery(paths);
		return SPARQLExecutor.executeConstruct(model, query);
	}
	
	/**
	 * combination of BGP of interest expression
	 * @param interest
	 * @return
	 */
	private EvaluationResultModel getBGPCombinationsForRemove(final Interest interest, Model removed){
		EvaluationResultModel result = new EvaluationResultModel();
		List<TriplePath> paths = interest.getBgp();
		//Combinations - BGP
		for(int i = paths.size()-1; i>0; i--){
			//2^b ask queries
			List<Query> askQueries = QueryDecomposer.composeAskQueries(paths, i);
			InterestExprGraph g = new InterestExprGraph();
			for (Query q : askQueries) {	
				List<TriplePath> askPaths = QueryPatternExtractor.getBGPTriplePaths(q);
				//if q contains disjoint pattern				
				if(askPaths.size() > 1 && !g.isNonDisjoint(q)){
					logger.info("SKIPPING: Disjoint Query: \n" + q);
					continue;
				}
				if (SPARQLExecutor.executeAsk(removed, q)) {
					Query cq = QueryDecomposer.toConstructQuery(askPaths);
					// extract c_i
					Model r = SPARQLExecutor.executeConstruct(removed, cq);
					if(!r.isEmpty()){
						EvaluationResultModel partials = assertRemoved(interest, askPaths, r);
						result.addInterestingTriples(partials.getInterestingTriples());
						result.addPotentiallyInterestingTriples(partials.getPotentiallyInterestingTriples());
						removed.remove(r);
					}
					
				}
			}
		}
		return result;
	}
	/**
	 * 
	 * @param interest
	 * @param candidatePaths
	 * @param candidateModel
	 * @return
	 */
	private EvaluationResultModel assertRemoved(final Interest interest, List<TriplePath> candidatePaths, Model candidateModel){
		List<TriplePath> paths = interest.getBgp();
		Model interestingTriples = ModelFactory.createDefaultModel();
		Model potentiallyInterestingTriples = ModelFactory.createDefaultModel();
		
		//InterestExprGraph g = new InterestExprGraph();
		
		Model missing= TargetManager.getMissingFromTarget(subscriber, paths, new ArrayList<TriplePath>(), candidatePaths, candidateModel);
		if(!missing.isEmpty()){
			// TODO: check if the prime is related to candidateModel only. 			
		   // If there are other triples connected a triple in prime from target, then leave this triple (remove from missing)			
			interestingTriples.add(missing);
			Model prime = missing.remove(candidateModel);
			potentiallyInterestingTriples.add(prime);
		}
		
		EvaluationResultModel result = new EvaluationResultModel();
		result.getInterestingTriples().add(interestingTriples);
		result.getPotentiallyInterestingTriples().add(potentiallyInterestingTriples);
		return result;
	}
}
