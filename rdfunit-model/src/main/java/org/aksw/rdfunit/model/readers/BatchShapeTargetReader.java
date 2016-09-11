package org.aksw.rdfunit.model.readers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.aksw.rdfunit.enums.ShapeTargetType;
import org.aksw.rdfunit.model.impl.ShapeTargetCore;
import org.aksw.rdfunit.model.impl.ShapeTargetValueShape;
import org.aksw.rdfunit.model.interfaces.ShapeTarget;
import org.aksw.rdfunit.vocabulary.SHACL;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Description
 *
 * @author Dimitris Kontokostas
 * @since 10/19/15 7:23 PM
 * @version $Id: $Id
 */
public final class BatchShapeTargetReader {

    private BatchShapeTargetReader() {
    }

    /**
     * <p>create.</p>
     *
     * @return a {@link org.aksw.rdfunit.model.readers.PatternReader} object.
     */
    public static BatchShapeTargetReader create() {
        return new BatchShapeTargetReader();
    }

    public Set<ShapeTarget> read(Resource resource) {
        checkNotNull(resource);

        ImmutableSet.Builder<ShapeTarget> scopeBuilder = ImmutableSet.builder();

        scopeBuilder.addAll(collectExplicitScopes(resource));

        scopeBuilder.addAll(collectValueShapeScopes(resource));

        return scopeBuilder.build();

    }

    private Set<ShapeTarget> collectExplicitScopes(Resource resource) {
        ImmutableSet.Builder<ShapeTarget> scopeBuilder = ImmutableSet.builder();

        scopeBuilder.addAll(collectClassTarget(resource));
        scopeBuilder.addAll(collectNodeTarget(resource));
        scopeBuilder.addAll(collectSubjectsOfTarget(resource));
        scopeBuilder.addAll(collectObjectsOfTarget(resource));

        return scopeBuilder.build();
    }

    private List<ShapeTarget> collectClassTarget(Resource resource) {
        return resource.listProperties(SHACL.targetClass)
                .toList().stream()
                .filter(smt -> smt.getObject().isResource())
                .map(smt -> ShapeTargetCore.create(ShapeTargetType.ClassTarget, smt.getObject().asResource().getURI()))
                .collect(Collectors.toList());
    }

    private List<ShapeTarget> collectNodeTarget(Resource resource) {
        return collectPropertyHelper(resource, SHACL.targetNode, ShapeTargetType.NodeTarget);
    }

    private List<ShapeTarget> collectSubjectsOfTarget(Resource resource) {
        return collectPropertyHelper(resource, SHACL.targetSubjectsOf, ShapeTargetType.SubjectsOfTarget);
    }

    private List<ShapeTarget> collectObjectsOfTarget(Resource resource) {
        return collectPropertyHelper(resource, SHACL.targetObjectsOf, ShapeTargetType.ObjectsOfTarget);
    }

    private List<ShapeTarget> collectPropertyHelper(Resource resourceShape, Property targetProperty, ShapeTargetType shapeTargetType) {
        return resourceShape.listProperties(targetProperty)
                .toList().stream()
                .filter(smt -> smt.getObject().isResource())
                .map(smt -> ShapeTargetCore.create(shapeTargetType, smt.getObject().asResource().getURI()))
                .collect(Collectors.toList());
    }

    private Set<ShapeTarget> collectValueShapeScopes(Resource resource){
        return collectValueShapeScopes(resource, Collections.emptyList());
    }

    private Set<ShapeTarget> collectValueShapeScopes(Resource resource, List<Resource> propertyChain){
        ImmutableSet.Builder<ShapeTarget> scopes = ImmutableSet.builder();

        List<Resource> tmp = resource.getModel().listResourcesWithProperty(SHACL.valueShape, resource).toList();

                tmp.forEach( r -> {
                    Resource property = r.getPropertyResourceValue(SHACL.predicate);
                    getParentShapeResources(r)
                            .forEach(shape -> {
                                ImmutableList<Resource> propChainNew = new ImmutableList.Builder<Resource>().add(property).addAll(propertyChain).build();
                                collectExplicitScopes(shape).forEach(scope ->
                                        scopes.add(ShapeTargetValueShape.create(scope, propChainNew)));

                                scopes.addAll(collectValueShapeScopes(shape, propChainNew));
                            });
                });

        return scopes.build();
    }

    private List<Resource> getParentShapeResources(Resource resource) {
        return resource.getModel().listResourcesWithProperty(SHACL.property, resource).toList();
    }
}

