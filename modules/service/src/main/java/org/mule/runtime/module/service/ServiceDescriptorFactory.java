/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.service;

import static java.io.File.separator;
import static java.lang.String.format;
import static org.mule.runtime.deployment.model.api.plugin.ArtifactPluginDescriptor.MULE_ARTIFACT_FOLDER;
import static org.mule.runtime.module.artifact.descriptor.ArtifactDescriptor.MULE_ARTIFACT_JSON_DESCRIPTOR;
import static org.mule.runtime.module.service.ServiceDescriptor.SERVICE_PROPERTIES;
import org.mule.runtime.api.deployment.meta.MuleServiceModel;
import org.mule.runtime.api.deployment.persistence.MuleServiceModelJsonSerializer;
import org.mule.runtime.module.artifact.descriptor.ArtifactDescriptorCreateException;
import org.mule.runtime.module.artifact.descriptor.ArtifactDescriptorFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.io.IOUtils;

/**
 * Creates {@link ServiceDescriptor} instances.
 */
public class ServiceDescriptorFactory implements ArtifactDescriptorFactory<ServiceDescriptor> {

  public static final String SERVICE_PROVIDER_CLASS_NAME = "service.className";

  @Override
  public ServiceDescriptor create(File artifactFolder) throws ArtifactDescriptorCreateException {
    if (!artifactFolder.exists()) {
      throw new IllegalArgumentException("Service folder does not exists: " + artifactFolder.getAbsolutePath());
    }
    // TODO(pablo.kraan): MULE-13281 - remove properties descriptor support once all the services are migrated to the new file format
    final File servicePropsFile = new File(artifactFolder, SERVICE_PROPERTIES);
    if (servicePropsFile.exists()) {
      final String serviceName = artifactFolder.getName();
      final ServiceDescriptor descriptor = new ServiceDescriptor(serviceName);
      descriptor.setRootFolder(artifactFolder);

      Properties props = new Properties();
      try {
        props.load(new FileReader(servicePropsFile));
      } catch (IOException e) {
        throw new ArtifactDescriptorCreateException("Cannot read service.properties file", e);
      }

      descriptor.setServiceProviderClassName(props.getProperty(SERVICE_PROVIDER_CLASS_NAME));

      return descriptor;
    }
    final File artifactJsonFile = new File(artifactFolder, MULE_ARTIFACT_FOLDER + separator + MULE_ARTIFACT_JSON_DESCRIPTOR);
    if (!artifactJsonFile.exists()) {
      throw new IllegalStateException("Artifact descriptor does not exists: " + artifactJsonFile);
    }

    ServiceDescriptor serviceDescriptor = loadFromJsonDescriptor(artifactFolder, artifactJsonFile);
    serviceDescriptor.setRootFolder(artifactFolder);

    return serviceDescriptor;
  }

  private ServiceDescriptor loadFromJsonDescriptor(File artifactFolder, File artifactJsonDescriptor) {
    final MuleServiceModel artifactModel = getArtifactJsonDescriber(artifactJsonDescriptor);

    final ServiceDescriptor descriptor = createArtifactDescriptor(artifactFolder.getName());
    descriptor.setServiceProviderClassName(artifactModel.getServiceProviderClassName());
    //descriptor.setServiceProviderClassName(artifactModel.ge);
    //descriptor.setArtifactLocation(artifactFolder);
    //descriptor.setRootFolder(artifactFolder);
    //descriptor.setBundleDescriptor(getBundleDescriptor(artifactFolder, artifactModel));
    //descriptor.setMinMuleVersion(new MuleVersion(artifactModel.getMinMuleVersion()));
    //descriptor.setRedeploymentEnabled(artifactModel.isRedeploymentEnabled());
    //doDescriptorConfig(artifactModel, descriptor);
    //
    //List<String> configs = artifactModel.getConfigs();
    //if (configs != null && !configs.isEmpty()) {
    //  descriptor.setConfigResources(configs.stream().map(configFile -> appendMuleFolder(configFile))
    //                                  .collect(toList()));
    //  List<File> configFiles = descriptor.getConfigResources()
    //    .stream()
    //    .map(config -> new File(artifactFolder, config)).collect(toList());
    //  descriptor.setConfigResourcesFile(configFiles.toArray(new File[configFiles.size()]));
    //  descriptor.setAbsoluteResourcePaths(configFiles.stream().map(configFile -> configFile.getAbsolutePath()).collect(toList())
    //                                        .toArray(new String[configFiles.size()]));
    //} else {
    //  File configFile = new File(artifactFolder, appendMuleFolder(getDefaultConfigurationResource()));
    //  descriptor.setConfigResourcesFile(new File[] {configFile});
    //  descriptor.setConfigResources(ImmutableList.<String>builder().add(getDefaultConfigurationResourceLocation()).build());
    //  descriptor.setAbsoluteResourcePaths(new String[] {configFile.getAbsolutePath()});
    //}
    //
    //artifactModel.getClassLoaderModelLoaderDescriptor().ifPresent(classLoaderModelLoaderDescriptor -> {
    //  ClassLoaderModel classLoaderModel = getClassLoaderModel(artifactFolder, classLoaderModelLoaderDescriptor);
    //  descriptor.setClassLoaderModel(classLoaderModel);
    //
    //  try {
    //    descriptor.setPlugins(createArtifactPluginDescriptors(classLoaderModel));
    //  } catch (IOException e) {
    //    throw new IllegalStateException(e);
    //  }
    //});
    return descriptor;
  }

  private MuleServiceModel getArtifactJsonDescriber(File jsonFile) {
    try (InputStream stream = new FileInputStream(jsonFile)) {
      return deserializeArtifactModel(stream);
    } catch (IOException e) {
      throw new IllegalArgumentException(format("Could not read extension describer on artifact '%s'",
                                                jsonFile.getAbsolutePath()),
                                         e);
    }
  }

  private ServiceDescriptor createArtifactDescriptor(String name) {
    return new ServiceDescriptor(name);
  }

  private MuleServiceModel deserializeArtifactModel(InputStream stream) throws IOException {
    return new MuleServiceModelJsonSerializer().deserialize(IOUtils.toString(stream));
  }
}
