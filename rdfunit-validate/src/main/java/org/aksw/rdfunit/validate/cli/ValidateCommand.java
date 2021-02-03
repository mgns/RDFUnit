package org.aksw.rdfunit.validate.cli;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.aksw.jena_sparql_api.model.QueryExecutionFactoryModel;
import org.aksw.rdfunit.RDFUnit;
import org.aksw.rdfunit.RDFUnitConfiguration;
import org.aksw.rdfunit.coverage.TestCoverageEvaluator;
import org.aksw.rdfunit.enums.TestCaseExecutionType;
import org.aksw.rdfunit.exceptions.UndefinedSchemaException;
import org.aksw.rdfunit.exceptions.UndefinedSerializationException;
import org.aksw.rdfunit.io.IOUtils;
import org.aksw.rdfunit.io.writer.RdfMultipleWriter;
import org.aksw.rdfunit.io.writer.RdfResultsWriterFactory;
import org.aksw.rdfunit.io.writer.RdfWriter;
import org.aksw.rdfunit.io.writer.RdfWriterException;
import org.aksw.rdfunit.model.interfaces.GenericTestCase;
import org.aksw.rdfunit.model.interfaces.TestSuite;
import org.aksw.rdfunit.model.interfaces.results.TestExecution;
import org.aksw.rdfunit.model.writers.TestCaseWriter;
import org.aksw.rdfunit.model.writers.results.TestExecutionWriter;
import org.aksw.rdfunit.services.PrefixNSService;
import org.aksw.rdfunit.sources.TestSource;
import org.aksw.rdfunit.tests.executors.TestExecutor;
import org.aksw.rdfunit.tests.executors.TestExecutorFactory;
import org.aksw.rdfunit.tests.executors.monitors.SimpleTestExecutorMonitor;
import org.aksw.rdfunit.tests.generators.TestGeneratorExecutor;
import org.aksw.rdfunit.utils.RDFUnitUtils;
import org.aksw.rdfunit.validate.ParameterException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "validate",
    description = "Validate a dataset",
    mixinStandardHelpOptions = true)
public class ValidateCommand implements Callable {

  static Logger log = LoggerFactory.getLogger(CLI.class);

  @Option(names = {"-d", "--dataset-uri"},
      description = "The dataset URI (required)",
      required = true)
  String datasetURI;

  @ArgGroup(exclusive = true)
  DatasetOptions datasetOptions = new DatasetOptions();

  @Option(names = {"-s", "--schemas"},
      description = "The schemata used in the chosen graph (comma separated prefixes according to http://lov.okfn.org/): If this option is missing RDFUnit will try to guess them automatically",
      split = "\\s*,\\s*",
      splitSynopsisLabel = ",")
  Collection<String> schemaUriPrefixes = new ArrayList<String>();

  @Option(names = {"-v", "--no-LOV"},
      defaultValue = "false",
      description = "Do not use the LOV service")
  boolean noLOV;

  @Option(names = {"-i", "--imports"},
      defaultValue = "false",
      description = "If set, in addition to the schemata provided or discovered, all transitively discovered import schemata (owl:imports) are included into the schema set")
  boolean imports;

  @Option(names = {"-x", "--exclude"},
      description = "The schemata excluded from test generation (comma separated prefixes according to http://lov.okfn.org/)",
      split = "\\s*,\\s*",
      splitSynopsisLabel = ",")
  Collection<String> excluded = new ArrayList<String>();

  @Option(names = {"-o", "--output-format"},
      defaultValue = "html",
      description = "The output format of the validation results: One of `html`, `turtle`, `n3`, `ntriples`, `json-ld`, `rdf-json`, `rdfxml`, `rdfxml-abbrev`, `junitxml` (default: ${DEFAULT-VALUE})")
  String outputFormat = "";

  @Option(names = {"-p", "--enriched-prefix"},
      description = "The prefix of this dataset used for caching the schema enrichment")
  String enrichedPrefix = "";

  @Option(names = {"-C", "--no-test-cache"},
      defaultValue = "false",
      description = "Do not load cached test cases, regenerate them (cached test cases are loaded by default)")
  boolean noTestCache;

  @Option(names = {"-M", "--no-manual-tests"},
      defaultValue = "false",
      description = "Do not load any manually defined test cases (manual test cases are loaded by default)")
  boolean noManualTests;

  @Option(names = {"-A", "--no-auto-tests"},
      defaultValue = "false",
      description = "Do not load any schema / automatically generated test cases (automatically generated test cases are loaded by default)")
  boolean noAutoTests;

