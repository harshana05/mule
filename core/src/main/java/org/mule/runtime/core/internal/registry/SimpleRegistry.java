/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.mule.runtime.core.internal.util.InjectionUtils.getInjectionTarget;
import static org.reflections.ReflectionUtils.getAllFields;
import static org.reflections.ReflectionUtils.getAllMethods;
import static org.reflections.ReflectionUtils.withAnnotation;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.core.api.Injector;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.internal.lifecycle.LifecycleInterceptor;
import org.mule.runtime.core.internal.lifecycle.phases.NotInLifecyclePhase;
import org.mule.runtime.core.privileged.registry.RegistrationException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * A very simple implementation of {@link Registry}. Useful for starting really lightweight contexts which don't depend on heavier
 * object containers such as Spring or Guice (testing being the best example).
 * <p/>
 * The {@link #inject(Object)} operation will only consider fields annotated with {@link Inject} and will perform the injection
 * using simple, not-cached reflection. Also, initialisation lifecycle will be performed in pseudo-random order, no analysis will
 * be done to ensure that dependencies of a given object get their lifecycle before it.
 *
 * @since 3.7.0
 */
public class SimpleRegistry extends TransientRegistry implements Injector {

  private static final String REGISTRY_ID = "org.mule.runtime.core.Registry.Simple";

  public SimpleRegistry(MuleContext muleContext, LifecycleInterceptor lifecycleInterceptor) {
    super(REGISTRY_ID, muleContext, lifecycleInterceptor);
  }

  @Override
  protected void doInitialise() throws InitialisationException {
    injectFieldDependencies();
    super.doInitialise();
  }

  /**
   * This implementation doesn't support applying lifecycle upon lookup and thus this method simply delegates into
   * {@link #lookupObject(String)}
   */
  @Override
  public <T> T lookupObject(String key, boolean applyLifecycle) {
    return lookupObject(key);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void doRegisterObject(String key, Object object, Object metadata) throws RegistrationException {
    Object previous = doGet(key);
    if (previous != null) {
      if (logger.isDebugEnabled()) {
        logger.debug(String.format("An entry already exists for key %s. It will be replaced", key));
      }

      unregisterObject(key);
    }

    doPut(key, object);

    try {
      getLifecycleManager().applyCompletedPhases(object);
    } catch (MuleException e) {
      throw new RegistrationException(e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object applyLifecycle(Object object) throws MuleException {
    getLifecycleManager().applyCompletedPhases(object);
    return object;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object applyLifecycle(Object object, String phase) throws MuleException {
    if (phase == null) {
      getLifecycleManager().applyCompletedPhases(object);
    } else {
      getLifecycleManager().applyPhase(object, NotInLifecyclePhase.PHASE_NAME, phase);
    }
    return object;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void applyLifecycle(Object object, String startPhase, String toPhase) throws MuleException {
    getLifecycleManager().applyPhase(object, startPhase, toPhase);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T inject(T object) {
    object = getInjectionTarget(object);
    return (T) applyProcessors(object, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Object applyProcessors(Object object, Object metadata) {
    return injectInto(super.applyProcessors(object, metadata));
  }

  private void injectFieldDependencies() throws InitialisationException {
    lookupObjects(Object.class).forEach(this::injectInto);
  }

  private <T> T injectInto(T object) {
    for (Field field : getAllFields(object.getClass(), withAnnotation(Inject.class))) {
      try {
        Object dependency = resolveTypedDependency(field.getType(), field.getAnnotation(Named.class),
                                                   () -> ((ParameterizedType) (field.getGenericType()))
                                                       .getActualTypeArguments()[0]);

        field.setAccessible(true);
        if (dependency != null) {
          field.set(object, dependency);
        }
      } catch (Exception e) {
        throw new RuntimeException(format("Could not inject dependency on field %s of type %s", field.getName(),
                                          object.getClass().getName()),
                                   e);
      }
    }
    for (Method method : getAllMethods(object.getClass(), withAnnotation(Inject.class))) {
      if (method.getParameters().length == 1) {
        try {
          Object dependency = resolveTypedDependency(method.getParameterTypes()[0], method.getAnnotation(Named.class),
                                                     () -> ((ParameterizedType) (method.getGenericParameterTypes()[0]))
                                                         .getActualTypeArguments()[0]);

          if (dependency != null) {
            method.invoke(object, dependency);
          }
        } catch (Exception e) {
          throw new RuntimeException(format("Could not inject dependency on method %s of type %s", method.getName(),
                                            object.getClass().getName()),
                                     e);
        }
      }

    }
    return object;
  }

  private Object resolveTypedDependency(Class<?> dependencyType, final Named namedAnnotation, Supplier<Type> typeSupplier)
      throws RegistrationException {
    boolean nullToOptional = false;
    boolean collection = false;
    if (dependencyType.equals(Optional.class)) {
      nullToOptional = true;
    } else if (Collection.class.isAssignableFrom(dependencyType)) {
      collection = true;
    }

    if (nullToOptional || collection) {
      Type type = typeSupplier.get();
      if (type instanceof ParameterizedType) {
        dependencyType = (Class<?>) ((ParameterizedType) type).getRawType();
      } else {
        dependencyType = (Class<?>) type;
      }
    }

    return resolveDependency(dependencyType, nullToOptional, collection, namedAnnotation);
  }

  private Object resolveDependency(Class<?> dependencyType, boolean nullToOptional, boolean collection, Named nameAnnotation)
      throws RegistrationException {
    if (collection) {
      return resolveObjectsToInject(dependencyType);
    } else {
      return resolveObjectToInject(dependencyType, nameAnnotation != null ? nameAnnotation.value() : null, nullToOptional);
    }
  }

  private Object resolveObjectToInject(Class<?> dependencyType, String name, boolean nullToOptional)
      throws RegistrationException {
    Object dependency;
    if (name != null) {
      dependency = lookupObject(name);
    } else {
      dependency = lookupObject(dependencyType);
    }
    if (dependency == null && MuleContext.class.isAssignableFrom(dependencyType)) {
      dependency = muleContext;
    }
    return nullToOptional ? ofNullable(dependency) : dependency;
  }

  private <T> Collection<T> resolveObjectsToInject(Class<T> dependencyType)
      throws RegistrationException {
    Collection<T> dependencies = lookupObjects(dependencyType);
    return dependencies;
  }
}
