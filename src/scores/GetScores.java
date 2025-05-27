package scores;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import database.Database;
import network.ApiRequester;
import network.ApiRequester.Method;

public class GetScores {
	
	private final Database db;
	private final ConcurrentLinkedQueue<SimpleEntry<String, Integer>> queue;
	private final ConcurrentLinkedQueue<SimpleEntry<String, Integer>> mutualQueue;
	
	private Map<String, String> status = new HashMap<String, String>();
	
	private volatile boolean isProcessing = false;
	private volatile boolean isProcessingMutual = false;
	
	private static String ApiKey;
	
	public GetScores() {
		db = new Database();
		db.setDatabase("ScoreSniper");
		db.connect();
		
		queue = new ConcurrentLinkedQueue<>();
		mutualQueue = new ConcurrentLinkedQueue<>();
	}
	
	private void getUserMostPlayed() {
		while(true) {
			
			if(queue.peek() == null ) {
				return;
			}
			
			SimpleEntry<String, Integer> user = queue.poll();
			String QueueUser = user.getKey();
			int UserOffset = user.getValue();
			
			System.out.println("Fetching maps for user " + QueueUser + " with offset " + UserOffset + "...");
			
			String endpoint = "https://osu.ppy.sh/users/" + QueueUser + "/beatmapsets/most_played?limit=100&offset=" + UserOffset;
			
			ApiRequester api = new ApiRequester();
			api.setRequestMethod(Method.GET);
			api.setEndpoint(endpoint);
			
			String apiresponse = "";
			
			try {
				apiresponse = api.request();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			JsonNode root = null;
			
	        ObjectMapper mapper = new ObjectMapper();
	        try {
	        	root = mapper.readTree(apiresponse);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
	        
	        if (root == null || !root.isArray() || root.size() == 0) {
	        	System.out.println("Finished fetching all played maps from Player " + QueueUser);
	        	setStatus("finished_maps", QueueUser);
	        	
	        	continue;
	        } else {
		    	queue.add(new SimpleEntry<>(QueueUser, UserOffset + 100));
	        }
	        
			
			String insertScoreQ = "INSERT into UserMostPlayed (user_id, map_id) values (:user_id, :map_id)";
			for (JsonNode node : root) {
			    Map<String, Object> insertScoreB = new HashMap<>();
			    insertScoreB.put("map_id", node.get("beatmap_id").asInt());
			    insertScoreB.put("user_id", QueueUser);

			    db.query2(insertScoreQ, insertScoreB);
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();	
			}
		}
	}
	
	private void fetchMutualMaps() {
		//TODO
	}
	
	public void checkForMostPlayedJob() throws InterruptedException {
		while (true) {
			if (!queue.isEmpty() && !isProcessing) {
				isProcessing = true;
				
				Thread thread = new Thread(() -> {
					try {
						getUserMostPlayed();
					} finally {
						isProcessing = false;
					}
				});
				thread.start();
				
				if(!mutualQueue.isEmpty() && !isProcessingMutual) {
					Thread thread2 = new Thread(() -> {
						try {
							fetchMutualMaps();
						} finally {
							isProcessingMutual = false;
						}
					});
					thread2.start();
				}
			}
			
			Thread.sleep(1000);
		}
	}
	
	public Map<String, Object> getScores(String player1, String player2) throws JsonProcessingException {
	    String queryExist = "SELECT count(1) as count FROM UserScores WHERE ((user_id = :player1) OR (user_id = :player2))";

	    Map<String, Object> bindsExist = new HashMap<>();
	    bindsExist.put("player1", player1);
	    bindsExist.put("player2", player2);
	    
	    Map<String, Object> result = new HashMap<>();
	    
	    if (Integer.parseInt(db.query2(queryExist, bindsExist).get(0).get("count").toString()) > 0) {
	    	result.put("response", getRandomMutualMap(player1, player2));
	    	
	    } else {
	    	queue.add(new SimpleEntry<>(player1, 0));
	    	queue.add(new SimpleEntry<>(player2, 0));
	    	
	    	System.out.println(queue);
	    	
	    	result.put("response", "Added user(s) to queue");
	    }

	    return result;
	}
	
	private String getRandomMutualMap(String player1, String player2) throws JsonProcessingException {
		
		System.out.println("Requested map for " + player1 + " and " + player2);
		
		String randomSql = "SELECT a.map_id, a.user_id AS user_player, a.score AS score_player, a.maxcombo AS maxcombo_player, a.perfect AS perfect_player, a.date AS date_player, a.rank AS rank_player, a.enabled_mods AS mods_player, b.user_id AS user_target, b.score AS score_target, b.maxcombo AS maxcombo_target, b.perfect AS perfect_target, b.date AS date_target, b.rank AS rank_target, b.enabled_mods AS mods_target FROM UserScores a JOIN UserScores b ON a.map_id = b.map_id WHERE a.user_id = :player2 AND b.user_id = :player1 AND a.score < b.score order by rand() limit 1";
		
		Map<String, Object> randomBinds = new HashMap<>();
		randomBinds.put("player1", player1);
		randomBinds.put("player2", player2);
		
		List<Map<String, Object>> results = db.query2(randomSql, randomBinds);

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(results);
	}
	
	public void setStatus(String status, String user) {
		this.status.put(user, status);
	}
	
	public String getStatus(String user) {
		return this.status.get(user).toString();
	}
	
	public static void setApiKey(String key) {
		ApiKey = key;
	}
}
