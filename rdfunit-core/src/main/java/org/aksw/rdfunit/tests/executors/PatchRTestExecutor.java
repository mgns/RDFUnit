package org.aksw.rdfunit.tests.executors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.aksw.jena_sparql_api.model.QueryExecutionFactoryModel;
import org.aksw.rdfunit.Utils.RDFUnitUtils;
import org.aksw.rdfunit.Utils.SparqlUtils;
import org.aksw.rdfunit.enums.TestCaseResultStatus;
import org.aksw.rdfunit.exceptions.TestCaseExecutionException;
import org.aksw.rdfunit.exceptions.TripleReaderException;
import org.aksw.rdfunit.io.DataFirstSuccessReader;
import org.aksw.rdfunit.io.DataReader;
import org.aksw.rdfunit.io.RDFFileReader;
import org.aksw.rdfunit.patterns.Pattern;
import org.aksw.rdfunit.services.PrefixService;
import org.aksw.rdfunit.sources.Source;
import org.aksw.rdfunit.tests.Binding;
import org.aksw.rdfunit.tests.PatternBasedTestCase;
import org.aksw.rdfunit.tests.TestCase;
import org.aksw.rdfunit.tests.results.PatchRTestCaseResult;
import org.aksw.rdfunit.tests.results.TestCaseResult;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.sparql.engine.http.QueryExceptionHTTP;

import de.hpi.patchr.PatchFactory;
import de.hpi.patchr.api.Dataset;
import de.hpi.patchr.api.Patch.UPDATE_ACTION;

/**
 * User: Magnus Knuth Executes results for PatchRTestCaseResult sets Created:
 * 14/5/14 8:00 PM
 */
public class PatchRTestExecutor extends TestExecutor {

	private QueryExecutionFactoryModel patternQueryFactory;

	@Override
	protected java.util.Collection<TestCaseResult> executeSingleTest(Source source, TestCase testCase) throws TestCaseExecutionException {

		Collection<TestCaseResult> testCaseResults = new ArrayList<TestCaseResult>();

		// TODO move to constructor
		if (this.patternQueryFactory == null) {
			DataReader patternReader_data = new RDFFileReader("../data/patchr_patterns.ttl");
			DataReader patternReader_resource = new RDFFileReader(RDFUnitUtils.class.getResourceAsStream("/org/aksw/rdfunit/patchr_patterns.ttl"));
			DataReader patternReader = new DataFirstSuccessReader(Arrays.asList(patternReader_data, patternReader_resource));
			Model patternModel = ModelFactory.createDefaultModel();
			try {
				patternReader.read(patternModel);
			} catch (TripleReaderException e) {
				System.out.println(e.getMessage());
				throw new TestCaseExecutionException(TestCaseResultStatus.Error);
			}
			patternModel.setNsPrefixes(PrefixService.getPrefixMap());
			this.patternQueryFactory = new QueryExecutionFactoryModel(patternModel);
		}

		QueryExecution qe = null;
		QueryExecution qePattern = null;
		try {
			if (testCase.getClass().equals(PatternBasedTestCase.class)) {
				PatternBasedTestCase pbTestCase = (PatternBasedTestCase) testCase;
				Pattern pattern = pbTestCase.getPattern();

		        String sparqlSelectPattern = RDFUnitUtils.getAllPrefixes() +
		                " SELECT DISTINCT ?pattern ?id WHERE { " +
		                " ?pattern a patrut:Pattern ; " +
		                "  dcterms:identifier \"" + pattern.getId() + "\" ; " +
		                " } ";

		        qePattern = this.patternQueryFactory.createQueryExecution(sparqlSelectPattern);
				ResultSet resultsPattern = qePattern.execSelect();

				if (resultsPattern.hasNext()) {
					// will not work on where queries using GROUP BY
					Query query = QueryFactory.create(RDFUnitUtils.getAllPrefixes() + " SELECT DISTINCT * WHERE " + testCase.getSparqlWhere() + " LIMIT 1000");
					
					qe = source.getExecutionFactory().createQueryExecution(query);
					ResultSet results = qe.execSelect();
		
					while (results.hasNext()) {
						testCaseResults.add(generateSingleResult(results.next(), testCase));
		
					}
				} else {
					System.out.println(pattern.getId() + " skipped: No Patchr Pattern.");
				}
			}
		} catch (QueryExceptionHTTP e) {
			if (SparqlUtils.checkStatusForTimeout(e))
				throw new TestCaseExecutionException(TestCaseResultStatus.Timeout);
			else
				throw new TestCaseExecutionException(TestCaseResultStatus.Error);
		} catch (Exception e) {
			e.printStackTrace();
			throw new TestCaseExecutionException(TestCaseResultStatus.Error);
		} finally {
			if (qe != null)
				qe.close();
			if (qePattern != null)
				qePattern.close();
		}

		return testCaseResults;
	}