  @Option(names = {"-r", "--result-level"},
      defaultValue = "aggregate",
      description = "Specify the result level for the error reporting: One of `status`, `aggregate`, `rlog`, `extended` (default: ${DEFAULT-VALUE})")
  String resultLevel;

  @Option(names = {"-l", "--logging-level"},
      description = "NOT SUPPORTED at the moment! will filter test cases based on logging level (notice, warn, error, etc).")
  String loggingLevel = "";

  @Option(names = {"-c", "--test-coverage"},
      defaultValue = "false",
      description = "Calculate test-coverage scores (default: ${DEFAULT-VALUE})")
  boolean testCoverage;

  @Option(names = {"-f", "--data-folder"},
      defaultValue = "./data/",
      description = "The location of the data folder where results and caches are stored (default: ${DEFAULT-VALUE})")
  private String dataFolder;

  private static void displayHelpAndExit(String errorMessage, Exception e) {
    log.error(errorMessage, e);
    //displayHelpAndExit();
  }

  static class DatasetOptions {
    @ArgGroup(exclusive = false)
    EndpointOptions endpointOptions;

    @Option(names = {"-u", "--uri"},
        description = "The URI to use for dereferencing a dump if not the same with the dataset URI (-d)")
    String customDereferenceURI;

    static class EndpointOptions {
      @Option(names = {"-e", "--endpoint"},
          description = "The endpoint URI to run the tests on (if no endpoint or dump URI (-u) is provided RDFUnit will try to dereference the dataset URI (-d))",
          required = true)
      String endpointURI;

      @Option(names = {"-g", "--graph"},
          defaultValue = "",
          description = "The graphs to use, separate multiple graphs with ',' (default: ${DEFAULT-VALUE})",
          split = "\\s*,\\s*",
          splitSynopsisLabel = ",")
      Collection<String> endpointGraphs;

      @ArgGroup(exclusive = false)
      EndpointPerformanceOptions endpointPerformanceOptions = new EndpointPerformanceOptions();

      static class EndpointPerformanceOptions {
        @Option(names = {"-T", "--query-cache-ttl"},
            defaultValue = "10080",
            description = "The SPARQL endpoint cache Time-To-Live (TTL) (in minutes) or '0' to disable it (default: ${DEFAULT-VALUE})")
        int queryCacheTTL;

        @Option(names = {"-D", "--query-delay"},
            defaultValue = "5",
            description = "The delay between consecutive queries against an endpoint (in seconds) or '0' to disable delay (default: ${DEFAULT-VALUE})")
        long queryDelay;

        @Option(names = {"-L", "--query-limit"},
            defaultValue = "800",
            description = "The maximum results from a SPARQL test query or '0' to disable limits (default: ${DEFAULT-VALUE})")
        int queryLimit;

        @Option(names = {"-P", "--query-pagination"},
            defaultValue = "800",
            description = "The pagination page size for retrieving big results or '0' to disable it (default: ${DEFAULT-VALUE})")
        long queryPagination;
      }

      @ArgGroup(exclusive = false)
      EndpointBasicAuthOptions endpointBasicAuthOptions;

      static class EndpointBasicAuthOptions {
        @Option(names = {"-eu", "--endpoint-username"},
            description = "The username for endpoint basic authentication.")
        String username;

        @Option(names = {"-ep", "--endpoint-password"},
            description = "The password for endpoint basic authentication.")
        String password;
      }
    }
  }

