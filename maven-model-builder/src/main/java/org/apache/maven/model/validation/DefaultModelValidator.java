package org.apache.maven.model.validation;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.ModelBuildingRequest;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Repository;
import org.apache.maven.model.Resource;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.StringUtils;

/**
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
@Component(role = ModelValidator.class )
public class DefaultModelValidator
    implements ModelValidator
{

    private static final String ID_REGEX = "[A-Za-z0-9_\\-.]+";

    public ModelValidationResult validateRawModel( Model model, ModelBuildingRequest request )
    {
        ModelValidationResult result = new ModelValidationResult();

        Parent parent = model.getParent();
        if ( parent != null )
        {
            validateStringNotEmpty( "parent.groupId", result, parent.getGroupId() );

            validateStringNotEmpty( "parent.artifactId", result, parent.getArtifactId() );

            validateStringNotEmpty( "parent.version", result, parent.getVersion() );

            if ( parent.getGroupId().equals( model.getGroupId() )
                && parent.getArtifactId().equals( model.getArtifactId() ) )
            {
                result.addMessage( "The parent element cannot have the same ID as the project." );
            }
        }

        if ( !request.istLenientValidation() )
        {
            validateDependencies( result, model.getDependencies(), "dependencies" );

            if ( model.getDependencyManagement() != null )
            {
                validateDependencies( result, model.getDependencyManagement().getDependencies(),
                                      "dependencyManagment.dependencies" );
            }

            validateRepositories( result, model.getRepositories(), "repositories.repository" );

            validateRepositories( result, model.getPluginRepositories(), "pluginRepositories.pluginRepository" );

            for ( Profile profile : model.getProfiles() )
            {
                validateDependencies( result, profile.getDependencies(), "profiles.profile[" + profile.getId()
                    + "].dependencies" );

                if ( profile.getDependencyManagement() != null )
                {
                    validateDependencies( result, profile.getDependencyManagement().getDependencies(),
                                          "profiles.profile[" + profile.getId() + "].dependencyManagment.dependencies" );
                }

                validateRepositories( result, profile.getRepositories(), "profiles.profile[" + profile.getId()
                    + "].repositories.repository" );

                validateRepositories( result, profile.getPluginRepositories(), "profiles.profile[" + profile.getId()
                    + "].pluginRepositories.pluginRepository" );
            }
        }

        return result;
    }

    public ModelValidationResult validateEffectiveModel( Model model, ModelBuildingRequest request )
    {
        ModelValidationResult result = new ModelValidationResult();

        validateStringNotEmpty( "modelVersion", result, model.getModelVersion() );

        validateId( "groupId", result, model.getGroupId() );

        validateId( "artifactId", result, model.getArtifactId() );

        validateStringNotEmpty( "packaging", result, model.getPackaging() );

        if ( !model.getModules().isEmpty() && !"pom".equals( model.getPackaging() ) )
        {
            result.addMessage( "Packaging '" + model.getPackaging() + "' is invalid. Aggregator projects " +
                "require 'pom' as packaging." );
        }

        Parent parent = model.getParent();
        if ( parent != null )
        {
            if ( parent.getGroupId().equals( model.getGroupId() ) &&
                parent.getArtifactId().equals( model.getArtifactId() ) )
            {
                result.addMessage( "The parent element cannot have the same ID as the project." );
            }
        }

        validateStringNotEmpty( "version", result, model.getVersion() );

        for ( Dependency d : model.getDependencies() )
        {
            validateId( "dependencies.dependency.artifactId", result, d.getArtifactId() );

            validateId( "dependencies.dependency.groupId", result, d.getGroupId() );

            validateStringNotEmpty( "dependencies.dependency.type", result, d.getType(), d.getManagementKey() );

            validateStringNotEmpty( "dependencies.dependency.version", result, d.getVersion(),
                                    d.getManagementKey() );

            if ( "system".equals( d.getScope() ) )
            {
                String systemPath = d.getSystemPath();

                if ( StringUtils.isEmpty( systemPath ) )
                {
                    result.addMessage( "For dependency " + d + ": system-scoped dependency must specify systemPath." );
                }
                else
                {
                    if ( !new File( systemPath ).isAbsolute() )
                    {
                        result.addMessage( "For dependency " + d + ": system-scoped dependency must " +
                            "specify an absolute path systemPath." );
                    }
                }
            }
            else if ( StringUtils.isNotEmpty( d.getSystemPath() ) )
            {
                result.addMessage(
                    "For dependency " + d + ": only dependency with system scope can specify systemPath." );
            }
        }

        DependencyManagement mgmt = model.getDependencyManagement();
        if ( mgmt != null )
        {
            for ( Dependency d : mgmt.getDependencies() )
            {
                validateSubElementStringNotEmpty( d, "dependencyManagement.dependencies.dependency.artifactId", result,
                                                  d.getArtifactId() );

                validateSubElementStringNotEmpty( d, "dependencyManagement.dependencies.dependency.groupId", result,
                                                  d.getGroupId() );

                if ( "system".equals( d.getScope() ) )
                {
                    String systemPath = d.getSystemPath();

                    if ( StringUtils.isEmpty( systemPath ) )
                    {
                        result.addMessage(
                            "For managed dependency " + d + ": system-scoped dependency must specify systemPath." );
                    }
                    else
                    {
                        if ( !new File( systemPath ).isAbsolute() )
                        {
                            result.addMessage( "For managed dependency " + d + ": system-scoped dependency must " +
                                "specify an absolute path systemPath." );
                        }
                    }
                }
                else if ( StringUtils.isNotEmpty( d.getSystemPath() ) )
                {
                    result.addMessage(
                        "For managed dependency " + d + ": only dependency with system scope can specify systemPath." );
                }
            }
        }

        if ( !request.istLenientValidation() )
        {
            Build build = model.getBuild();
            if ( build != null )
            {
                for ( Plugin p : build.getPlugins() )
                {
                    validateStringNotEmpty( "build.plugins.plugin.artifactId", result, p.getArtifactId() );

                    validateStringNotEmpty( "build.plugins.plugin.groupId", result, p.getGroupId() );

                    validateStringNotEmpty( "build.plugins.plugin.version", result, p.getVersion(), p.getKey() );
                }

                validateResources( result, build.getResources(), "build.resources.resource" );

                validateResources( result, build.getTestResources(), "build.testResources.testResource" );
            }

            Reporting reporting = model.getReporting();
            if ( reporting != null )
            {
                for ( ReportPlugin p : reporting.getPlugins() )
                {
                    validateStringNotEmpty( "reporting.plugins.plugin.artifactId", result, p.getArtifactId() );

                    validateStringNotEmpty( "reporting.plugins.plugin.groupId", result, p.getGroupId() );

                    validateStringNotEmpty( "reporting.plugins.plugin.version", result, p.getVersion(), p.getKey() );
                }
            }

            forcePluginExecutionIdCollision( model, result );
        }

        return result;
    }

    private boolean validateId( String fieldName, ModelValidationResult result, String id )
    {
        if ( !validateStringNotEmpty( fieldName, result, id ) )
        {
            return false;
        }
        else
        {
            boolean match = id.matches( ID_REGEX );
            if ( !match )
            {
                result.addMessage( "'" + fieldName + "' with value '" + id + "' does not match a valid id pattern." );
            }
            return match;
        }
    }

    private void validateDependencies( ModelValidationResult result, List<Dependency> dependencies, String prefix )
    {
        Map<String, Dependency> index = new HashMap<String, Dependency>();

        for ( Dependency dependency : dependencies )
        {
            String key = dependency.getManagementKey();

            Dependency existing = index.get( key );

            if ( existing != null )
            {
                result.addMessage( "'" + prefix + ".(groupId:artifactId:type:classifier)' must be unique: " + key
                    + " -> " + existing.getVersion() + " vs " + dependency.getVersion() );
            }
            else
            {
                index.put( key, dependency );
            }
        }
    }

    private void validateRepositories( ModelValidationResult result, List<Repository> repositories, String prefix )
    {
        Map<String, Repository> index = new HashMap<String, Repository>();

        for ( Repository repository : repositories )
        {
            validateStringNotEmpty( prefix + ".id", result, repository.getId() );

            validateStringNotEmpty( prefix + ".url", result, repository.getUrl() );

            String key = repository.getId();

            Repository existing = index.get( key );

            if ( existing != null )
            {
                result.addMessage( "'" + prefix + ".id' must be unique: " + repository.getId() + " -> "
                    + existing.getUrl() + " vs " + repository.getUrl() );
            }
            else
            {
                index.put( key, repository );
            }
        }
    }

    private void validateResources( ModelValidationResult result, List<Resource> resources, String prefix )
    {
        for ( Resource resource : resources )
        {
            validateStringNotEmpty( prefix + ".directory", result, resource.getDirectory() );
        }
    }

    private void forcePluginExecutionIdCollision( Model model, ModelValidationResult result )
    {
        Build build = model.getBuild();

        if ( build != null )
        {
            List<Plugin> plugins = build.getPlugins();

            if ( plugins != null )
            {
                for ( Plugin plugin : plugins )
                {
                    // this will force an IllegalStateException, even if we don't have to do inheritance assembly.
                    try
                    {
                        plugin.getExecutionsAsMap();
                    }
                    catch ( IllegalStateException collisionException )
                    {
                        result.addMessage( collisionException.getMessage() );
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // Field validation
    // ----------------------------------------------------------------------

    private boolean validateStringNotEmpty( String fieldName, ModelValidationResult result, String string )
    {
        return validateStringNotEmpty( fieldName, result, string, null );
    }

    /**
     * Asserts:
     * <p/>
     * <ul>
     * <li><code>string.length != null</code>
     * <li><code>string.length > 0</code>
     * </ul>
     */
    private boolean validateStringNotEmpty( String fieldName, ModelValidationResult result, String string,
                                            String sourceHint )
    {
        if ( !validateNotNull( fieldName, result, string, sourceHint ) )
        {
            return false;
        }

        if ( string.length() > 0 )
        {
            return true;
        }

        if ( sourceHint != null )
        {
            result.addMessage( "'" + fieldName + "' is missing for " + sourceHint );
        }
        else
        {
            result.addMessage( "'" + fieldName + "' is missing." );
        }

        return false;
    }

    /**
     * Asserts:
     * <p/>
     * <ul>
     * <li><code>string.length != null</code>
     * <li><code>string.length > 0</code>
     * </ul>
     */
    private boolean validateSubElementStringNotEmpty( Object subElementInstance, String fieldName,
                                                      ModelValidationResult result, String string )
    {
        if ( !validateSubElementNotNull( subElementInstance, fieldName, result, string ) )
        {
            return false;
        }

        if ( string.length() > 0 )
        {
            return true;
        }

        result.addMessage( "In " + subElementInstance + ":\n\n       -> '" + fieldName + "' is missing." );

        return false;
    }

    /**
     * Asserts:
     * <p/>
     * <ul>
     * <li><code>string != null</code>
     * </ul>
     */
    private boolean validateNotNull( String fieldName, ModelValidationResult result, Object object, String sourceHint )
    {
        if ( object != null )
        {
            return true;
        }

        if ( sourceHint != null )
        {
            result.addMessage( "'" + fieldName + "' is missing for " + sourceHint );
        }
        else
        {
            result.addMessage( "'" + fieldName + "' is missing." );
        }

        return false;
    }

    /**
     * Asserts:
     * <p/>
     * <ul>
     * <li><code>string != null</code>
     * </ul>
     */
    private boolean validateSubElementNotNull( Object subElementInstance, String fieldName,
                                               ModelValidationResult result, Object object )
    {
        if ( object != null )
        {
            return true;
        }

        result.addMessage( "In " + subElementInstance + ":\n\n       -> '" + fieldName + "' is missing." );

        return false;
    }

}