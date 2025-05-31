package endpoint;

import com.sun.net.httpserver.HttpServer;

import database.Database;
import scores.GetScores;

import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class HTTPEndpoint {
	
	private HttpServer server;
	private final GetScores scores;
	
	public HTTPEndpoint(GetScores sharedScores) throws IOException {
		server = HttpServer.create(new InetSocketAddress(7727), 0);
		scores = sharedScores;
	}
	
	public void listen() throws IOException {
		endpoints();
		server.setExecutor(null);
		server.start();
	}

	private void endpoints() throws IOException {
		Map<String, String> routes = Map.of(
		    "/snipe", "snipe"
		);

		for (Map.Entry<String, String> route : routes.entrySet()) {
		    server.createContext(route.getKey(), textHandler(route.getValue()));
		}

	}
	
	private Map<String, Object> scoreresponse = new HashMap<>();
	
	private HttpHandler textHandler(String responseText) {
		
	    return exchange -> {
	        String query = exchange.getRequestURI().getQuery();
	        Map<String, String> queryParams = parseQuery(query);
	        
			System.out.println("accepted connection from IP " + exchange.getRemoteAddress() + " with endpoint /" + responseText);
	        
	        if(responseText.equals("snipe")) {
		        String player1 = queryParams.getOrDefault("player1", "not provided");
		        String player2 = queryParams.getOrDefault("player2", "not provided");
		        
		        scoreresponse = scores.getScores(player1, player2);
	        }

	        String finalResponse = scoreresponse.get("response").toString();
	        
	        exchange.getResponseHeaders().set("Content-Type", "application/json");
	        exchange.sendResponseHeaders(200, finalResponse.getBytes().length);
	        
	        try (OutputStream os = exchange.getResponseBody()) {
	            os.write(finalResponse.getBytes());
	        }
	    };
	}

	
	private Map<String, String> parseQuery(String query) {
	    if (query == null || query.isEmpty()) return Map.of();

	    return java.util.Arrays.stream(query.split("&"))
	        .map(kv -> kv.split("=", 2))
	        .collect(
            java.util.stream.Collectors.toMap(
                kv -> decodeURIComponent(kv[0]),
                kv -> kv.length > 1 ? decodeURIComponent(kv[1]) : ""
            )
        );
	}


	private String decodeURIComponent(String s) {
	    try {
	        return java.net.URLDecoder.decode(s, "UTF-8");
	    } catch (Exception e) {
	        return s;
	    }
	}
}