  @Override
  public Object call() throws Exception {
    if (!noLOV) { // explicitly do not use LOV
      RDFUnitUtils.fillSchemaServiceFromLOV();
    }

    RDFUnitConfiguration configuration = getConfigurationFromArguments();
    checkNotNull(configuration);

    if (!IOUtils.isFile(configuration.getDataFolder())) {
      log.error("Path " + configuration.getDataFolder() + " does not exists, use -f argument");
      System.exit(1);
    }

    RDFUnit rdfunit = RDFUnit.createWithOwlAndShacl();
    try {
      rdfunit.init();
    } catch (IllegalArgumentException e) {
      displayHelpAndExit("Cannot read patterns and/or pattern generators.", e);
    }

    final TestSource dataset = configuration.getTestSource();

    TestGeneratorExecutor testGeneratorExecutor = new TestGeneratorExecutor(
        configuration.isAutoTestsEnabled(),
        configuration.isTestCacheEnabled(),
        configuration.isManualTestsEnabled());
    TestSuite testSuite = testGeneratorExecutor
        .generateTestSuite(configuration.getTestFolder(), dataset, rdfunit.getAutoGenerators());

    TestExecutor testExecutor = TestExecutorFactory
        .createTestExecutor(configuration.getTestCaseExecutionType());
    if (testExecutor == null) {
      displayHelpAndExit("Cannot initialize test executor. Exiting", null);
    }
    SimpleTestExecutorMonitor testExecutorMonitor = new SimpleTestExecutorMonitor();
    testExecutorMonitor.setExecutionType(configuration.getTestCaseExecutionType());
    checkNotNull(testExecutor);
    testExecutor.addTestExecutorMonitor(testExecutorMonitor);

    // warning, caches intermediate results
    testExecutor.execute(dataset, testSuite);
    TestExecution testExecution = testExecutorMonitor.getTestExecution();

    // Write results to RDFWriter ()
    String resultsFolder = configuration.getDataFolder() + "results/";
    String filename =
        resultsFolder + dataset.getPrefix() + "." + configuration.getTestCaseExecutionType()
            .toString();

    if (!(new File(resultsFolder).exists())) {
      log.warn("Results folder ({}) does not exist, creating it...", resultsFolder);
      File resultsFileFolder = new File(resultsFolder);
      boolean dirsCreated = resultsFileFolder.mkdirs();
      if (!dirsCreated) {
        log.error("Could not create folder {}", resultsFileFolder.getAbsolutePath());
      }
    }

    List<RdfWriter> outputWriters = configuration.getOutputFormats().stream()
        .map(serializationFormat ->
            RdfResultsWriterFactory
                .createWriterFromFormat(filename, serializationFormat, testExecution))
        .collect(Collectors.toList());

    RdfWriter resultWriter = new RdfMultipleWriter(outputWriters);
    try {
      Model model = ModelFactory.createDefaultModel();
      TestExecutionWriter.create(testExecution).write(model);

      resultWriter.write(model);
      log.info("Results stored in: " + filename + ".*");
    } catch (RdfWriterException e) {
      log.error("Cannot write tests to file", e);
    }

    // Calculate coverage
    if (configuration.isCalculateCoverageEnabled()) {
      Model testSuiteModel = ModelFactory.createDefaultModel();
      PrefixNSService.setNSPrefixesInModel(testSuiteModel);
      for (GenericTestCase ut : testSuite.getTestCases()) {
        TestCaseWriter.create(ut).write(testSuiteModel);
      }

      TestCoverageEvaluator tce = new TestCoverageEvaluator();
      tce.calculateCoverage(new QueryExecutionFactoryModel(testSuiteModel),
          configuration.getTestSource().getExecutionFactory());
    }

    return null;
  }

  private RDFUnitConfiguration getConfigurationFromArguments() throws ParameterException {

    RDFUnitConfiguration configuration = readDatasetUriAndInitConfiguration();

    setDumpOrSparqlEndpoint(configuration);
    setQueryTtlCachePaginationLimit(configuration);

    loadSchemaDecl(configuration);

    setExcludeSchemata(configuration);
    setSchemas(configuration);
    setEnrichedSchemas(configuration);

    setTestExecutionType(configuration);
    setOutputFormats(configuration);

    setTestAutogetCacheManual(configuration);

    setCoverageCalculation(configuration);

    return configuration;
  }

  private RDFUnitConfiguration readDatasetUriAndInitConfiguration() {
    RDFUnitConfiguration configuration;

    //Dataset URI, important & required (used to associate manual dataset test cases)
    if (!datasetURI.isEmpty() && datasetURI.endsWith("/")) {
      datasetURI = datasetURI.substring(0, datasetURI.length() - 1);
    }

    configuration = new RDFUnitConfiguration(datasetURI, dataFolder);
    return configuration;
  }

  private void setDumpOrSparqlEndpoint(RDFUnitConfiguration configuration) {
    // Dump location for dump dereferencing (defaults to dataset uri)
    if (datasetOptions.customDereferenceURI != null) {
      configuration.setCustomDereferenceURI(datasetOptions.customDereferenceURI);
    }

    //Endpoint initialization
    if (datasetOptions.endpointOptions != null) {
      if (datasetOptions.endpointOptions.endpointBasicAuthOptions != null) {
        configuration.setEndpointConfiguration(datasetOptions.endpointOptions.endpointURI,
            datasetOptions.endpointOptions.endpointGraphs,
            datasetOptions.endpointOptions.endpointBasicAuthOptions.username,
            datasetOptions.endpointOptions.endpointBasicAuthOptions.password);
      } else {
        configuration.setEndpointConfiguration(datasetOptions.endpointOptions.endpointURI,
            datasetOptions.endpointOptions.endpointGraphs, "", "");
      }
    }
  }

