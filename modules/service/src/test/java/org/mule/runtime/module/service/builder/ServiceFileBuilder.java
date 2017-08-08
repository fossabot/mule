/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.service.builder;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.emptyMap;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.core.api.util.StringUtils.isBlank;
import static org.mule.runtime.deployment.model.api.application.ApplicationDescriptor.REPOSITORY_FOLDER;
import static org.mule.runtime.deployment.model.api.plugin.MavenClassLoaderConstants.MAVEN;
import static org.mule.runtime.module.artifact.descriptor.ArtifactDescriptor.MULE_ARTIFACT_JSON_DESCRIPTOR_LOCATION;
import org.mule.runtime.api.deployment.meta.MuleArtifactLoaderDescriptor;
import org.mule.runtime.api.deployment.meta.MuleServiceModel.MuleServiceModelBuilder;
import org.mule.runtime.api.deployment.persistence.MuleServiceModelJsonSerializer;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.module.artifact.builder.AbstractArtifactFileBuilder;
import org.mule.runtime.module.artifact.builder.AbstractDependencyFileBuilder;
import org.mule.tck.ZipUtils;
import org.mule.tools.api.classloader.model.ArtifactCoordinates;
import org.mule.tools.api.classloader.model.ClassLoaderModel;
import org.mule.tools.api.packager.ContentGenerator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Creates Service files.
 */
public class ServiceFileBuilder extends AbstractArtifactFileBuilder<ServiceFileBuilder> {


  // TODO(pablo.kraan): deployment - clean up
  private static final String META_INF = "META-INF";
  public static final String CLASSLOADER_MODEL_JSON_DESCRIPTOR = "classloader-model.json";
  public static final String CLASSLOADER_MODEL_JSON_DESCRIPTOR_LOCATION =
    Paths.get("META-INF", "mule-artifact", CLASSLOADER_MODEL_JSON_DESCRIPTOR).toString();
  public static final String MULE_PLUGIN_CLASSIFIER = "mule-plugin";
  public static final String EXTENSION_BUNDLE_TYPE = "jar";
  public static final String MULE_ARTIFACT = "mule-artifact";
  private Properties properties = new Properties();
  // TODO(pablo.kraan): deployment - need lightway package?
  private boolean useHeavyPackage = true;
  private String serviceProviderClassName;

  /**
   * Creates a new builder
   *
   * @param id artifact identifier. Non empty.
   */
  public ServiceFileBuilder(String id) {
    super(id);
  }

  /**
   * Creates a new builder from another instance.
   *
   * @param source instance used as template to build the new one. Non null.
   */
  public ServiceFileBuilder(ServiceFileBuilder source) {
    super(source);
  }

  /**
   * Create a new builder from another instance and different ID.
   *
   * @param id artifact identifier. Non empty.
   * @param source instance used as template to build the new one. Non null.
   */
  public ServiceFileBuilder(String id, ServiceFileBuilder source) {
    super(id, source);
    this.properties.putAll(source.properties);
  }

  @Override
  protected String getFileExtension() {
    return ".zip";
  }

  @Override
  protected ServiceFileBuilder getThis() {
    return this;
  }

  /**
   * Configures the service provider
   *
   * @param className service provider class name. Non blank.
   * @return the same builder instance
   */
  public ServiceFileBuilder withServiceProviderClass(String className) {
    checkImmutable();
    checkArgument(!isBlank(className), "Property value cannot be blank");
    serviceProviderClassName = className;

    return this;
  }

  @Override
  protected final List<ZipUtils.ZipResource> getCustomResources() {
    final List<ZipUtils.ZipResource> customResources = new LinkedList<>();

    for (AbstractDependencyFileBuilder dependencyFileBuilder : getAllCompileDependencies()) {
      customResources.add(new ZipUtils.ZipResource(dependencyFileBuilder.getArtifactFile().getAbsolutePath(),
                                                   Paths.get(REPOSITORY_FOLDER,
                                                             dependencyFileBuilder.getArtifactFileRepositoryPath())
                                                     .toString()));

      // TODO(pablo.kraan): deployment - remove plugins from here
      if (useHeavyPackage && MULE_PLUGIN_CLASSIFIER.equals(dependencyFileBuilder.getClassifier())) {
        File pluginClassLoaderModel = createClassLoaderModelJsonFile(dependencyFileBuilder);
        customResources.add(new ZipUtils.ZipResource(pluginClassLoaderModel.getAbsolutePath(),
                                                     Paths.get(REPOSITORY_FOLDER,
                                                               dependencyFileBuilder.getArtifactFileRepositoryFolderPath(),
                                                               CLASSLOADER_MODEL_JSON_DESCRIPTOR)
                                                       .toString()));
      } else {
        customResources.add(new ZipUtils.ZipResource(dependencyFileBuilder.getArtifactPomFile().getAbsolutePath(),
                                                     Paths.get(REPOSITORY_FOLDER,
                                                               dependencyFileBuilder.getArtifactFilePomRepositoryPath())
                                                       .toString()));
      }
    }

    if (useHeavyPackage) {
      customResources.add(new ZipUtils.ZipResource(getClassLoaderModelFile().getAbsolutePath(),
                                                   CLASSLOADER_MODEL_JSON_DESCRIPTOR_LOCATION));
    }

    File serviceDescriptor = createServiceJsonDescriptorFile();
    customResources.add(new ZipUtils.ZipResource(serviceDescriptor.getAbsolutePath(), MULE_ARTIFACT_JSON_DESCRIPTOR_LOCATION));

    return customResources;
  }

