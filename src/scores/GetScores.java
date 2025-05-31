package scores;

import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import database.Database;
import network.ApiRequester;
import network.ApiRequester.Method;

public class GetScores {
	
	private Database db;
	private ConcurrentLinkedQueue<SimpleEntry<SimpleEntry<String, Integer>, SimpleEntry<String, Integer>>> mostPlayedQueue;
	private ConcurrentLinkedQueue<SimpleEntry<SimpleEntry<String, Integer>, SimpleEntry<String, Integer>>> mutualQueue;
	
	private final Map<String, Boolean> finishedUsers = new HashMap<>();
	private final Map<String, Boolean> mostPlayedProcessing = new ConcurrentHashMap<>();
	
	private Map<String, String> status = new HashMap<>();
	
	private volatile boolean isProcessing = false;
	
	private static String ApiKey = "";
	
	public GetScores() {
	    db = new Database();
	    db.setDatabase("ScoreSniper");
	    db.connect();
	    
	    mostPlayedQueue = new ConcurrentLinkedQueue<>();
	    mutualQueue = new ConcurrentLinkedQueue<>();

	    new Thread(() -> {
	        while (true) {
	            if (!mutualQueue.isEmpty()) {
	                try {
	                    getMutualScores();
	                } catch (Exception e) {
	                    e.printStackTrace();
	                }
	            }
	            try {
	                Thread.sleep(500);
	            } catch (InterruptedException e) {
	                e.printStackTrace();
	            }
	        }
	    }).start();
	}
	
	private String getPairKey(String player1, String player2) {
	    return player1.compareTo(player2) < 0 ? player1 + ":" + player2 : player2 + ":" + player1;
	}
	
	public static void setApiKey(String key) {
		ApiKey = key;
	}
	
	public Map<String, Object> getScores(String player1, String player2) throws JsonProcessingException {
	    String queryExist = "SELECT (select count(1) from UserMostPlayed where user_id = :player1) as exist_1, (select count(1) from UserMostPlayed where user_id = :player2) as exist_2";

	    db.bindValue("player1", player1);
	    db.bindValue("player2", player2);
	    db.query2(queryExist);

	    System.out.println(mostPlayedQueue);

	    Map<String, Object> result = new HashMap<>();
	    String pairKey = getPairKey(player1, player2);

	    if (Integer.parseInt(db.result().get(0).get("exist_1").toString()) > 0 &&
	        Integer.parseInt(db.result().get(0).get("exist_2").toString()) > 0) {

	        System.out.println(status);

	        if (status.containsKey(player1)) {
	            result.put("response", status.get(player1));
	        } else if (status.containsKey(player2)) {
	            result.put("response", status.get(player2));
	        } else {
	            result.put("response", getRandomMutualMap(player1, player2));
	        }

	        return result;

	    } else {
	        if (!mostPlayedProcessing.containsKey(pairKey)) {
	            mostPlayedProcessing.put(pairKey, true);

	            mostPlayedQueue.add(new SimpleEntry<>(
	                new SimpleEntry<>(player1, 0),
	                new SimpleEntry<>(player2, 0)
	            ));

	            if (!isProcessing) {
	                isProcessing = true;

	                new Thread(() -> {
	                    try {
	                        getMostPlayed();
	                    } catch (Exception e) {
	                        e.printStackTrace();
	                    } finally {
	                        isProcessing = false;
	                    }
	                }).start();
	            }

	            setStatus(player1, "Fetching most played maps for " + player1);
	            setStatus(player2, "Fetching most played maps for " + player2);
	        } else {
	            setStatus(player1, "Already fetching data for " + player1);
	            setStatus(player2, "Already fetching data for " + player2);
	        }

	        result.put("response", "Fetching user(s)...");
	    }

	    return result;
	}
	
