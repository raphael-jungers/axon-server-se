package io.axoniq.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import io.axoniq.cli.json.RestResponse;
import org.apache.commons.cli.CommandLine;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Author: marc
 */
public class InitCluster extends AxonIQCliCommand {
    public static void run(String[] args) throws IOException {
        // check args

        CommandLine commandLine = processCommandLine( args[0], args);

        String url = createUrl(commandLine, "/v1/context/init");

        try (CloseableHttpClient httpclient  = createClient(commandLine)) {
            getJSON(httpclient, url, RestResponse.class, 200, null);
        }
    }



}