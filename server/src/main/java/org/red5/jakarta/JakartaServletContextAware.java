package org.red5.jakarta;

import org.springframework.beans.factory.Aware;
import org.springframework.web.context.ServletConfigAware;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.ServletContext;

/**
 * Interface to be implemented by any object that wishes to be notified of the
 * {@link ServletContext} (typically determined by the {@link WebApplicationContext})
 * that it runs in.
 * 
 * The intention of this interface is to allow older Spring applications to be used with newer Tomcat versions.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Paul Gregoire
 * @since 02.08.2024
 * @see ServletConfigAware
 * @see ServletContextAware
 */
public interface JakartaServletContextAware extends Aware {

	/**
	 * Set the {@link ServletContext} that this object runs in.
	 * <p>Invoked after population of normal bean properties but before an init
	 * callback like InitializingBean's {@code afterPropertiesSet} or a
	 * custom init-method. Invoked after ApplicationContextAware's
	 * {@code setApplicationContext}.
	 * @param servletContext the ServletContext object to be used by this object
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 * @see org.springframework.context.ApplicationContextAware#setApplicationContext
	 */
	void setServletContext(ServletContext servletContext);

}