package org.jackjs;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class JackFilter extends JackServlet implements Filter {
	
    public void destroy() {
	}

	public void doFilter(ServletRequest req, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		String path = URLDecoder.decode(request.getRequestURI().substring(request.getContextPath().length() + 1), "UTF8");
		String realPath = request.getRealPath(path);
		File targetFile;
		boolean fileExists = realPath != null && (targetFile = new File(realPath)).exists() && targetFile.isFile();
		if(fileExists){
			chain.doFilter(req, response);
		}
		else{
			service(request, (HttpServletResponse) response);
		}
	}

	public void init(final FilterConfig config) throws ServletException {
    	initialize(new Config(){
    		public String getInitParameter(String name){
    			return config.getInitParameter(name);
    		}
    		public ServletContext getServletContext(){
    			return config.getServletContext();
    		}
    	});

	}

    
}
