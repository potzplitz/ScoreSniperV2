package scores;

import java.util.AbstractMap.SimpleEntry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONArray;
import org.json.JSONObject;

import database.Database;
import network.ApiRequester;
import network.ApiRequester.Method;

public class GetScores {
	
	private final Database db;
	private final ConcurrentLinkedQueue<SimpleEntry<String, Integer>> queue;
	private ConcurrentLinkedQueue<SimpleEntry<SimpleEntry<String, Integer>, SimpleEntry<String, Integer>>> mutualQueue;
	
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
		while(true) {

			if(mutualQueue.peek() == null ) {
				return;
			}
			
			var entry = mutualQueue.poll();
			
			String player1 = entry.getKey().getKey();
			String player2 = entry.getValue().getKey();
			
			int offset1 = entry.getKey().getValue();
			int offset2 = entry.getValue().getValue();

			String mutualMapsQ = "SELECT a.map_id FROM UserMostPlayed a WHERE a.user_id = :player_1 AND a.map_id IN ( SELECT b.map_id FROM UserMostPlayed b WHERE b.user_id = :player_2 )";
		    Map<String, Object> mutalMapsB = new HashMap<>();
		    mutalMapsB.put("player_1", Long.parseLong(player1));
		    mutalMapsB.put("player_2", Long.parseLong(player2));
		    
		    List<Map<String, Object>> results = db.query2(mutualMapsQ, mutalMapsB);
		    if (results.isEmpty()) {
		        System.out.println("No mutual maps found for " + player1 + " and " + player2);
		        continue;
		    }
		    
			String map_id = results.get(0).get("map_id").toString();
			
			String response1 = requestMap(player1, map_id);
			String response2 = requestMap(player2, map_id);
			
			if (response1 == null || response2 == null) {
				System.out.println("no maps"); 
				continue;
			}

			JSONArray parsed1 = new JSONArray(response1);
			JSONArray parsed2 = new JSONArray(response2);

			if (parsed1.isEmpty() || parsed2.isEmpty()) {
			    System.out.println("One of the players has no score on map " + map_id);
			    continue;
			}

			JSONObject score1 = parsed1.getJSONObject(0);
			JSONObject score2 = parsed2.getJSONObject(0);


			Map<String, Object> insertScoresB1 = new HashMap<>();
			insertScoresB1.put("score_id", score1.getString("score_id"));
			insertScoresB1.put("score", score1.getInt("score"));
			insertScoresB1.put("maxcombo", score1.getInt("maxcombo"));
			insertScoresB1.put("user_id", score1.getInt("user_id"));
			insertScoresB1.put("perfect", score1.getInt("perfect"));
			insertScoresB1.put("date", score1.getString("date"));
			insertScoresB1.put("rank", score1.getString("rank"));
			insertScoresB1.put("enabled_mods", score1.getInt("enabled_mods"));
			insertScoresB1.put("map_id", Integer.parseInt(map_id));

			Map<String, Object> insertScoresB2 = new HashMap<>();
			insertScoresB2.put("score_id", score2.getString("score_id"));
			insertScoresB2.put("score", score2.getInt("score"));
			insertScoresB2.put("maxcombo", score2.getInt("maxcombo"));
			insertScoresB2.put("user_id", score2.getInt("user_id"));
			insertScoresB2.put("perfect", score2.getInt("perfect"));
			insertScoresB2.put("date", score2.getString("date"));
			insertScoresB2.put("rank", score2.getString("rank"));
			insertScoresB2.put("enabled_mods", score2.getInt("enabled_mods"));
			insertScoresB2.put("map_id", Integer.parseInt(map_id));
			
			String insertScoresQ = "INSERT INTO UserScores (score_id, score, maxcombo, user_id, perfect, date, rank, enabled_mods, map_id) VALUES (:score_id, :score, :maxcombo, :user_id, :perfect, :date, :rank, :enabled_mods, :map_id) ON DUPLICATE KEY UPDATE score = VALUES(score), maxcombo = VALUES(maxcombo), user_id = VALUES(user_id), perfect = VALUES(perfect), date = VALUES(date), rank = VALUES(rank), enabled_mods = VALUES(enabled_mods), map_id = VALUES(map_id)";
			
			db.query2(insertScoresQ, insertScoresB1);
			db.query2(insertScoresQ, insertScoresB2);
			
	    	mutualQueue.add(
    		    new SimpleEntry<>(
    		        new SimpleEntry<>(player1, offset1 + 1),
    		        new SimpleEntry<>(player2, offset2 + 1)
    		    )
    		);
		}
	}
	
	private String requestMap(String player, String map_id) {
		ApiRequester api = new ApiRequester();
		api.setRequestMethod(Method.GET);
		api.setEndpoint("https://osu.ppy.sh/api/get_scores?k=" + ApiKey + "&b=" + map_id + "&u=" + player);
		
		try {
			return api.request();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
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
	    	
	    	mutualQueue.add(
    		    new SimpleEntry<>(
    		        new SimpleEntry<>(player1, 0),
    		        new SimpleEntry<>(player2, 0)
    		    )
    		);

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
		return this.status.getOrDefault(user, "unknown");
	}
	
	public static void setApiKey(String key) {
		ApiKey = key;
	}
}
