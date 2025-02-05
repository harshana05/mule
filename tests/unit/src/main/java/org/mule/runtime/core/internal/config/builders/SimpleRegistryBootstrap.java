/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.config.builders;

import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.setMuleContextIfNeeded;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.DataTypeParamsBuilder;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.bootstrap.ArtifactType;
import org.mule.runtime.core.api.config.i18n.CoreMessages;
import org.mule.runtime.core.api.transformer.DiscoverableTransformer;
import org.mule.runtime.core.api.transformer.Transformer;
import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.core.internal.config.bootstrap.AbstractRegistryBootstrap;
import org.mule.runtime.core.internal.config.bootstrap.BootstrapObjectFactory;
import org.mule.runtime.core.internal.config.bootstrap.ObjectBootstrapProperty;
import org.mule.runtime.core.internal.config.bootstrap.TransformerBootstrapProperty;
import org.mule.runtime.core.internal.context.MuleContextWithRegistry;
import org.mule.runtime.core.internal.registry.SimpleRegistry;
import org.mule.runtime.core.internal.util.StreamCloser;
import org.mule.runtime.core.privileged.registry.ObjectProcessor;
import org.mule.runtime.core.privileged.registry.RegistrationException;

/**
 * An implementation of {@link AbstractRegistryBootstrap} to populate instances of {@link SimpleRegistry}
 *
 * @deprecated as of 3.7.0. Try to use {@see org.mule.runtime.core.config.spring.SpringRegistryBootstrap} instead.
 */
@Deprecated
public class SimpleRegistryBootstrap extends AbstractRegistryBootstrap {

  /**
   * @param supportedArtifactType type of the artifact to support. This attributes defines which types of registry bootstrap
   *                              entries will be created depending on the entry applyToArtifactType parameter value.
   * @param muleContext           {@code MuleContext} in which the objects will be registered
   */
  public SimpleRegistryBootstrap(ArtifactType supportedArtifactType, MuleContext muleContext) {
    super(supportedArtifactType, muleContext, k -> true);
  }

  @Override
  protected void doRegisterTransformer(TransformerBootstrapProperty bootstrapProperty, Class<?> returnClass,
                                       Class<? extends Transformer> transformerClass)
      throws Exception {
    Transformer trans = ClassUtils.instantiateClass(transformerClass);
    if (!(trans instanceof DiscoverableTransformer)) {
      throw new RegistrationException(CoreMessages.transformerNotImplementDiscoverable(trans));
    }
    if (returnClass != null) {
      DataTypeParamsBuilder builder = DataType.builder().type(returnClass);
      if (isNotEmpty(bootstrapProperty.getMimeType())) {
        builder = builder.mediaType(bootstrapProperty.getMimeType());
      }
      trans.setReturnDataType(builder.build());
    }
    if (bootstrapProperty.getName() != null) {
      trans.setName(bootstrapProperty.getName());
    } else {
      // Prefixes the generated default name to ensure there is less chance of conflict if the user registers
      // the transformer with the same name
      trans.setName("_" + trans.getName());
    }
    ((MuleContextWithRegistry) muleContext).getRegistry().registerObject(trans.getName(), trans);
  }

  @Override
  protected void doRegisterObject(ObjectBootstrapProperty bootstrapProperty) throws Exception {
    Object value = bootstrapProperty.getService().instantiateClass(bootstrapProperty.getClassName());
    Class<?> meta = Object.class;

    if (value instanceof ObjectProcessor) {
      meta = ObjectProcessor.class;
    } else if (value instanceof StreamCloser) {
      meta = StreamCloser.class;
    } else if (value instanceof BootstrapObjectFactory) {
      setMuleContextIfNeeded(value, muleContext);
      value = ((BootstrapObjectFactory) value).create();
    }
    ((MuleContextWithRegistry) muleContext).getRegistry().registerObject(bootstrapProperty.getKey(), value, meta);
  }
}
