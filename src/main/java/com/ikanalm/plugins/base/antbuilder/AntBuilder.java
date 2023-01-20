/**
 * 
 */
package com.ikanalm.plugins.antbuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Ant;
import hudson.tasks.Ant.AntInstallation;
import hudson.tasks._maven.MavenConsoleAnnotator;
/* import hudson.tasks.Messages;   Not permitted with new Jenkins versions */
import hudson.util.ArgumentListBuilder;
import hudson.util.VariableResolver;
import jenkins.model.Jenkins;

/**
 * Copy of hudson.tasks.Ant, adapted to be able to be called from a Pipeline Step
 * 
 * @author frs
 *
 */
public class AntBuilder {

    /**
     * The targets, properties, and other Ant options.
     * Either separated by whitespace or newline.
     */
    private final String targets;

    /**
     * Identifies {@link AntInstallation} to be used.
     */
    private final String antName;

    /**
     * ANT_OPTS if not null.
     */
    private final String antOpts;

    /**
     * Optional build script path relative to the workspace.
     * Used for the Ant '-f' option.
     */
    private final String buildFile;

    /**
     * Optional properties to be passed to Ant. Follows {@link Properties} syntax.
     */
    private final String properties;

	/**
	 * @param targets
	 * @param antName
	 * @param antOpts
	 * @param buildFile
	 * @param properties
	 */
	public AntBuilder(String targets, String antName, String antOpts, String buildFile, String properties) {
		this.targets = targets;
		this.antName = antName;
		this.antOpts = antOpts;
		this.buildFile = buildFile;
		this.properties = properties;
	}

