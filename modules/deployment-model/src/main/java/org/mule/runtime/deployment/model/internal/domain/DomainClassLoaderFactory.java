/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.deployment.model.internal.domain;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.deployment.model.api.domain.DomainDescriptor.DEFAULT_DOMAIN_NAME;
import static org.mule.runtime.module.artifact.api.classloader.ParentFirstLookupStrategy.PARENT_FIRST;

import org.mule.runtime.deployment.model.api.DeploymentException;
import org.mule.runtime.deployment.model.api.domain.DomainDescriptor;
import org.mule.runtime.deployment.model.api.plugin.ArtifactPluginDescriptor;
import org.mule.runtime.deployment.model.internal.nativelib.DefaultNativeLibraryFinderFactory;
import org.mule.runtime.deployment.model.internal.nativelib.NativeLibraryFinder;
import org.mule.runtime.deployment.model.internal.nativelib.NativeLibraryFinderFactory;
import org.mule.runtime.module.artifact.api.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.api.classloader.ClassLoaderLookupPolicy;
import org.mule.runtime.module.artifact.api.classloader.DeployableArtifactClassLoaderFactory;
import org.mule.runtime.module.artifact.api.classloader.LookupStrategy;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates {@link ArtifactClassLoader} for domain artifacts.
 */
public class DomainClassLoaderFactory implements DeployableArtifactClassLoaderFactory<DomainDescriptor> {

  protected static final Logger logger = LoggerFactory.getLogger(DomainClassLoaderFactory.class);

  private final NativeLibraryFinderFactory nativeLibraryFinderFactory;
  private final Map<String, ArtifactClassLoader> domainArtifactClassLoaders = new HashMap<>();

  /**
   * Creates a new instance
   *
   * @param nativeLibraryFinderFactory creates {@link NativeLibraryFinder} for the created module
   *
   */
  public DomainClassLoaderFactory(NativeLibraryFinderFactory nativeLibraryFinderFactory) {
    checkArgument(nativeLibraryFinderFactory != null, "nativeLibraryFinderFactory cannot be null");
    this.nativeLibraryFinderFactory = nativeLibraryFinderFactory;
  }

  /**
   * Creates a new factory
   *
   * @param nativeLibsTempFolderChildFunction a function to determine the location of a temp dir to copy the native libs of the
   *                                          artifact to, based on the deployment name.
   */
  public DomainClassLoaderFactory(Function<String, File> nativeLibsTempFolderChildFunction) {
    this.nativeLibraryFinderFactory = new DefaultNativeLibraryFinderFactory(nativeLibsTempFolderChildFunction);
  }

  /**
   * @param domainName name of the domain. Non empty.
   * @return the unique identifier for the domain in the container.
   */
  public static String getDomainId(String domainName) {
    checkArgument(!isEmpty(domainName), "domainName cannot be empty");

    return "domain/" + domainName;
  }

  @Override
  public ArtifactClassLoader create(String artifactId, ArtifactClassLoader parent, DomainDescriptor descriptor,
                                    List<ArtifactClassLoader> artifactClassLoaders) {
    final String domainId = getDomainId(descriptor.getName());

    ArtifactClassLoader domainClassLoader = domainArtifactClassLoaders.get(domainId);
    if (domainClassLoader != null) {
      return domainClassLoader;
    } else {
      synchronized (this) {
        domainClassLoader = domainArtifactClassLoaders.get(domainId);
        if (domainClassLoader == null) {
          if (descriptor.getName().equals(DEFAULT_DOMAIN_NAME)) {
            domainClassLoader = getDefaultDomainClassLoader(parent, parent.getClassLoaderLookupPolicy());
          } else {
            NativeLibraryFinder nativeLibraryFinder =
                nativeLibraryFinderFactory.create(descriptor.getDataFolderName(), descriptor.getClassLoaderModel().getUrls());
            domainClassLoader = getCustomDomainClassLoader(parent, descriptor, artifactClassLoaders, nativeLibraryFinder);
          }

          domainClassLoader.addShutdownListener(() -> {
            synchronized (DomainClassLoaderFactory.this) {
              domainArtifactClassLoaders.remove(domainId);
            }
          });

          domainArtifactClassLoaders.put(domainId, domainClassLoader);
        }
      }
    }

    return domainClassLoader;
  }

  private ArtifactClassLoader getCustomDomainClassLoader(ArtifactClassLoader parent, DomainDescriptor domain,
                                                         List<ArtifactClassLoader> artifactClassLoaders,
                                                         NativeLibraryFinder nativeLibraryFinder) {
    validateDomain(domain);

    final ClassLoaderLookupPolicy classLoaderLookupPolicy = getApplicationClassLoaderLookupPolicy(parent, domain);

    ArtifactClassLoader classLoader =
        new MuleSharedDomainClassLoader(domain, parent.getClassLoader(), classLoaderLookupPolicy, Arrays
            .asList(domain.getClassLoaderModel().getUrls()), artifactClassLoaders, nativeLibraryFinder);

    return classLoader;
  }

  private ClassLoaderLookupPolicy getApplicationClassLoaderLookupPolicy(ArtifactClassLoader parent,
                                                                        DomainDescriptor descriptor) {

    final Map<String, LookupStrategy> pluginsLookupStrategies = new HashMap<>();

    for (ArtifactPluginDescriptor artifactPluginDescriptor : descriptor.getPlugins()) {
      artifactPluginDescriptor.getClassLoaderModel().getExportedPackages()
          .forEach(p -> pluginsLookupStrategies.put(p, PARENT_FIRST));
    }

    return parent.getClassLoaderLookupPolicy().extend(pluginsLookupStrategies);
  }

  private ArtifactClassLoader getDefaultDomainClassLoader(ArtifactClassLoader parent,
                                                          ClassLoaderLookupPolicy containerLookupPolicy) {
    return new MuleSharedDomainClassLoader(new DomainDescriptor(DEFAULT_DOMAIN_NAME), parent.getClassLoader(),
                                           containerLookupPolicy.extend(emptyMap()), emptyList(), emptyList());
  }

  private void validateDomain(DomainDescriptor domainDescriptor) {
    File domainFolder = domainDescriptor.getRootFolder();
    if (!(domainFolder.exists() && domainFolder.isDirectory())) {
      throw new DeploymentException(createStaticMessage(format("Domain %s does not exists", domainDescriptor.getName())));
    }
  }

}
