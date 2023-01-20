/**
 * 
 */
package com.ikanalm.plugins.base;

import java.io.File;
import java.io.StringWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import com.google.common.collect.ImmutableSet;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Abstract base class that can be extended to create pipeline Steps.
 * Overriding classes must override the runExection() method and create
 * an inner class that extends BaseDescriptorImpl
 * 
 * @author frs
 *
 */
public abstract class BaseStep extends Step {

	/**
	 * Implementing Step classes must override this method and perform here whatever task 
	 * needs to be done by the pipeline Step. Gets called from BaseExecution.run()
	 * 
	 * @param execution the Execution to execute
	 * @throws Exception
	 */
	abstract public void runExecution(SynchronousNonBlockingStepExecution<Void> execution) throws Exception;
	
	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.workflow.steps.Step#start(org.jenkinsci.plugins.workflow.steps.StepContext)
	 */
	@Override
	public StepExecution start(StepContext context) throws Exception {
		// launch BaseExecution
        return new BaseExecution(context, this);
	}

	/**
	 * Nested Execution class that calls the overriding Step's runExecution() method
	 * Sets up a StepContext, from which access to Run, FilePath, EnvVars, Launcher and TaskListener objects is provided
	 * 
	 * @author frs
	 *
	 */
	@SuppressWarnings("serial")
	public static class BaseExecution extends SynchronousNonBlockingStepExecution<Void> {
		
		// link to the surrounding Step
		protected transient final BaseStep step;

		protected BaseExecution(StepContext context, BaseStep step) {
			super(context);
			this.step = step;
		}

		/* (non-Javadoc)
		 * @see org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution#run()
		 */
		@Override
		protected Void run() throws Exception {
			step.runExecution(this);
			
			return null;
		}
		
	}

	/**
	 * Implementing Step classes must extend this class to make the Step usable in a Pipeline.
	 * The overriding class must be annotated with hudson.Extension.
	 * The overriding class must at least implement getDisplayName() and getFunctionName()
	 * 
	 * @author frs
	 *
	 */
	public abstract static class BaseDescriptorImpl extends StepDescriptor {

        /**
         * provide Run, FilePath, EnvVars, Launcher, and TaskListener objects in the StepContext
         */
		@Override
		public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, EnvVars.class, Launcher.class, TaskListener.class);
		}
		
	}
	
	/**
	 * extract the zipped phase resources into a subdir of the workspace
	 * the name of the resource file is "resources-<FQ_PHASE_NAME>.zip"
	 * the contents will be unzipped into the folder "<WORKSPACE>/<FQ_PHASE_NAME>"
	 *
	 * @param workspace
	 * @param fqPhaseName
	 * @throws Exception
	 */
	protected void extractResources(FilePath workspace, String fqPhaseName) throws Exception {
        // copy the phase resources zip to the workspace
    	FilePath resourcesZipFile = new FilePath(workspace, fqPhaseName + "/resources.zip");
    	resourcesZipFile.delete();
    	Class theClass = getClass();
    	URL resourcesZipFileUrl = theClass.getResource("/resources-" + fqPhaseName + ".zip");
    	resourcesZipFile.copyFrom(resourcesZipFileUrl);
        
        // unzip the phase resources zip 
    	FilePath resourcesDir = new FilePath(workspace, fqPhaseName);
        resourcesZipFile.unzip(resourcesDir);
	}
	
    /**
     * Generate class fields as Ant properties, in a String format, to pass to Ant.perform()
     * Uses a FreeMarker template named "ant_properties.ftlh" that should be present in the folder
     * <WORKSPACE>/<FQ_PHASENAME>
     * 
     * @return
     */
    protected String generateAntProperties(FilePath workspace, String fqPhaseName) throws Exception {
    	FilePath resourcesDir = new FilePath(workspace, fqPhaseName);
    	
    	// Setup a FreeMarker Configuration
    	Configuration cfg = new Configuration(Configuration.VERSION_2_3_27);
        cfg.setDirectoryForTemplateLoading(new File(resourcesDir.getRemote()));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);

        // put the Step (and all its fields) in the model
        Map<String, Object> model = new HashMap<>();
        model.put("root", this);
        
        // Get the FreeMarker template
        Template template = cfg.getTemplate("ant_properties.ftlh");
    	
        // Merge data-model with template
        StringWriter sw = new StringWriter();
        template.process(model, sw);
        
        return sw.toString();
    }
	
}
