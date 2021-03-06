package org.aksw.rdfunit.model.writers;

import org.aksw.rdfunit.model.impl.PatternBasedTestCaseImpl;
import org.aksw.rdfunit.model.interfaces.Binding;
import org.aksw.rdfunit.vocabulary.RDFUNITv;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * Description
 *
 * @author Dimitris Kontokostas
 * @since 6/17/15 5:57 PM

 */
final class PatternBasedTestCaseWriter implements ElementWriter {

    private final PatternBasedTestCaseImpl patternBasedTestCase;

    private PatternBasedTestCaseWriter(PatternBasedTestCaseImpl patternBasedTestCase) {
        this.patternBasedTestCase = patternBasedTestCase;
    }

    public static PatternBasedTestCaseWriter create(PatternBasedTestCaseImpl patternBasedTestCase) {return new PatternBasedTestCaseWriter(patternBasedTestCase);}


    @Override
    public Resource write(Model model) {
        Resource resource = ElementWriter.copyElementResourceInModel(patternBasedTestCase, model);

        resource
                //.addProperty(RDFS.comment, "FOR DEBUGGING ONLY: SPARQL Query: \n" + new QueryGenerationSelectFactory().getSparqlQueryAsString(this) + "\n Prevalence SPARQL Query :\n" + getSparqlPrevalence());
                .addProperty(RDF.type, RDFUNITv.PatternBasedTestCase)
                .addProperty(RDFUNITv.basedOnPattern,  ElementWriter.copyElementResourceInModel(patternBasedTestCase.getPattern(), model));

        for (Binding binding : patternBasedTestCase.getBindings()) {
            Resource bindingResource = BindingWriter.create(binding).write(model);
            resource.addProperty(RDFUNITv.binding, bindingResource);
        }

        TestAnnotationWriter.create(patternBasedTestCase.getTestCaseAnnotation()).write(model);


        return resource;
    }
}
