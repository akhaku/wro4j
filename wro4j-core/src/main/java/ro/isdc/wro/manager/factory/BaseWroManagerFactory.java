/*
 * Copyright (c) 2008. All rights reserved.
 */
package ro.isdc.wro.manager.factory;

import java.beans.PropertyChangeListener;

import javax.servlet.ServletContext;

import ro.isdc.wro.cache.CacheChangeCallbackAware;
import ro.isdc.wro.cache.CacheEntry;
import ro.isdc.wro.cache.CacheStrategy;
import ro.isdc.wro.cache.ContentHashEntry;
import ro.isdc.wro.cache.impl.MapCacheStrategy;
import ro.isdc.wro.config.Context;
import ro.isdc.wro.config.WroConfigurationChangeListener;
import ro.isdc.wro.manager.WroManager;
import ro.isdc.wro.manager.WroManagerFactory;
import ro.isdc.wro.model.WroModel;
import ro.isdc.wro.model.factory.FallbackAwareWroModelFactory;
import ro.isdc.wro.model.factory.ScheduledWroModelFactory;
import ro.isdc.wro.model.factory.WroModelFactory;
import ro.isdc.wro.model.factory.XmlModelFactory;
import ro.isdc.wro.model.group.DefaultGroupExtractor;
import ro.isdc.wro.model.group.GroupExtractor;
import ro.isdc.wro.model.group.processor.Injector;
import ro.isdc.wro.model.resource.locator.ResourceLocator;
import ro.isdc.wro.model.resource.locator.factory.DefaultResourceLocatorFactory;
import ro.isdc.wro.model.resource.locator.factory.ResourceLocatorFactory;
import ro.isdc.wro.model.resource.locator.support.ServletContextResourceLocator;
import ro.isdc.wro.model.resource.processor.factory.ProcessorsFactory;
import ro.isdc.wro.model.resource.processor.factory.SimpleProcessorsFactory;
import ro.isdc.wro.model.resource.util.HashBuilder;
import ro.isdc.wro.model.resource.util.MD5HashBuilder;


/**
 * A simple implementation of {@link WroManagerFactory} which doesn't define any processors or uriLocators.
 *
 * @author Alex Objelean
 * @created Created on Dec 30, 2009
 */
public abstract class BaseWroManagerFactory
  implements WroManagerFactory, WroConfigurationChangeListener, CacheChangeCallbackAware {
  /**
   * Manager instance. Using volatile keyword fix the problem with double-checked locking in JDK 1.5.
   */
  protected volatile WroManager manager;
  /**
   * A callback to be notified about the cache change.
   */
  private PropertyChangeListener cacheChangeCallback;
  /**
   * Prevent instantiation. Use factory method.
   */
  protected BaseWroManagerFactory() {
  }

  /**
   * Creates default singleton instance of manager, by initializing manager
   * dependencies with default values (processors).
   */
  public final WroManager getInstance() {
    // use double-check locking
    if (this.manager == null) {
      synchronized (this) {
        if (this.manager == null) {
          final GroupExtractor groupExtractor = newGroupExtractor();
          //TODO pass servletContext to this method - it could be useful to access it when creating model.
          //decorate with scheduler ability
          final WroModelFactory modelFactory = new ScheduledWroModelFactory(new FallbackAwareWroModelFactory(
            newModelFactory()));
          final CacheStrategy<CacheEntry, ContentHashEntry> cacheStrategy = newCacheStrategy();
          final Injector injector = new Injector(newResourceLocatorFactory(), newProcessorsFactory());
          this.manager = new WroManager(injector);
          manager.setGroupExtractor(groupExtractor);
          manager.setModelFactory(modelFactory);
          manager.setCacheStrategy(cacheStrategy);
          manager.setHashBuilder(newHashBuilder());
          manager.registerCallback(cacheChangeCallback);
        }
      }
    }
    return this.manager;
  }


  /**
   * By default no processors are used.
   * Override to provide a different or modified factory.
   *
   * @return {@link ProcessorsFactory} object.
   */
  protected ProcessorsFactory newProcessorsFactory() {
    return new SimpleProcessorsFactory();
  }


  /**
   * Override to provide a different or modified factory.
   *
   * @return {@link UriLocatorFactory} object.
   */
  protected ResourceLocatorFactory newResourceLocatorFactory() {
    return DefaultResourceLocatorFactory.contextAwareFactory();
  }

  /**
   * @return {@link HashBuilder} instance.
   */
  protected HashBuilder newHashBuilder() {
    return new MD5HashBuilder();
  }

  /**
   * {@inheritDoc}
   */
  public void registerCallback(final PropertyChangeListener callback) {
    this.cacheChangeCallback = callback;
  }

  /**
   * {@inheritDoc}
   */
  public void onCachePeriodChanged() {
    if (manager != null) {
      manager.onCachePeriodChanged();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void onModelPeriodChanged() {
    if (manager != null) {
      manager.onModelPeriodChanged();
      //update cache too.
      manager.onCachePeriodChanged();
    }
  }

  /**
   * @return {@link CacheStrategy} instance.
   */
  protected CacheStrategy<CacheEntry, ContentHashEntry> newCacheStrategy() {
    return new MapCacheStrategy<CacheEntry, ContentHashEntry>();
  }

  /**
   * @return {@link GroupExtractor} implementation.
   */
  protected GroupExtractor newGroupExtractor() {
    return new DefaultGroupExtractor();
  }


  /**
   * @param servletContext {@link ServletContext} which could be useful for creating dynamic {@link WroModel}.
   * @return {@link WroModelFactory} implementation
   */
  protected WroModelFactory newModelFactory() {
    return new XmlModelFactory() {
      /**
       * This factory will run properly only when is used inside a web application. The configuration xml file will be
       * read from the following location: <code>/WEB-INF/wro.xml</code>
       */
      @Override
      protected ResourceLocator getModelResourceLocator() {
        return new ServletContextResourceLocator(Context.get().getServletContext(), "/WEB-INF/" + XML_CONFIG_FILE);
      }
    };
  }

  /**
   * {@inheritDoc}
   */
  public void destroy() {
    //there is a strange situation when manager actually can be null
    if (manager != null) {
      manager.destroy();
    }
  }
}
