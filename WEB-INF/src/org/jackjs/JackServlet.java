package org.jackjs;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrappedException;

@SuppressWarnings("serial")
public class JackServlet extends HttpServlet {
	Scriptable scope;
	Scriptable servletHandler;
	Function handler;
	interface Config{
		String getInitParameter(String name);
		ServletContext getServletContext();
	}
	
    public void init(final ServletConfig config) throws ServletException {
    	super.init(config);
    	initialize(new Config(){
    		public String getInitParameter(String name){
    			return config.getInitParameter(name);
    		}
    		public ServletContext getServletContext(){
    			return JackServlet.this.getServletContext();
    		}
    	});
    }
    public void initialize(Config config) throws ServletException {
    	String modulesPathDefault = System.getProperty("narwhal.modules.path");
    	if(modulesPathDefault== null){
    		modulesPathDefault = config.getServletContext().getRealPath("WEB-INF/jslib");
    	}
		final String[] modulesPath = getInitParam(config, "modulesFilePath",modulesPathDefault).split(",");
		final String configPath = config.getServletContext().getRealPath(getInitParam(config, "configPath", "WEB-INF"));
		final String moduleName = getInitParam(config, "module", "jackconfig.js");
		final boolean reload = "true".equals(getInitParam(config, "reload", "true"));
		final String appName = getInitParam(config, "app", "app");
		final String environmentName = getInitParam(config, "environment", "development");
		String narwhalHomeDefault = System.getProperty("narwhal.home");
		if(narwhalHomeDefault == null){
			narwhalHomeDefault = config.getServletContext().getRealPath("WEB-INF/narwhal");
		}
		try {
			narwhalHomeDefault = new File(narwhalHomeDefault).getCanonicalPath();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		final String narwhalHome = getInitParam(config, "narwhalFilePath", narwhalHomeDefault);
		final String narwhalFilename = "engines/rhino/bootstrap.js";
		
		Context context = Context.enter();
		try {
			context.setOptimizationLevel(5);
			scope = new ImporterTopLevel(context);
			
			ScriptableObject.putProperty(scope, "NARWHAL_HOME",  Context.javaToJS(narwhalHome, scope));
			
			// load Narwhal
			context.evaluateReader(scope, new FileReader(narwhalHome+"/"+narwhalFilename), narwhalFilename, 1, null);
			
			// load Servlet handler "process" method
			servletHandler = (Scriptable)context.evaluateString(scope, "new (require('jack/handler/servlet').Servlet)({reload:" + reload + ", app:'" + appName + "', environment:'"+ environmentName + "', args:['"+configPath.replaceAll("\\\\", "/") +"/"+moduleName+"']})", null, 1, null);
			
			handler = (Function) ScriptableObject.getProperty(servletHandler, "process");  

			for (String modulePath : modulesPath){
				context.evaluateString(scope, "require.paths.unshift('"+modulePath.replaceAll("\\\\", "/")+"');", null, 1, null);
			}

			
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			Context.exit();
		}
    }

	public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
		Context context = Context.enter();
		try	{
			Object args[] = {request, response};
			handler.call(context, scope, servletHandler, args);
		} 
		catch(WrappedException e){
			// this allows Jetty's RetryRequest exceptions to escape properly, and just 
			// improves visibility of exceptions
			if(e.getWrappedException() instanceof RuntimeException)
				throw (RuntimeException) e.getWrappedException();
			throw e;
		}
		finally {
			Context.exit();
		}
	}
	
	private String getInitParam(Config config, String name, String defaultValue) {
        String value = config.getInitParameter(name);
        return value == null ? defaultValue : value;
    }
}