  private File createServiceJsonDescriptorFile() {
    File serviceDescriptor = new File(getTempFolder(), getArtifactId() + "service.json");
    serviceDescriptor.deleteOnExit();
    MuleServiceModelBuilder serviceModelBuilder = new MuleServiceModelBuilder();
    serviceModelBuilder.setName(getArtifactId()).setMinMuleVersion("4.0.0");
    //domain.ifPresent(serviceModelBuilder::setDomain);
    //redeploymentEnabled.ifPresent(serviceModelBuilder::setRedeploymentEnabled);
    //configResources.ifPresent(configs -> {
    //  String[] configFiles = configs.split(",");
    //  serviceModelBuilder.setConfigs(asList(configFiles));
    //});
    serviceModelBuilder.withClassLoaderModelDescriber().setId(MAVEN);
    //exportedResources.ifPresent(resources -> {
    //  serviceModelBuilder.withClassLoaderModelDescriber().addProperty(EXPORTED_RESOURCES, resources.split(","));
    //});
    serviceModelBuilder.withBundleDescriptorLoader(new MuleArtifactLoaderDescriptor(MAVEN, emptyMap()));
    serviceModelBuilder.withServiceProviderClassName(serviceProviderClassName);
    String serviceDescriptorContent = new MuleServiceModelJsonSerializer().serialize(serviceModelBuilder.build());
    try (FileWriter fileWriter = new FileWriter(serviceDescriptor)) {
      fileWriter.write(serviceDescriptorContent);
    } catch (IOException e) {
      throw new MuleRuntimeException(e);
    }
    return serviceDescriptor;
  }

  private File createClassLoaderModelJsonFile(AbstractDependencyFileBuilder dependencyFileBuilder) {
    ArtifactCoordinates artifactCoordinates =
      new ArtifactCoordinates(dependencyFileBuilder.getGroupId(), dependencyFileBuilder.getArtifactId(),
                              dependencyFileBuilder.getVersion(), dependencyFileBuilder.getType(),
                              dependencyFileBuilder.getClassifier());
    ClassLoaderModel classLoaderModel = new ClassLoaderModel("1.0", artifactCoordinates);

    List<org.mule.tools.api.classloader.model.Artifact> artifactDependencies = new LinkedList<>();
    List<AbstractDependencyFileBuilder> dependencies = dependencyFileBuilder.getDependencies();
    for (AbstractDependencyFileBuilder fileBuilderDependency : dependencies) {
      artifactDependencies.add(getArtifact(fileBuilderDependency));
    }

    classLoaderModel.setDependencies(artifactDependencies);

    Path repository = Paths.get(getTempFolder(), REPOSITORY_FOLDER, dependencyFileBuilder.getArtifactFileRepositoryFolderPath());
    if (repository.toFile().exists()) {
      repository.toFile().delete();
    } else {
      if (!repository.toFile().mkdirs()) {
        throw new IllegalStateException("Cannot create artifact folder inside repository");
      }
    }

    return ContentGenerator.createClassLoaderModelJsonFile(classLoaderModel, repository.toFile());
  }

  private File getClassLoaderModelFile() {
    ArtifactCoordinates artifactCoordinates = new ArtifactCoordinates(getGroupId(), getArtifactId(), getVersion());
    ClassLoaderModel classLoaderModel = new ClassLoaderModel("1.0", artifactCoordinates);

    List<org.mule.tools.api.classloader.model.Artifact> artifactDependencies = new LinkedList<>();
    for (AbstractDependencyFileBuilder fileBuilderDependency : getDependencies()) {
      artifactDependencies.add(getArtifact(fileBuilderDependency));
    }

    classLoaderModel.setDependencies(artifactDependencies);

    File destinationFolder = Paths.get(getTempFolder()).resolve(META_INF).resolve(MULE_ARTIFACT).toFile();

    if (!destinationFolder.exists()) {
      assertThat(destinationFolder.mkdirs(), is(true));
    }
    return ContentGenerator.createClassLoaderModelJsonFile(classLoaderModel, destinationFolder);
  }

  private org.mule.tools.api.classloader.model.Artifact getArtifact(AbstractDependencyFileBuilder builder) {
    ArtifactCoordinates artifactCoordinates =
      new ArtifactCoordinates(builder.getGroupId(), builder.getArtifactId(), builder.getVersion(), builder.getType(),
                              builder.getClassifier());
    return new org.mule.tools.api.classloader.model.Artifact(artifactCoordinates, builder.getArtifactFile().toURI());
  }

  @Override
  public String getConfigFile() {
    return null;
  }
}
