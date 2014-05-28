package org.aksw.rdfunit.tests.results;

import org.aksw.rdfunit.tests.TestCase;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;

import de.hpi.patchr.PatchFactory;

public class PatchRTestCaseResult extends TestCaseResult {

	private PatchFactory patchFactory;
	
	public PatchRTestCaseResult(TestCase testCase, PatchFactory patchFactory) {
        super(testCase);
        
        this.patchFactory = patchFactory;
	}

	@Override
	public Resource serialize(Model model, String sourceURI) {
		model.add(this.patchFactory.getModel());
		
		return null;
	}

}