	private void getMutualScores() {
	    SimpleEntry<SimpleEntry<String, Integer>, SimpleEntry<String, Integer>> entry;

	    while ((entry = mutualQueue.poll()) != null) {
	        String player1 = entry.getKey().getKey();
	        String player2 = entry.getValue().getKey();
	        
			setStatus(player1, "Fetching mutual scores for " + player1);
			setStatus(player2, "Fetching mutual scores for " + player2);
	        
	        int offset1 = entry.getKey().getValue();

	        String mutualMapsQ = "SELECT a.map_id FROM UserMostPlayed a WHERE a.user_id = :player_1 AND a.map_id IN ( SELECT b.map_id FROM UserMostPlayed b WHERE b.user_id = :player_2)";

	        db.bindValue("player_1", (String)player1);
	        db.bindValue("player_2", (String)player2);
	        db.bindValue("offset", offset1);

	        db.query2(mutualMapsQ);

	        if (db.result().isEmpty()) {
	            System.out.println("No mutual maps found for " + player1 + " and " + player2);
	            continue;
	        }
	        
	        if (offset1 >= db.result().size()) {
	            System.out.printf("Offset %d exceeds mutual map count (%d) for %s vs %s\n", offset1, db.result().size(), player1, player2);
	            continue;
	        }

	        String map_id = db.result().get(offset1).get("map_id").toString();
	        
            long start = System.currentTimeMillis();
	        String response1 = requestMap(player1, map_id);
            long duration = System.currentTimeMillis() - start;
            if (duration < 1000) {
                try {
					Thread.sleep(1000 - duration);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
            }
            
            long start2 = System.currentTimeMillis();
	        String response2 = requestMap(player2, map_id);
            long duration2 = System.currentTimeMillis() - start2;
            if (duration2 < 1000) {
                try {
					Thread.sleep(1000 - duration2);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
            }

	        if (response1 == null || response2 == null) {
	            System.out.println("no maps");
	            continue;
	        }

	        JSONArray parsed1 = new JSONArray(response1);
	        JSONArray parsed2 = new JSONArray(response2);

	        if (parsed1.isEmpty() || parsed2.isEmpty()) {
	            System.out.println("One of the players has no score on map " + map_id);

	            if (offset1 + 1 < db.result().size()) {
	                mutualQueue.add(
	                    new SimpleEntry<>(
	                        new SimpleEntry<>(player1, offset1 + 1),
	                        new SimpleEntry<>(player2, 0)
	                    )
	                );
	            } else {
	            	status.remove(player1);
	            	status.remove(player2);
	            	
	                System.out.printf("Finished all mutual maps for %s and %s\n", player1, player2);
	            }
	            continue;
	        }

	        JSONObject score1 = parsed1.getJSONObject(0);
	        JSONObject score2 = parsed2.getJSONObject(0);

	        String insertScoresQ = "INSERT INTO UserScores (score_id, score, maxcombo, user_id, perfect, date, rank, enabled_mods, map_id) VALUES (:score_id, :score, :maxcombo, :user_id, :perfect, :date, :rank, :enabled_mods, :map_id) ON DUPLICATE KEY UPDATE score = VALUES(score), maxcombo = VALUES(maxcombo), user_id = VALUES(user_id), perfect = VALUES(perfect), date = VALUES(date), rank = VALUES(rank), enabled_mods = VALUES(enabled_mods), map_id = VALUES(map_id)";

	        db.bindValue("score_id", score1.getString("score_id"));
	        db.bindValue("score", score1.getInt("score"));
	        db.bindValue("maxcombo", score1.getInt("maxcombo"));
	        db.bindValue("user_id", score1.getInt("user_id"));
	        db.bindValue("perfect", score1.getInt("perfect"));
	        db.bindValue("date", score1.getString("date"));
	        db.bindValue("rank", score1.getString("rank"));
	        db.bindValue("enabled_mods", score1.getInt("enabled_mods"));
	        db.bindValue("map_id", Integer.parseInt(map_id));

	        db.query2(insertScoresQ);

	        db.bindValue("score_id", score2.getString("score_id"));
	        db.bindValue("score", score2.getInt("score"));
	        db.bindValue("maxcombo", score2.getInt("maxcombo"));
	        db.bindValue("user_id", score2.getInt("user_id"));
	        db.bindValue("perfect", score2.getInt("perfect"));
	        db.bindValue("date", score2.getString("date"));
	        db.bindValue("rank", score2.getString("rank"));
	        db.bindValue("enabled_mods", score2.getInt("enabled_mods"));
	        db.bindValue("map_id", Integer.parseInt(map_id));

	        db.query2(insertScoresQ);

	        System.out.println("Inserted score with ID " + score1.getString("score_id") + " for User ID " + score1.getString("user_id"));
	        System.out.println("Inserted score with ID " + score2.getString("score_id") + " for User ID " + score2.getString("user_id"));

	        mutualQueue.add(
	            new SimpleEntry<>(
	                new SimpleEntry<>(player1, offset1 + 1),
	                new SimpleEntry<>(player2, 0)
	            )
	        );
	    }
	}
	
	private String requestMap(String player, String map_id) {
		ApiRequester api = new ApiRequester();
		api.setRequestMethod(Method.GET);
		api.setEndpoint("https://osu.ppy.sh/api/get_scores");
		
		Map<String, String> params = new HashMap<>();
		params.put("k", ApiKey);
		params.put("b", map_id);
		params.put("u", player);
		
		api.setParameters(params);

		String response = "";
		
		try {
		    response = api.request();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("https://osu.ppy.sh/api/get_scores?k=" + ApiKey + "&b=" + map_id + "&u=" + player);
		
		return response;
	}
	
	private void getMostPlayed() throws Exception {
		
	    while (true) {
	        var entry = mostPlayedQueue.poll();
	        if (entry == null) {
	            System.out.println("MostPlayedQueue is empty, exiting thread.");
	            break;
	        }

	        String player1 = entry.getKey().getKey();
	        String player2 = entry.getValue().getKey();

	        int offset1 = entry.getKey().getValue();
	        int offset2 = entry.getValue().getValue();

	        System.out.printf("Fetching maps for users %s (%d) and %s (%d)%n", player1, offset1, player2, offset2);
	        	        
	        boolean finished1 = finishedUsers.getOrDefault(player1, false);
	        boolean finished2 = finishedUsers.getOrDefault(player2, false);
	        
	        if (!finished1) {
	            long start = System.currentTimeMillis();
	            finished1 = fetchAndInsertMostPlayed(player1, offset1);
	            long duration = System.currentTimeMillis() - start;
	            if (duration < 1000) {
	                Thread.sleep(1000 - duration);
	            }
	        }

	        if (!finished2) {
	            long start = System.currentTimeMillis();
	            finished2 = fetchAndInsertMostPlayed(player2, offset2);
	            long duration = System.currentTimeMillis() - start;
	            if (duration < 1000) {
	                Thread.sleep(1000 - duration);
	            }
	        }

	        if (finished1 && finished2) {
	            System.out.println("finished both");
	            
	            String pairKey = getPairKey(player1, player2);
	            mostPlayedProcessing.remove(pairKey);
	            
	            mutualQueue.add(new SimpleEntry<>(
	                new SimpleEntry<>(player1, 0),
	                new SimpleEntry<>(player2, 0)
	            ));
	            
	            finishedUsers.remove(player1);
	            finishedUsers.remove(player2);
	            continue;
	        }

	        if (!finished1 && !finished2) {
	            mostPlayedQueue.add(new SimpleEntry<>(
	                new SimpleEntry<>(player1, offset1 + 100),
	                new SimpleEntry<>(player2, offset2 + 100)
	            ));
	        } else if (!finished1) {
	            mostPlayedQueue.add(new SimpleEntry<>(
	                new SimpleEntry<>(player1, offset1 + 100),
	                new SimpleEntry<>(player2, offset2)
	            ));
	        } else if (!finished2) {
	            mostPlayedQueue.add(new SimpleEntry<>(
	                new SimpleEntry<>(player1, offset1),
	                new SimpleEntry<>(player2, offset2 + 100)
	            ));
	        }
	    }
	}


	private boolean fetchAndInsertMostPlayed(String userId, int offset) {
	    String endpoint = "https://osu.ppy.sh/users/" + userId + "/beatmapsets/most_played?limit=100&offset=" + offset;
	    String insertScoreQ = "INSERT INTO UserMostPlayed (user_id, map_id) VALUES (:user_id, :map_id)";	    

	    try {
	        ApiRequester api = new ApiRequester();
	        api.setRequestMethod(Method.GET);
	        api.setEndpoint(endpoint);

	        String apiResponse = api.request();
	        ObjectMapper mapper = new ObjectMapper();
	        JsonNode root = mapper.readTree(apiResponse);

	        if (!root.isArray() || root.size() == 0) {
	            System.out.println("No more most played maps for user " + userId);
	            finishedUsers.put(userId, true);
	            return true;
	        }

	        for (JsonNode node : root) {
	            db.bindValue("user_id", userId);
	            db.bindValue("map_id", node.get("beatmap_id").asInt());

	            db.query2(insertScoreQ);
	        }

	    } catch (Exception e) {
	        System.err.println("Error processing user " + userId + ": " + e.getMessage());
	        finishedUsers.put(userId, true);
	        return true;
	    }

	    return false;
	}
	
	private String getRandomMutualMap(String player1, String player2) throws JsonProcessingException {
		
		System.out.println("Requested map for " + player1 + " and " + player2);
		
		String randomSql = "SELECT a.map_id, a.user_id AS user_player, a.score AS score_player, a.maxcombo AS maxcombo_player, a.perfect AS perfect_player, a.date AS date_player, a.rank AS rank_player, a.enabled_mods AS mods_player, b.user_id AS user_target, b.score AS score_target, b.maxcombo AS maxcombo_target, b.perfect AS perfect_target, b.date AS date_target, b.rank AS rank_target, b.enabled_mods AS mods_target FROM UserScores a JOIN UserScores b ON a.map_id = b.map_id WHERE a.user_id = :player2 AND b.user_id = :player1 AND a.score < b.score order by rand() limit 1";
		
		Map<String, Object> randomBinds = new HashMap<>();
		randomBinds.put("player1", player1);
		randomBinds.put("player2", player2);
		
		db.bindValue("player1", player1);
		db.bindValue("player2", player2);
		
		db.query2(randomSql);

        ObjectMapper mapper = new ObjectMapper();
        
        String resultString = mapper.writeValueAsString(db.result());
        
        if(resultString.equals("[]")) {
        	resultString = "{\"message\":\"" + player1 + " has no scores better than " + player2 + ".\"}";
        }
        
        System.out.println(resultString);
        
        return resultString;
	}
	
	public void setStatus(String player, String message) {
		if (this.status.containsKey(player)) {
		    this.status.replace(player, message);
		} else {
		    this.status.put(player, message);
		}

	}
	
	public String getStatus(String player) {
		return this.status.get(player);
	}

}
