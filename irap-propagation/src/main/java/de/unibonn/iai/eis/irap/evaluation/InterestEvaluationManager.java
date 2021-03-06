/**
 * 
 */
package de.unibonn.iai.eis.irap.evaluation;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibonn.iai.eis.irap.interest.InterestManager;
import de.unibonn.iai.eis.irap.model.Changeset;
import de.unibonn.iai.eis.irap.model.Subscriber;

/**
 * @author Kemele M. Endris
 *
 */
public class InterestEvaluationManager {
	
	private static final Logger logger = LoggerFactory.getLogger(InterestEvaluationManager.class);
	
	InterestManager interestManager;
	Changeset changeset;
	String formatedFilePath;
	 public InterestEvaluationManager(InterestManager interestManager, Changeset changeset) {
		this.interestManager = interestManager;
		this.changeset = changeset;
		this.formatedFilePath = changeset.getSequenceNum();
	}
	 
	/**
	 * when notified, it inquires subscribed interest expression for the specified changeset,
	 * then start evaluator
	 */
	public void begin(){
		logger.info("Reading interest expressions subscribed for changeset from: " + changeset.getChangesetUri());
		List<Subscriber> subscribers = interestManager.getSubscribers(changeset.getChangesetUri());
		for(Subscriber s: subscribers){
			logger.info("Stating evalaution for Subscriber: " + s.getId());
			InterestEvaluator eval = new InterestEvaluator(s, changeset, formatedFilePath);
			String folderName = s.getTargetEndpoint() + "_changesets";
			File folder = new File(folderName);
			logger.info(folderName);
			if(!folder.exists())
				folder.mkdir();
			eval.start(folderName, true);
		}
	}
}