	public void perform(EnvVars env, Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {
        ArgumentListBuilder args = new ArgumentListBuilder();

        if (env == null) {
        	env = run.getEnvironment(listener);
        }

        // add build variables to the Environment if run is an AbstractBuild (for example, when used in a Freestyle project)
        if (run instanceof AbstractBuild<?, ?>) {
        	AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
            // Allow empty build parameters to be used in property replacements.
            // The env.override/overrideAll methods remove the propery if it's an empty string.
            for (Map.Entry<String, String> e : build.getBuildVariables().entrySet()) {
                if (e.getValue() != null && e.getValue().length() == 0) {
                    env.put(e.getKey(), e.getValue());
                } else {
                    env.override(e.getKey(), e.getValue());
                }
            }
        }

        AntInstallation ai = findAntInstallation();
        
        if(ai==null) {
            args.add(launcher.isUnix() ? "ant" : "ant.bat");
        } else {
            Node node = null;
            if (workspace.toComputer() != null)
               node = workspace.toComputer().getNode();
/* No message found
            if (node == null) {
                throw new AbortException(Messages.Ant_NodeOffline());
            }
*/
            if (node == null) {
                throw new AbortException("Node is Offline");
            }
            ai = ai.forNode(node, listener);
            ai = ai.forEnvironment(env);
            String exe = ai.getExecutable(launcher);
/*
            if (exe==null) {
                throw new AbortException(Messages.Ant_ExecutableNotFound(ai.getName()));
            }
*/
            if (exe==null) {
                throw new AbortException("Cannot find executable from the chosen Ant installation " + ai.getName());
            }
            args.add(exe);
        }

        VariableResolver<String> vr = new VariableResolver.ByMap<String>(env);
        String buildFile = env.expand(this.buildFile);
        String targets = env.expand(this.targets);

        // first check if this appears to be a valid relative path from workspace root
        if (workspace == null) {
        	throw new AbortException("Workspace is not available. Agent may be disconnected.");
        }
        FilePath buildFilePath = buildFilePath(workspace, buildFile, targets);
        if(!buildFilePath.exists()) {
            // build file doesn't exist
            throw new AbortException("Unable to find build script at "+ buildFilePath);
        }
        
        // disabled this piece of code. Probably we don't need to support Module roots (root dir of a checked out module)
//        FilePath buildFilePath = buildFilePath(run.getModuleRoot(), buildFile, targets);
//
//        if(!buildFilePath.exists()) {
//            // because of the poor choice of getModuleRoot() with CVS/Subversion, people often get confused
//            // with where the build file path is relative to. Now it's too late to change this behavior
//            // due to compatibility issue, but at least we can make this less painful by looking for errors
//            // and diagnosing it nicely. See HUDSON-1782
//
//            // first check if this appears to be a valid relative path from workspace root
//            FilePath workspaceFilePath = workspace;
//            if (workspaceFilePath != null) {
//                FilePath buildFilePath2 = buildFilePath(workspaceFilePath, buildFile, targets);
//                if(buildFilePath2.exists()) {
//                    // This must be what the user meant. Let it continue.
//                    buildFilePath = buildFilePath2;
//                } else {
//                    // neither file exists. So this now really does look like an error.
//                    throw new AbortException("Unable to find build script at "+ buildFilePath);
//                }
//            } else {
//                throw new AbortException("Workspace is not available. Agent may be disconnected.");
//            }
//        }

        if(buildFile!=null) {
            args.add("-file", buildFilePath.getName());
        }
        
        // TODO : remove verbose flag or make it configurable
        // args.add("-v");

        // mask sensitive build variables if AbstractBuild
        if (run instanceof AbstractBuild<?, ?>) {
        	AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
            Set<String> sensitiveVars = build.getSensitiveBuildVariables();

            args.addKeyValuePairs("-D",build.getBuildVariables(),sensitiveVars);
            args.addKeyValuePairsFromPropertyString("-D",properties,vr,sensitiveVars);
        } else {
            args.addKeyValuePairsFromPropertyString("-D",properties,vr);
        }

        args.addTokenized(targets.replaceAll("[\t\r\n]+"," "));

        if(ai!=null)
            ai.buildEnvVars(env);
        if(antOpts!=null)
            env.put("ANT_OPTS",env.expand(antOpts));

        if(!launcher.isUnix()) {
            args = toWindowsCommand(args.toWindowsCommand());
        }

        long startTime = System.currentTimeMillis();
        try {
            MavenConsoleAnnotator aca = new MavenConsoleAnnotator(listener.getLogger(),run.getCharset());
            int r;
            try {
                r = launcher.launch().cmds(args).envs(env).stdout(aca).pwd(buildFilePath.getParent()).join();
                if (r > 0) throw new IOException();
            } finally {
                aca.forceEol();
            }
//            return r==0;
        } catch (IOException e) {
            Util.displayIOException(e,listener);

/*            String errorMessage = Messages.Ant_ExecFailed(); */
            String errorMessage = "Command execution failed.";
            if(ai==null && (System.currentTimeMillis()-startTime)<1000) {
                if(Jenkins.getInstance().getDescriptorByType(Ant.DescriptorImpl.class).getInstallations() == null)
                    // looks like the user didn't configure any Ant installation
                    errorMessage += " Maybe you need to configure where your Ant installations are?"; /* Messages.Ant_GlobalConfigNeeded(); */
                else
                    // There are Ant installations configured but the project didn't pick it
                    errorMessage += " Maybe you need to configure the job to choose one of your Ant installations?"; /* Messages.Ant_ProjectConfigNeeded(); */
            }
            throw new AbortException(errorMessage);
        }
    }
	
	/**
	 * find AntInstallation that matches this.antName
	 * 
	 * @return matching AntInstallation, or null if not found
	 */
	private AntInstallation findAntInstallation() {
    	AntInstallation[] antInstallations = Jenkins.getInstance().getDescriptorByType(Ant.DescriptorImpl.class).getInstallations();
		// get AntInstallation that matches this.antName
        for( AntInstallation i : antInstallations ) {
            if(antName != null && antName.equals(i.getName())) {
                return i;
            }
        }
    	
		return null;
	}
	
	/**
	 * construct FilePath of buildFile. Also searches for the buildfile in the arguments list.
	 * 
	 * @param base
	 * @param buildFile
	 * @param targets
	 * @return FilePath of the buildfile, or "build.xml" if nothing found
	 */
    private static FilePath buildFilePath(FilePath base, String buildFile, String targets) {
        if(buildFile!=null)     return base.child(buildFile);
        // some users specify the -f option in the targets field, so take that into account as well.
        // see 
        String[] tokens = Util.tokenize(targets);
        for (int i = 0; i<tokens.length-1; i++) {
            String a = tokens[i];
            if(a.equals("-f") || a.equals("-file") || a.equals("-buildfile"))
                return base.child(tokens[i+1]);
        }
        return base.child("build.xml");
    }
	
    /**
     * Backward compatibility by checking the number of parameters
     *
     */
    protected static ArgumentListBuilder toWindowsCommand(ArgumentListBuilder args) {
        List<String> arguments = args.toList();

        if (arguments.size() > 3) { // "cmd.exe", "/C", "ant.bat", ...
            // branch for core equals or greater than 1.654
            boolean[] masks = args.toMaskArray();
            // don't know why are missing single quotes.

            args = new ArgumentListBuilder();
            args.add(arguments.get(0), arguments.get(1)); // "cmd.exe", "/C", ...

            int size = arguments.size();
            for (int i = 2; i < size; i++) {
                String arg = arguments.get(i).replaceAll("^(-D[^\" ]+)=$", "$0\"\"");

                if (masks[i]) {
                    args.addMasked(arg);
                } else {
                    args.add(arg);
                }
            }
        } else {
            // branch for core under 1.653 (backward compatibility)
            // For some reason, ant on windows rejects empty parameters but unix does not.
            // Add quotes for any empty parameter values:
            List<String> newArgs = new ArrayList<String>(args.toList());
            newArgs.set(newArgs.size() - 1, newArgs.get(newArgs.size() - 1).replaceAll(
                    "(?<= )(-D[^\" ]+)= ", "$1=\"\" "));
            args = new ArgumentListBuilder(newArgs.toArray(new String[newArgs.size()]));
        }

        return args;
    }
    
	/**
	 * @return the targets
	 */
	public String getTargets() {
		return targets;
	}

	/**
	 * @return the antName
	 */
	public String getAntName() {
		return antName;
	}

	/**
	 * @return the antOpts
	 */
	public String getAntOpts() {
		return antOpts;
	}

	/**
	 * @return the buildFile
	 */
	public String getBuildFile() {
		return buildFile;
	}

	/**
	 * @return the properties
	 */
	public String getProperties() {
		return properties;
	}
	
	
}
