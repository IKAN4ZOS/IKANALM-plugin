/**
 * 
 */
package com.ikanalm.plugins.base.steps;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import com.ikanalm.plugins.base.antbuilder.AntBuilder;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Abstract base class that can be extended to create pipeline Steps that are to be run with Ant.
 * Overriding classes must override the methods getFQStepName(), getMainScript() and getAntInstallation().
 * A default implementation of runExecution() is provided.
 * Overriding classes must create an inner class that extends BaseDescriptorImpl
 * 
 * @author frs
 *
 */
public abstract class BaseAntStep extends BaseStep {

	/**
	 * implementing classes must override this and return the Fully Qualified name of the Step
	 * 
	 * @return Fully Qualified Step name
	 */
	public abstract String getFQStepName();
	
	/**
	 * implementing classes must override this and return the main Ant script
	 * 
	 * @return main Ant script
	 */
	public abstract String getMainScript();
	
	/**
	 * implementing classes must override this and return the name of the Ant installation as it is defined in Jenkins
	 * 
	 * @return Ant installation name
	 */
	public abstract String getAntInstallation();
	
	
	/* (non-Javadoc)
	 * @see com.ikanalm.plugins.jenkins.base.BaseStep#runExection(org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution)
	 */
	@Override
	public void runExecution(SynchronousNonBlockingStepExecution<Void> execution) throws Exception {
		StepContext context = execution.getContext();
		TaskListener taskListener = context.get(TaskListener.class);
		
		Run run = context.get(Run.class);
		FilePath workspace = context.get(FilePath.class);
		EnvVars envVars = context.get(EnvVars.class);
		Launcher launcher = context.get(Launcher.class);
		
//		taskListener.getLogger().println("env vars :");
//        for (String key : envVars.keySet()) {
//        	taskListener.getLogger().println(key + "=" + envVars.get(key));
//		}
        
        // unzip the phase resources to the workspace
        extractResources(workspace, getFQStepName());

        // generate the Ant properties
    	String propertiesAsString = generateAntProperties(workspace, getFQStepName());
    	
//    	taskListener.getLogger().println("generated Ant properties :");
//    	taskListener.getLogger().println(propertiesAsString);
    	
    	// TODO : support specifying Ant targets
    	// TODO : support specifying Ant options
    	AntBuilder antBuilder = new AntBuilder("", getAntInstallation(), "", getFQStepName() + "/" + getMainScript(), propertiesAsString);
    	// launch Ant. The working dir is the dir of the main script
//    	taskListener.getLogger().println("start AntBuilder.perform()");
    	antBuilder.perform(envVars, run, workspace, launcher, taskListener);
//    	taskListener.getLogger().println("end AntBuilder.perform()");
	}

}
