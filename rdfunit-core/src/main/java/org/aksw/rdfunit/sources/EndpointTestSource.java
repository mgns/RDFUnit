package org.aksw.rdfunit.sources;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.apache.http.Header;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.sparql.core.DatasetDescription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Describes a SPARQL Endpoint source
 *
 * @author Dimitris Kontokostas

 */
public class EndpointTestSource extends AbstractTestSource implements TestSource {

    private final String sparqlEndpoint;
    private final Collection<String> sparqlGraph;
    private final String username;
    private final String password;
    private final Collection<Header> headers;

    EndpointTestSource(SourceConfig sourceConfig, QueryingConfig queryingConfig, Collection<SchemaSource> referenceSchemata, String sparqlEndpoint, Collection<String> sparqlGraph) {
        this(sourceConfig, queryingConfig, referenceSchemata, sparqlEndpoint, sparqlGraph, "", "");
    }

    EndpointTestSource(SourceConfig sourceConfig, QueryingConfig queryingConfig, Collection<SchemaSource> referenceSchemata, String sparqlEndpoint, Collection<String> sparqlGraph, String username, String password) {
        this(sourceConfig, queryingConfig, referenceSchemata, sparqlEndpoint, sparqlGraph, username, password, new ArrayList<Header>());
    }

    EndpointTestSource(SourceConfig sourceConfig, QueryingConfig queryingConfig, Collection<SchemaSource> referenceSchemata, String sparqlEndpoint, Collection<String> sparqlGraph, String username, String password, Collection<Header> headers) {
        super(sourceConfig, queryingConfig, referenceSchemata);
        this.sparqlEndpoint = checkNotNull(sparqlEndpoint);
        this.sparqlGraph = Collections.unmodifiableCollection(checkNotNull(sparqlGraph));
        this.username = checkNotNull(username);
        this.password = checkNotNull(password);
        this.headers = Collections.unmodifiableCollection(checkNotNull(headers));
    }

    EndpointTestSource(EndpointTestSource endpointTestSource, Collection<SchemaSource> referenceSchemata) {
        this(endpointTestSource.sourceConfig, endpointTestSource.queryingConfig, referenceSchemata, endpointTestSource.sparqlEndpoint, endpointTestSource.sparqlGraph, endpointTestSource.username, endpointTestSource.password, endpointTestSource.headers);
    }


    @Override
    protected QueryExecutionFactory initQueryFactory() {

        QueryExecutionFactory qef;
        // if empty
        if (username.isEmpty() && password.isEmpty()) {
//            qef = new QueryExecutionFactoryHttp(getSparqlEndpoint(), getSparqlGraphs());
            DatasetDescription dd = new DatasetDescription(new ArrayList<>(getSparqlGraphs()), Collections.emptyList());

            HttpClient client = HttpClientBuilder.create()
                .setDefaultHeaders(getHeaders())
                .build();
            qef = new QueryExecutionFactoryHttp(getSparqlEndpoint(), dd, client);
        } else {
            DatasetDescription dd = new DatasetDescription(new ArrayList<>(getSparqlGraphs()), Collections.emptyList());
            CredentialsProvider provider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials
                    = new UsernamePasswordCredentials(username, password);
            provider.setCredentials(AuthScope.ANY, credentials);

            HttpClient client = HttpClientBuilder.create()
                    .setDefaultCredentialsProvider(provider)
                    .build();
            qef = new QueryExecutionFactoryHttp(getSparqlEndpoint(), dd, client);
        }

        return masqueradeQEF(qef, this);
    }

    public String getSparqlEndpoint() {
        return sparqlEndpoint;
    }

    public Collection<String> getSparqlGraphs() {
        return sparqlGraph;
    }

    public Collection<Header> getHeaders() {
        return headers;
    }

}