  private void loadSchemaDecl(RDFUnitConfiguration configuration) {
    try {
      File f = new File(configuration.getDataFolder() + "schemaDecl.csv");
      if (f.exists()) {
        RDFUnitUtils.fillSchemaServiceFromFile(f.getAbsolutePath());
      } else {
        RDFUnitUtils.fillSchemaServiceFromSchemaDecl();
      }
      RDFUnitUtils.fillSchemaServiceWithStandardVocabularies();
    } catch (Exception e) {
      log.warn("Loading custom scheme declarations failed.\n" +
          "Falling back to bundled declarations in classpath due to", e);
    }
  }

  private void setExcludeSchemata(RDFUnitConfiguration configuration) {
    if (!excluded.isEmpty()) {
      //Get schema list
      configuration.setExcludeSchemataFromPrefixes(excluded);
    }
  }

  private void setSchemas(RDFUnitConfiguration configuration) throws ParameterException {
    // first check if owl:imports are included
    if (imports) {
      configuration.setAugmentWithOwlImports(true);
    }
    if (!schemaUriPrefixes.isEmpty()) {
      try {
        //Get schema list
        configuration.setSchemataFromPrefixes(schemaUriPrefixes);
      } catch (UndefinedSchemaException e) {
        throw new ParameterException(e.getMessage(), e);
      }
    }
    // try to guess schemas automatically
    else {
      log.info("Searching for used schemata in dataset");
      configuration.setAutoSchemataFromQEF(configuration.getTestSource().getExecutionFactory());
    }
  }

  private void setEnrichedSchemas(RDFUnitConfiguration configuration) {
    //Get enriched schema
    configuration.setEnrichedSchema(enrichedPrefix);
  }

  private void setTestExecutionType(RDFUnitConfiguration configuration) {
    TestCaseExecutionType rl = TestCaseExecutionType.aggregatedTestCaseResult;
    if (!resultLevel.isEmpty()) {
      switch (resultLevel.toLowerCase()) {
        case "status":
          rl = TestCaseExecutionType.statusTestCaseResult;
          break;
        case "aggregated":
        case "aggregate":
          rl = TestCaseExecutionType.aggregatedTestCaseResult;
          break;
        case "shacl-lite":
        case "shacllite":
          rl = TestCaseExecutionType.shaclLiteTestCaseResult;
          break;
        case "shacl":
          rl = TestCaseExecutionType.shaclTestCaseResult;
          break;
        default:
          log.warn(
              "Option --result-level defined but not recognised. Using 'aggregate' by default.");
          break;
      }
    }
    configuration.setTestCaseExecutionType(rl);
  }

  private void setOutputFormats(RDFUnitConfiguration configuration) throws ParameterException {
    // Get output formats (with HTML as default)
    Collection<String> outputFormats = new ArrayList<String>();
    outputFormats.add(outputFormat);
    try {
      configuration.setOutputFormatTypes(outputFormats);
    } catch (UndefinedSerializationException e) {
      throw new ParameterException(e.getMessage(), e);
    }
  }

  private void setTestAutogetCacheManual(RDFUnitConfiguration configuration)
      throws ParameterException {
    // for automatically generated test cases
    configuration.setTestCacheEnabled(!noTestCache);

    //Do not use manual tests
    configuration.setManualTestsEnabled(!noManualTests);

    //Do not use automatic tests
    configuration.setAutoTestsEnabled(!noAutoTests);

    if (!configuration.isManualTestsEnabled() && !configuration.isAutoTestsEnabled()) {
      throw new ParameterException("both -M & -A does not make sense");
    }

    if (!configuration.isAutoTestsEnabled() && configuration.isTestCacheEnabled()) {
      throw new ParameterException("both -A & -C does not make sense");
    }
  }

  private void setQueryTtlCachePaginationLimit(RDFUnitConfiguration configuration)
      throws ParameterException {
    if (datasetOptions.endpointOptions == null)
      return;

    // Get query time to live cache option

    // we set the cache in minutes
    long ttl = 60L * 1000L * datasetOptions.endpointOptions.endpointPerformanceOptions.queryCacheTTL;
    configuration.setEndpointQueryCacheTTL(ttl);

    // Get query delay option
    configuration.setEndpointQueryDelayMS(datasetOptions.endpointOptions.endpointPerformanceOptions.queryDelay);

    // Get query pagination option
    configuration.setEndpointQueryPagination(datasetOptions.endpointOptions.endpointPerformanceOptions.queryPagination);

    // Get query Limit option
    configuration.setEndpointQueryLimit(datasetOptions.endpointOptions.endpointPerformanceOptions.queryLimit);
  }

  private void setCoverageCalculation(RDFUnitConfiguration configuration) {
    configuration.setCalculateCoverageEnabled(testCoverage);
  }

}