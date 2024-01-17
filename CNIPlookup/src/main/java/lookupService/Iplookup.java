package lookupService;

import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.beans.JavaBean;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.ExecutionException;

public class Iplookup implements HttpFunction {

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {

        String projectID = "cn2122-t1-g03";
        String zone = request.getFirstQueryParameter("zone").orElse("europe-southwest1-a");

        BufferedWriter writer = response.getWriter();

        try (InstancesClient client = InstancesClient.create()) {
            for (Instance instance : client.list(projectID, zone).iterateAll()) {
                if ((instance.getStatus().compareTo("RUNNING") == 0) && instance.getName().contains("instance-group-servers")) {

                    String ip = instance.getNetworkInterfaces(0).getAccessConfigs(0).getNatIP();

                    String json = "{'IpAddress': '" + ip + "'}";
                    writer.write(json + "\n");

                }

            }
        }

    }

}