	protected TestCaseResult generateSingleResult(QuerySolution qs, TestCase testCase) throws TestCaseExecutionException {
		PatchFactory patchFactory = new PatchFactory("http://purl.org/hpi/patchr/data/", testCase.getTestURI(), testCase.getTestURI(), new Dataset("http://dbpedia.org", "http://dbpedia.org/sparql", "http://dbpedia.org"));

		PatternBasedTestCase pbTestCase = (PatternBasedTestCase) testCase;
		Pattern pattern = pbTestCase.getPattern();
		System.out.println(pattern.getId());

        String sparqlSelectPattern = RDFUnitUtils.getAllPrefixes() +
                " SELECT DISTINCT ?pattern ?id WHERE { " +
                " ?pattern a patrut:Pattern ; " +
                "  dcterms:identifier \"" + pattern.getId() + "\" ; " +
                " } ";

        String sparqlSelectActions = RDFUnitUtils.getAllPrefixes() +
                " SELECT DISTINCT ?updatePattern ?action ?id ?targetSubject ?targetSubjectType ?targetSubjectId ?targetPredicate ?targetPredicateType ?targetPredicateId ?targetObject ?targetObjectType ?targetObjectId ?confidence WHERE { " +
                " %%PATTERN%% a patrut:Pattern ; " +
                "   patrut:delete | patrut:insert ?updatePattern . " +
                " ?updatePattern dcterms:identifier ?id ; " +
                "   patrut:action ?action ; " +
                "   patrut:targetSubject ?targetSubject ; " +
                "   patrut:targetPredicate ?targetPredicate ; " +
                "   patrut:targetObject ?targetObject ; " +
                "   patrut:confidence ?confidence . " +
                " OPTIONAL { ?targetSubject a ?targetSubjectType . } " +
                " OPTIONAL { ?targetSubject dcterms:identifier ?targetSubjectId . } " +
                " OPTIONAL { ?targetPredicate a ?targetPredicateType . } " +
                " OPTIONAL { ?targetPredicate dcterms:identifier ?targetPredicateId . } " +
                " OPTIONAL { ?targetObject a ?targetObjectType . } " +
                " OPTIONAL { ?targetObject dcterms:identifier ?targetObjectId . } " +
                " } ";

		QueryExecution qePattern = null;
		QueryExecution qeActions = null;
		
		try {
			qePattern = this.patternQueryFactory.createQueryExecution(sparqlSelectPattern);
			ResultSet resultsPattern = qePattern.execSelect();
	
			if (resultsPattern.hasNext()) {
				QuerySolution qsPattern = resultsPattern.next();
				String patternURI = qsPattern.get("pattern").toString();
				System.out.println(patternURI);
	
				qeActions = this.patternQueryFactory.createQueryExecution(sparqlSelectActions.replace("%%PATTERN%%", "<" + patternURI + ">"));
				ResultSet resultsActions = qeActions.execSelect();
				
				while (resultsActions.hasNext()) {
					QuerySolution qsActions = resultsActions.next();
					String updatePattern = qsActions.get("updatePattern").toString();
		            String action = qsActions.get("action").toString();
		            String id = qsActions.get("id").toString();
		            String targetSubject = qsActions.get("targetSubject").toString();
		            String targetSubjectType = qsActions.contains("targetSubjectType")?qsActions.get("targetSubjectType").toString():"";
		            String targetSubjectId = qsActions.contains("targetSubjectId")?qsActions.get("targetSubjectId").toString():"";
		            String targetPredicate = qsActions.get("targetPredicate").toString();
		            String targetPredicateType = qsActions.contains("targetPredicateType")?qsActions.get("targetPredicateType").toString():"";
		            String targetPredicateId = qsActions.contains("targetPredicateId")?qsActions.get("targetPredicateId").toString():"";
		            String targetObject = qsActions.get("targetObject").toString();
		            String targetObjectType = qsActions.contains("targetObjectType")?qsActions.get("targetObjectType").toString():"";
		            String targetObjectId = qsActions.contains("targetObjectId")?qsActions.get("targetObjectId").toString():"";
		            double confidence = qsActions.getLiteral("confidence").getDouble();
		            
		            Resource subject = null;
		            Property property = null;
		            RDFNode object = null;
		            
					Collection<Binding> bindings = pbTestCase.getBindings();
	
		            if (targetSubjectType.equals("http://purl.org/hpi/patchr/ns/rdfunit-core#QueryBindingParameter")) {
		            	subject = qs.get(targetSubjectId).asResource();
		            } else if (targetSubjectType.equals("http://rdfunit.aksw.org/ns/core#Parameter")) {
		            	for (Binding binding : bindings)
							if (binding.getParameterId().equals(targetSubjectId)) {
								String value = binding.getValue().replaceAll("[<>]", "");
								if (value.startsWith("?")) {
									// variable bound as parameter
									subject = qs.get(value.replace("?", "")).asResource();
								} else {
									subject = patchFactory.getModel().createResource(value);
								}
							}
		            }
		            
		            if (targetPredicateType.equals("http://purl.org/hpi/patchr/ns/rdfunit-core#QueryBindingParameter")) {
		            	property = patchFactory.getModel().createProperty(qs.get(targetPredicateId).toString());
		            } else if (targetPredicateType.equals("http://rdfunit.aksw.org/ns/core#Parameter")) {
		            	for (Binding binding : bindings)
							if (binding.getParameterId().equals(targetPredicateId)) {
								String value = binding.getValue().replaceAll("[<>]", "");
								if (value.startsWith("?")) {
									// variable bound as parameter
									property = patchFactory.getModel().createProperty(qs.get(value.replace("?", "")).toString());
								} else {
									property = patchFactory.getModel().createProperty(value);
								}
							}
		            } else if (targetPredicateType.equals("http://purl.org/hpi/patchr/ns/rdfunit-core#FixedParameter")) {
		            	property = patchFactory.getModel().createProperty(targetPredicateId);
		            }
	
		            if (targetObjectType.equals("http://purl.org/hpi/patchr/ns/rdfunit-core#QueryBindingParameter")) {
		            	object = qs.get(targetObjectId);
		            } else if (targetObjectType.equals("http://rdfunit.aksw.org/ns/core#Parameter")) {
		            	for (Binding binding : bindings)
							if (binding.getParameterId().equals(targetObjectId)) {
								String value = binding.getValue().replaceAll("[<>]", "");
								if (value.startsWith("?")) {
									// variable bound as parameter
									object = qs.get(value.replace("?", ""));
								} else {
									object = patchFactory.getModel().createResource(value);
								}
							}
		            }
		            
		            switch (action) {
		            	case ("http://purl.org/hpi/patchr/ns/core#Delete"): 
		            		System.out.println("Delete " + subject.toString() + " " + property.toString() + " " + object.toString() + ".");
		            		patchFactory.addPatchRequest(UPDATE_ACTION.delete, subject, property, object, confidence);
		            		break;
		            	case ("http://purl.org/hpi/patchr/ns/core#Insert"):
		            		System.out.println("Insert " + subject.toString() + " " + property.toString() + " " + object.toString() + ".");
		            		patchFactory.addPatchRequest(UPDATE_ACTION.insert, subject, property, object, confidence);
		            		break;
	            		default:
	            			System.out.println("Nothing.");
		            }
				}	
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new TestCaseExecutionException(TestCaseResultStatus.Error);
		} finally {
			if (qePattern != null)
				qePattern.close();
			if (qeActions != null)
				qeActions.close();
		}
		return new PatchRTestCaseResult(testCase, patchFactory);
	}

}
