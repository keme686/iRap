/**
 * 
 */
package de.unibonn.iai.eis.irap;


import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibonn.iai.eis.irap.changeset.ChangesetManager;
import de.unibonn.iai.eis.irap.changeset.RemoteChangesetManager;
import de.unibonn.iai.eis.irap.interest.FileBasedInterestManager;
import de.unibonn.iai.eis.irap.interest.InterestManager;
import de.unibonn.iai.eis.irap.model.CMMethod;

/**
 * @author Kemele M. Endris
 *
 */
public class Main 
{	
	private static final Logger logger = LoggerFactory.getLogger(Main.class);
	
    public static void main( String[] args )
    {    	
    	String filename = "../restaurant-interest.ttl";
    	int mode = 0;
    	if(args == null || args.length < 2){
    		logger.info("Usage: \n$mvn exec:java -Dexec.args=\"<interest>, <run-mode>\"\n Where:\n\t <interest> is interest expression RDF file (ttl, nt. rdf) and \n\t <run-mode> is an interer value 0 - for one time and -1 for endless run mode");
    		//return;    		
    	}else{
    		filename = args[0];
    		mode = Integer.parseInt(args[1]);
    	}
    	if(!new File(filename).exists()){
    		logger.info("File doesnt exist");
    		return;
    	}
    	//String filename = args[0];
    	//int mode = Integer.parseInt(args[1]);
    	System.out.println(mode);
    	if(mode != 0 && mode != -1){    		
    		System.out.println("Invalid running mode. Valid run modes are 0 and -1");
    		return;
    	}
        logger.info("reading interest expression started");
        
        InterestManager imgr  = new FileBasedInterestManager(filename); //"interest.ttl"
        
        ChangesetManager cmgr = new RemoteChangesetManager(imgr);
        if(mode ==0){
        	cmgr.start(CMMethod.ONETIME); 
        }else if(mode == -1){
        	cmgr.start(CMMethod.ENDLESS); 
        }
    }
}

