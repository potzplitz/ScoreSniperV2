package network;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class ApiRequester {
	
	private String endpoint;
	private Map<String, String> parameters = new HashMap<>();
    private Method method = Method.GET;
	
    public enum Method {
        GET, POST
    }
	
	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}
	
	public void setParameters(Map<String, String> parameters) {
		this.parameters = parameters;
	}
	
	public void setRequestMethod(Method method) {	
		this.method = method;
	}

    public String request() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        String paramString = parameters.entrySet().stream()
        	    .map(e -> {
        	        try {
        	            return e.getKey() + "=" + URLEncoder.encode(e.getValue(), "UTF-8");
        	        } catch (UnsupportedEncodingException ex) {
        	            throw new RuntimeException("UTF-8 encoding not supported", ex);
        	        }
        	    })
        	    .reduce((a, b) -> a + "&" + b)
        	    .orElse("");


        HttpRequest request;

        if (method == Method.GET) {
            URI uri = URI.create(endpoint + "?" + paramString);
            request = HttpRequest.newBuilder(uri).GET().build();
        } else {
            // POST
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(paramString);
            request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(body)
                .build();
        }

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

}
