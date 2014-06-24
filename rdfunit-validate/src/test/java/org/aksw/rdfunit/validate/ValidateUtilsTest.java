package org.aksw.rdfunit.validate;

import org.aksw.rdfunit.RDFUnitConfiguration;
import org.aksw.rdfunit.services.SchemaService;
import org.aksw.rdfunit.sources.EndpointSource;
import org.aksw.rdfunit.sources.DumpSource;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.*;

public class ValidateUtilsTest {

    @org.junit.Test
    public void testGetConfigurationFromArguments() throws Exception {
        Options cliOptions = ValidateUtils.getCliOptions();

        String args = "";
        RDFUnitConfiguration configuration = null;
        CommandLine commandLine = null;
        CommandLineParser cliParser = new GnuParser();

        // Set two dummy schemas for testing
        SchemaService.addSchemaDecl("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        SchemaService.addSchemaDecl("owl", "http://www.w3.org/2002/07/owl#");

        args = " -d http://dbpedia.org -e http://dbpedia.org/sparql -g http://dbpedia.org -s rdfs,owl -p dbo";
        commandLine = cliParser.parse(cliOptions, args.split(" "));
        configuration = ValidateUtils.getConfigurationFromArguments(commandLine);

        assertEquals(configuration.getDatasetURI(), "http://dbpedia.org");
        assertEquals(configuration.getPrefix(), "dbpedia.org");
        assertEquals(configuration.getEndpointURI(), "http://dbpedia.org/sparql");
        assertNull(configuration.getCustomDereferenceURI());
        assertEquals(configuration.getEndpointGraphs(), Arrays.asList("http://dbpedia.org"));
        assertEquals(configuration.getAllSchemata().size(), 3); //2 schema + 1 enriched
        assertNotNull(configuration.getEnrichedSchema());
        assertEquals(configuration.getDataFolder(), "../data/");
        assertEquals(configuration.getTestFolder(), "../data/tests/");
        assertEquals(configuration.getOutputFormats().size(), 1); // html by default
        assertTrue(configuration.getTestSource() instanceof EndpointSource);


        args = " -d http://dbpedia.org -u http://custom.dbpedia.org -s rdfs -f /home/rdfunit/ -M -o html,turtle";
        commandLine = cliParser.parse(cliOptions, args.split(" "));
        configuration = ValidateUtils.getConfigurationFromArguments(commandLine);

        assertEquals(configuration.getDatasetURI(), "http://dbpedia.org");
        assertEquals(configuration.getCustomDereferenceURI(), "http://custom.dbpedia.org");
        assertEquals(configuration.getAllSchemata().size(), 1);
        assertNull(configuration.getEnrichedSchema());
        assertEquals(configuration.getOutputFormats().size(), 2); // html,turtle
        assertEquals(configuration.getDataFolder(), "/home/rdfunit/");
        assertEquals(configuration.getTestFolder(), "/home/rdfunit/tests/");
        assertEquals(configuration.isManualTestsEnabled(), false);
        assertEquals(configuration.isTestCacheEnabled(), true);
        assertEquals(configuration.isCalculateCoverageEnabled(), false);
        assertTrue(configuration.getTestSource() instanceof DumpSource);


        args = " -d http://dbpedia.org -s rdfs -f /home/rdfunit/ -C -c";
        commandLine = cliParser.parse(cliOptions, args.split(" "));
        configuration = ValidateUtils.getConfigurationFromArguments(commandLine);

        assertEquals(configuration.isManualTestsEnabled(), true);
        assertEquals(configuration.isTestCacheEnabled(), false);
        assertEquals(configuration.isCalculateCoverageEnabled(), true);

        // Expect exception for missing -d
        HashMap<String, String> exceptionsExpected = new HashMap<>();
        exceptionsExpected.put(
                " -s rdf ",
                "Expected exception for missing -d");
        exceptionsExpected.put(
                " -d http://dbpedia.org ",
                "Expected exception for missing -s");
        exceptionsExpected.put(
                " -d http://dbpedia.org -e http://dbpedia.org/sparql -u http://custom.dbpedia.org ",
                "Expected exception for defining both -e & -u");
        exceptionsExpected.put(
                " -d http://dbpedia.org -e http://dbpedia.org/sparql -s rdf -l log",
                "Expected exception for asking unusupported -l");
        exceptionsExpected.put(
                " -d http://dbpedia.org -s foaf ",
                "Expected exception for asking for undefined 'foaf' schema ");
        exceptionsExpected.put(
                " -d http://dbpedia.org -s rdf -o htmln",
                "Expected exception for asking for undefined serialization 'htmln'");
        exceptionsExpected.put(
                " -d http://dbpedia.org -s rdf -o html,turtle123",
                "Expected exception for asking for undefined serialization 'turtle123'");

        for (String arg : exceptionsExpected.keySet()) {

            try {
                commandLine = cliParser.parse(cliOptions, arg.split(" "));
                configuration = ValidateUtils.getConfigurationFromArguments(commandLine);
                fail(exceptionsExpected.get(arg));
            } catch (ParameterException e) {
            }
        }


    }
}