/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal;

import static java.util.stream.Collectors.toList;

import org.mule.runtime.api.util.Pair;
import org.mule.runtime.config.internal.dsl.model.ConfigurationDependencyResolver;
import org.mule.runtime.config.internal.registry.BeanDependencyResolver;
import org.mule.runtime.config.internal.registry.SpringContextRegistry;
import org.mule.runtime.core.internal.lifecycle.InjectedDependenciesProvider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;

public class DummyDependencyResolver implements BeanDependencyResolver {

  private final SpringContextRegistry springRegistry;
  private final Set<String> processedKey;
  private final ConfigurationDependencyResolver configurationDependencyResolver;
  private final DeclaredDependencyResolver declaredDependencyResolver;
  private final AutoDiscoveredDependencyResolver autoDiscoveredDependencyResolver;


  public DummyDependencyResolver(ConfigurationDependencyResolver configurationDependencyResolver,
                                 DeclaredDependencyResolver declaredDependencyResolver,
                                 AutoDiscoveredDependencyResolver autoDiscoveredDependencyResolver,
                                 SpringContextRegistry springRegistry) {
    this.configurationDependencyResolver = configurationDependencyResolver;
    this.declaredDependencyResolver = declaredDependencyResolver;
    this.autoDiscoveredDependencyResolver = autoDiscoveredDependencyResolver;
    this.springRegistry = springRegistry;
    processedKey = new HashSet<>();
  }

  public List<Object> getDirectDependencies(String name) {
    return resolveBeanDependencies(name);
  }

  @Override
  public List<Object> resolveBeanDependencies(String beanName) {
    Object currentObject = springRegistry.get(beanName);
    final DependencyNode currentNode = new DependencyNode(currentObject);

    addDirectDependency(beanName, currentObject, currentNode, processedKey);

    return currentNode.getChildren()
        .stream()
        .map(DependencyNode::getValue)
        .collect(toList());
  }

  public List<Pair<Object, String>> getDirectBeanDependencies(String beanName) {
    Object currentObject = springRegistry.get(beanName);
    final DependencyNode currentNode = new DependencyNode(currentObject, beanName);

    addDirectDependency(beanName, currentObject, currentNode, processedKey);

    return currentNode.getChildren()
        .stream()
        .map(DependencyNode::getObjectKeyPair)
        .collect(toList());
  }


  private void addDirectDependency(String key, Object object, DependencyNode node, Set<String> processedKeys) {
    addDirectAutoDiscoveredDependencies(key, processedKeys, node);
    addDirectConfigurationDependencies(key, processedKeys, node);
    addDirectDeclaredDependencies(object, processedKeys, node);
  }

  /**
   * If the target object implements {@link InjectedDependenciesProvider}, then the custom dependencies declared by it are added.
   */
  private void addDirectDeclaredDependencies(Object object, Set<String> processedKeys, DependencyNode node) {
    declaredDependencyResolver.getDeclaredDirectDependencies(object)
        .forEach(pair -> addDirectChild(node, pair.getFirst(), pair.getSecond(), processedKeys));
  }

  /**
   * These are obtained through the {@link #configurationDependencyResolver}
   */
  private void addDirectConfigurationDependencies(String key, Set<String> processedKeys, DependencyNode node) {
    if (configurationDependencyResolver == null) {
      return;
    }
    for (String dependency : configurationDependencyResolver.getDirectComponentDependencies(key)) {
      try {
        if (springRegistry.isSingleton(dependency)) { // to use it, configResolver, make it static..
          addDirectChild(node, dependency, springRegistry.get(dependency), processedKeys);
        }
      } catch (NoSuchBeanDefinitionException e) {
        // we're starting in lazy mode... disregard.
      }
    }
  }

  /**
   * Adds the dependencies that are explicit on the {@link BeanDefinition}. These were inferred from introspecting fields
   * annotated with {@link Inject} or were programmatically added to the definition
   */
  private void addDirectAutoDiscoveredDependencies(String key, Set<String> processedKeys, DependencyNode node) {
    autoDiscoveredDependencyResolver.getAutoDiscoveredDependencies(key)
        .stream().filter(x -> !x.getValue().equals(node.getValue()))
        .forEach(dependency -> addDirectChild(node, dependency.getKey(), dependency.getValue(), processedKeys));
  }


  private void addDirectChild(DependencyNode parent, String key, Object childObject, Set<String> processedKeys) {
    if (!processedKeys.add(key)) {
      return;
    }
    parent.addChild(new DependencyNode(childObject, key));
  }



}
