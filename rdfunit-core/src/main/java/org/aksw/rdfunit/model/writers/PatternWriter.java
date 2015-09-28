package org.aksw.rdfunit.model.writers;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.RDF;
import org.aksw.rdfunit.model.interfaces.Pattern;
import org.aksw.rdfunit.model.interfaces.PatternParameter;
import org.aksw.rdfunit.model.interfaces.ResultAnnotation;
import org.aksw.rdfunit.vocabulary.RDFUNITv;

/**
 * Description
 *
 * @author Dimitris Kontokostas
 * @since 6/17/15 5:57 PM
 * @version $Id: $Id
 */
public final class PatternWriter implements ElementWriter {

    private final Pattern pattern;

    private PatternWriter(Pattern pattern) {
        this.pattern = pattern;
    }

    public static PatternWriter createPatternWriter(Pattern pattern) {return new PatternWriter(pattern);}

    /** {@inheritDoc} */
    @Override
    public Resource write(Model model) {
        Resource resource = ElementWriterUtils.copyElementResourceInModel(pattern, model);

        resource
                .addProperty(RDF.type, RDFUNITv.Pattern)
                .addProperty(DCTerms.identifier, pattern.getId())
                .addProperty(DCTerms.description, pattern.getDescription())
                .addProperty(RDFUNITv.sparqlWherePattern, pattern.getSparqlWherePattern());

        if (pattern.getSparqlPatternPrevalence().isPresent()) {
            resource.addProperty(RDFUNITv.sparqlPrevalencePattern, pattern.getSparqlPatternPrevalence().get());
        }

        for (PatternParameter patternParameter: pattern.getParameters()) {
            Resource parameter = PatternParameterWriter.createPatternParameterWriter(patternParameter).write(model);
            resource.addProperty(RDFUNITv.parameter, parameter);
        }

        for (ResultAnnotation resultAnnotation: pattern.getResultAnnotations()) {
            Resource annotationResource = ResultAnnotationWriter.createResultAnnotationWriter(resultAnnotation).write(model);
            resource.addProperty(RDFUNITv.resultAnnotation, annotationResource);
        }

        return resource;
    }
}
