package scores;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import database.Database;
import network.ApiRequester;
import network.ApiRequester.Method;
import tools.Mod;

public class GetScores {

	private ConcurrentLinkedQueue<SimpleEntry<SimpleEntry<String, Integer>, SimpleEntry<String, Integer>>> mostPlayedQueue;
	private ConcurrentLinkedQueue<SimpleEntry<SimpleEntry<String, Integer>, SimpleEntry<String, Integer>>> mutualQueue;
	private ConcurrentLinkedQueue<String> recentQueue = new ConcurrentLinkedQueue<>();
	private ConcurrentLinkedQueue<String> registerQueue = new ConcurrentLinkedQueue<>();
	private final Map<String, Boolean> stillFetching = new ConcurrentHashMap<>();
	
	private final Set<String> finishedUsers = ConcurrentHashMap.newKeySet();
	private final Map<String, Boolean> mostPlayedProcessing = new ConcurrentHashMap<>();
	
	private Map<String, String> status = new HashMap<>();
	
	private volatile boolean isProcessing = false;
	
	private static String ApiKey = "";
	private static boolean toggle;
	
	public GetScores() {
	    
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
	    
	    new Thread(() -> {
	        try {
	            RecentScores();
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }).start();

	}
	
	private String getPairKey(String player1, String player2) {
	    return player1.compareTo(player2) < 0 ? player1 + ":" + player2 : player2 + ":" + player1;
	}
	
	public static void setApiKey(String key) {
		ApiKey = key;
	}
	
	public Map<String, Object> getScores(String player1, String player2, String random) throws JsonProcessingException {
	    String queryExist = "SELECT (SELECT COUNT(1) FROM UserMostPlayed WHERE user_id = :player1) AS exist_1, " +
	                        "(SELECT COUNT(1) FROM UserMostPlayed WHERE user_id = :player2) AS exist_2";
	    
	    String queryRegistered = "SELECT * FROM RegisteredUsers WHERE user_id = :user_id";
	    String queryScores = "SELECT COUNT(*) AS count FROM UserScores a " +
	                         "JOIN UserScores b ON a.map_id = b.map_id " +
	                         "WHERE a.user_id = :player1 AND b.user_id = :player2";

	    Database db = new Database();
	    db.setDatabase("ScoreSniper");
	    db.connect();

	    boolean empty_player1 = false;
	    boolean empty_player2 = false;

	    db.bindValue("user_id", player1);
	    db.query2(queryRegistered);
	    if (db.result().isEmpty()) {
	        registerQueue.add(player1);
	        empty_player1 = true;
	    }

	    db.bindValue("user_id", player2);
	    db.query2(queryRegistered);
	    if (db.result().isEmpty()) {
	        registerQueue.add(player2);
	        empty_player2 = true;
	    }

	    if (empty_player1 || empty_player2) {
	        new Thread(() -> {
	            try {
	                registerNewUser();
	            } catch (Exception e) {
	                e.printStackTrace();
	            } finally {
	                isProcessing = false;
	            }
	        }).start();
	    }

	    db.bindValue("player1", player1);
	    db.bindValue("player2", player2);
	    db.query2(queryScores);
	    int mutualScoreCount = Integer.parseInt(db.result().get(0).get("count").toString());

	    db.bindValue("player1", player1);
	    db.bindValue("player2", player2);
	    db.query2(queryExist);
	    int exist1 = Integer.parseInt(db.result().get(0).get("exist_1").toString());
	    int exist2 = Integer.parseInt(db.result().get(0).get("exist_2").toString());

	    String pairKey = getPairKey(player1, player2);
	    Map<String, Object> result = new HashMap<>();

	    if (exist1 > 0 && exist2 > 0 && mutualScoreCount > 0) {
	        if (stillFetching.getOrDefault(pairKey, false)) {
	            Map<String, Object> response = new LinkedHashMap<>();
	            response.put("warning", "Score fetching is still in progress. More maps may be added soon.");

	            String json = getRandomMutualMap(player1, player2, random);
	            ObjectMapper mapper = new ObjectMapper();
	            JsonNode root = mapper.readTree(json);

	            if (root.isObject()) {
	                for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
	                    String field = it.next();
	                    response.put(field, mapper.convertValue(root.get(field), Object.class));
	                }
	            } else {
	                return Map.of("response", json);
	            }

	            result.put("response", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(response));
	        } else {
	            result.put("response", getRandomMutualMap(player1, player2, random));
	        }

	        db.close();
	        return result;
	    }

	    if (!mostPlayedProcessing.containsKey(pairKey)) {
	        mostPlayedProcessing.put(pairKey, true);
	        stillFetching.put(pairKey, true);

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

	        setStatus(player1, "Fetching most played maps for " + player1 + " and " + player2);
	        result.put("response", "{\"message\":\"Fetching user(s)...\"}");
	    } else {
	        setStatus(player1, "Fetching most played maps for " + player1 + " and " + player2);
	        result.put("response", "{\"message\":\"Still fetching most played maps for " + player1 + " and " + player2 + "\"}");
	    }

	    db.close();
	    return result;
	}
	
	private void registerNewUser() {
		Database db = new Database();
		db.setDatabase("ScoreSniper");
		db.connect();
		
		String insertRegister = "insert into RegisteredUsers (user_id, username, last_updated) values (:user_id, :username, current_timestamp)";
			
		while(!registerQueue.isEmpty()) {
			String player = registerQueue.poll();
			String response = "";
			
			ApiRequester api = new ApiRequester();
			api.setEndpoint("https://osu.ppy.sh/api/get_user");
			
			Map<String, String> parameters = new HashMap<>();
			parameters.put("k", ApiKey);
			parameters.put("u", player);
			
			api.setParameters(parameters);
			try {
				response = api.request();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			log(response);

			JSONArray jsonArray = new JSONArray(response);
			if (jsonArray.length() > 0) {
			    JSONObject json = jsonArray.getJSONObject(0);
			    String username = json.getString("username");
			    
			    db.bindValue("user_id", player);
			    db.bindValue("username", username);
			    db.query2(insertRegister);
			}
	        
		}
		
		db.close();
	}
	
	private void getMutualScores() {
	    SimpleEntry<SimpleEntry<String, Integer>, SimpleEntry<String, Integer>> entry;
	    
	    Database db = new Database();
	    db.setDatabase("ScoreSniper");
	    db.connect();

	    while ((entry = mutualQueue.poll()) != null) {
	        String player1 = entry.getKey().getKey();
	        String player2 = entry.getValue().getKey();
	        
			setStatus(player1, "Fetching mutual scores for " + player1 + " and " + player2);
			setStatus(player2, "Fetching mutual scores for " + player2 + " and " + player1);
	        
	        int offset1 = entry.getKey().getValue();

	        String mutualMapsQ = "SELECT a.map_id FROM UserMostPlayed a WHERE a.user_id = :player_1 AND a.map_id IN ( SELECT b.map_id FROM UserMostPlayed b WHERE b.user_id = :player_2)";

	        db.bindValue("player_1", (String)player1);
	        db.bindValue("player_2", (String)player2);
	        db.bindValue("offset", offset1);

	        db.query2(mutualMapsQ);

	        if (db.result().isEmpty()) {
	            log("No mutual maps found for " + player1 + " and " + player2);
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
	            log("no maps");
	            continue;
	        }

	        JSONArray parsed1 = new JSONArray(response1);
	        JSONArray parsed2 = new JSONArray(response2);

	        if (parsed1.isEmpty() || parsed2.isEmpty()) {

	            log("One of the players has no score on map " + map_id);

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
	            	stillFetching.remove(getPairKey(player1, player2));
	            	finishedUsers.remove(player1);
	            	finishedUsers.remove(player2);
	            	
	            	log("Finished all mutual maps for " + player1 + " and " + player2);
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
	        
	        log("Inserted score with ID " + score1.getString("score_id") + " for User ID " + score1.getString("user_id"));
	        log("Inserted score with ID " + score2.getString("score_id") + " for User ID " + score2.getString("user_id"));

	        mutualQueue.add(
	            new SimpleEntry<>(
	                new SimpleEntry<>(player1, offset1 + 1),
	                new SimpleEntry<>(player2, 0)
	            )
	        );
	    }
	    db.close();
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
		
		log("https://osu.ppy.sh/api/get_scores?k=" + ApiKey + "&b=" + map_id + "&u=" + player);
		
		return response;
	}
	
	private void getMostPlayed() throws Exception {
		
	    while (true) {
	        var entry = mostPlayedQueue.poll();
	        if (entry == null) {
	            log("MostPlayedQueue is empty, exiting thread.");
	            break;
	        }

	        String player1 = entry.getKey().getKey();
	        String player2 = entry.getValue().getKey();

	        int offset1 = entry.getKey().getValue();
	        int offset2 = entry.getValue().getValue();
	        
	        log("Fetching maps for users " + player1 + "(" + offset1 + ") and " + player2 + "(" + offset2 + ")");
	        	        
	        boolean finished1 = finishedUsers.contains(player1);
	        boolean finished2 = finishedUsers.contains(player2);
	        
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
	            
	            String pairKey = getPairKey(player1, player2);
	            mostPlayedProcessing.remove(pairKey);
	            
	            mutualQueue.add(new SimpleEntry<>(
	                new SimpleEntry<>(player1, 0),
	                new SimpleEntry<>(player2, 0)
	            ));
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

	private void getLatestPlayed() throws JsonProcessingException {
	    Database db = new Database();
	    db.setDatabase("ScoreSniper");
	    db.connect();
	    
	    ApiRequester api = new ApiRequester();
	    api.setRequestMethod(Method.GET);
	    
	    String queryMostPlayed = "INSERT IGNORE INTO UserMostPlayed (user_id, map_id) VALUES (:user_id, :map_id)";
	    String insertScores = "INSERT IGNORE INTO UserScores (score_id, score, map_id, user_id, maxcombo, perfect, date, rank, enabled_mods) "
	    	    + "VALUES (:score_id, :score, :map_id, :user_id, :maxcombo, :perfect, :date, :rank, :enabled_mods)";
	    	    
	    String player = "";
	    
	    while(!recentQueue.isEmpty()) {
	    	player = recentQueue.poll();

	    	log("updating outdated user...");

		    ObjectMapper mapper = new ObjectMapper();

		    int offset = 0;
		    boolean moreScores = true;

		    while (moreScores) {
		        String endpoint = "https://osu.ppy.sh/users/" + player + "/scores/recent?mode=osu&limit=100&offset=" + offset;
		        api.setEndpoint(endpoint);

		        String response = "";
		        
		        try {
		            long start = System.currentTimeMillis();
		            response = api.request();
		            long duration = System.currentTimeMillis() - start;
		            if (duration < 1000) {
		                Thread.sleep(1000 - duration);
		            }
		        } catch (Exception e) {
		            e.printStackTrace();
		            break;
		        }

		        if (response.equalsIgnoreCase("[]")) {
		            break;
		        }

		        JsonNode root = mapper.readTree(response);
		        if (root == null || !root.isArray() || root.size() == 0) {
		            break;
		        }

		        for (JsonNode node : root) {
		            db.bindValue("user_id", player);
		            db.bindValue("map_id", node.get("beatmap_id").asInt());
		            db.query2(queryMostPlayed);

		            List<String> modList = new ArrayList<>();
		            
		            for (JsonNode mod : node.get("mods")) {
		                modList.add(mod.asText());
		            }
		            
		            int mods = Mod.convertModsToBitmask(modList);

		            db.bindValue("score_id", node.get("id").asLong());
		            db.bindValue("score", node.get("legacy_total_score").asLong());
		            db.bindValue("map_id", node.get("beatmap_id").asLong());
		            db.bindValue("user_id", Long.parseLong(player));
		            db.bindValue("maxcombo", node.get("max_combo").asInt());
		            db.bindValue("perfect", node.get("legacy_perfect").asBoolean());
		            
		            String createdAtRaw = node.get("ended_at").asText();
		            String createdAtSqlFormat = java.time.OffsetDateTime
		                .parse(createdAtRaw)
		                .toLocalDateTime()
		                .toString().replace('T', ' ');

		            db.bindValue("date", createdAtSqlFormat);
		            
		            db.bindValue("rank", node.get("rank").asText());
		            db.bindValue("enabled_mods", mods);
		            
		            log("inserting scores...");

		            db.query2(insertScores);
		        }

		        offset += 100;
		      	        
		    }
		    String updateUpdated = "UPDATE RegisteredUsers set last_updated = current_timestamp where user_id = :user_id";
		    db.bindValue("user_id", player);
		    
		    db.query2(updateUpdated);
	    }

	    db.close();
	}

	
	public void RecentScores() {
		String query = "SELECT user_id FROM RegisteredUsers WHERE last_updated < NOW() - INTERVAL 1 HOUR";
		
		Database db = new Database();
		db.setDatabase("ScoreSniper");
		db.connect();
		
		while(true) {
			db.query(query);
			
			if(!db.result().isEmpty()) {
				for(int i = 0; i < db.result().size(); i++) {
					recentQueue.add(db.result().get(i).get("user_id").toString());
				}
				
			    new Thread(() -> {
			        try {
						getLatestPlayed();
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
			    }).start();
			}
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private boolean fetchAndInsertMostPlayed(String userId, int offset) {
	    String endpoint = "https://osu.ppy.sh/users/" + userId + "/beatmapsets/most_played?limit=100&offset=" + offset;
	    String insertScoreQ = "INSERT IGNORE INTO UserMostPlayed (user_id, map_id, beatmapset_id, artist, creator, title, version, difficulty_rating) VALUES (:user_id, :map_id, :beatmapset_id, :artist, :creator, :title, :version, :difficulty_rating)";   
	    
	    Database db = new Database();
	    db.setDatabase("ScoreSniper");
	    db.connect();

	    try {
	        ApiRequester api = new ApiRequester();
	        api.setRequestMethod(Method.GET);
	        api.setEndpoint(endpoint);

	        String apiResponse = api.request();
	        ObjectMapper mapper = new ObjectMapper();
	        JsonNode root = mapper.readTree(apiResponse);

	        if (!root.isArray() || root.size() == 0) {
	            log("No more most played maps for user " + userId);
	            finishedUsers.add(userId);
	            return true;
	            
	        }

	        for (JsonNode node : root) {
	            JsonNode beatmap = node.get("beatmap");
	            JsonNode beatmapset = node.get("beatmapset");

	            db.bindValue("user_id", userId);
	            db.bindValue("map_id", beatmap.get("id").asInt());
	            db.bindValue("beatmapset_id", beatmap.get("beatmapset_id").asInt());
	            db.bindValue("artist", beatmapset.get("artist_unicode").asText()); // oder .get("artist")
	            db.bindValue("creator", beatmapset.get("creator").asText());
	            db.bindValue("title", beatmapset.get("title_unicode").asText()); // oder .get("title")
	            db.bindValue("version", beatmap.get("version").asText());
	            db.bindValue("difficulty_rating", beatmap.get("difficulty_rating").asDouble());

	            db.query2(insertScoreQ);
	        }


	    } catch (Exception e) {
	        System.err.println("Error processing user " + userId + ": " + e.getMessage());
	        finishedUsers.add(userId);
	        db.close();
	        return true;
	        
	    }
	    db.close();
	    return false;
	    
	}
	
	private String getRandomMutualMap(String player1, String player2, String random) throws JsonProcessingException {
	    log("Requested map(s) for " + player1 + " and " + player2 + " (random=" + random + ")");

	    String pairKey = getPairKey(player1, player2);
	    boolean isStillFetching = stillFetching.getOrDefault(pairKey, false);

	    Database db = new Database();
	    db.setDatabase("ScoreSniper");
	    db.connect();

	    boolean randomMode = "1".equals(random);

	    String sql = "SELECT " +
	    	    "a.map_id, " +
	    	    "a.score_id AS score_id_player, " +
	    	    "a.score AS score_player, " +
	    	    "a.user_id AS user_player, " +
	    	    "a.maxcombo AS maxcombo_player, " +
	    	    "a.perfect AS perfect_player, " +
	    	    "a.date AS date_player, " +
	    	    "a.rank AS rank_player, " +
	    	    "a.enabled_mods AS mods_player, " +
	    	    "b.score_id AS score_id_target, " +
	    	    "b.score AS score_target, " +
	    	    "b.user_id AS user_target, " +
	    	    "b.maxcombo AS maxcombo_target, " +
	    	    "b.perfect AS perfect_target, " +
	    	    "b.date AS date_target, " +
	    	    "b.rank AS rank_target, " +
	    	    "b.enabled_mods AS mods_target, " +
	    	    "mp.beatmapset_id, " +
	    	    "mp.artist, " +
	    	    "mp.creator, " +
	    	    "mp.title, " +
	    	    "mp.version, " +
	    	    "mp.difficulty_rating " +
	    	    "FROM UserScores a " +
	    	    "JOIN UserScores b ON a.map_id = b.map_id " +
	    	    "JOIN UserMostPlayed mp ON mp.map_id = a.map_id " +
	    	    "WHERE a.user_id = :player2 AND b.user_id = :player1 AND a.score < b.score ";


	    if (randomMode) {
	        sql += "ORDER BY rand() LIMIT 1";
	    }

	    db.bindValue("player1", player1);
	    db.bindValue("player2", player2);
	    db.query2(sql);

	    List<Map<String, Object>> result = db.result();

	    ObjectMapper mapper = new ObjectMapper();
	    mapper.enable(SerializationFeature.INDENT_OUTPUT);

	    if (result.isEmpty()) {
	        return mapper.writeValueAsString(
	            Map.of("error", "no mutual beatmap where player1 < player2")
	        );
	    }

	    if (randomMode) {
	        Map<String, Object> row = result.get(0);
	        Map<String, Object> response = new LinkedHashMap<>();

	        if (isStillFetching) {
	            response.put("warning", "Score fetching is still in progress. More maps may be added soon.");
	        }

	        response.put("map_id", row.get("map_id"));

	        Map<String, Object> playerData = new LinkedHashMap<>();
	        playerData.put("user_id", row.get("user_player"));
	        playerData.put("score", Long.parseLong(row.get("score_player").toString()));
	        playerData.put("maxcombo", row.get("maxcombo_player"));
	        playerData.put("perfect", row.get("perfect_player"));
	        playerData.put("date", row.get("date_player"));
	        playerData.put("rank", row.get("rank_player"));
	        playerData.put("mods", row.get("mods_player"));

	        Map<String, Object> targetData = new LinkedHashMap<>();
	        targetData.put("user_id", row.get("user_target"));
	        targetData.put("score", Long.parseLong(row.get("score_target").toString()));
	        targetData.put("maxcombo", row.get("maxcombo_target"));
	        targetData.put("perfect", row.get("perfect_target"));
	        targetData.put("date", row.get("date_target"));
	        targetData.put("rank", row.get("rank_target"));
	        targetData.put("mods", row.get("mods_target"));

	        response.put("player", playerData);
	        response.put("target", targetData);

	        return mapper.writeValueAsString(response);
	    } else {
	    	List<Map<String, Object>> output = new ArrayList<>();

	    	for (Map<String, Object> row : result) {
	    	    Map<String, Object> map = new LinkedHashMap<>();
	    	    Map<String, Object> mapInfo = new LinkedHashMap<>();
	    	    mapInfo.put("map_id", row.get("map_id"));
	    	    mapInfo.put("beatmapset_id", row.get("beatmapset_id"));
	    	    mapInfo.put("artist", row.get("artist"));
	    	    mapInfo.put("creator", row.get("creator"));
	    	    mapInfo.put("title", row.get("title"));
	    	    mapInfo.put("version", row.get("version"));
	    	    mapInfo.put("difficulty_rating", row.get("difficulty_rating"));
	    	    map.put("map", mapInfo);


	    	    Map<String, Object> playerData = new LinkedHashMap<>();
	    	    playerData.put("user_id", row.get("user_player"));
	    	    playerData.put("score", Long.parseLong(row.get("score_player").toString()));
	    	    playerData.put("maxcombo", row.get("maxcombo_player"));
	    	    playerData.put("perfect", row.get("perfect_player"));
	    	    playerData.put("date", row.get("date_player"));
	    	    playerData.put("rank", row.get("rank_player"));
	    	    playerData.put("mods", row.get("mods_player"));

	    	    Map<String, Object> targetData = new LinkedHashMap<>();
	    	    targetData.put("user_id", row.get("user_target"));
	    	    targetData.put("score", Long.parseLong(row.get("score_target").toString()));
	    	    targetData.put("maxcombo", row.get("maxcombo_target"));
	    	    targetData.put("perfect", row.get("perfect_target"));
	    	    targetData.put("date", row.get("date_target"));
	    	    targetData.put("rank", row.get("rank_target"));
	    	    targetData.put("mods", row.get("mods_target"));

	    	    map.put("player", playerData);
	    	    map.put("target", targetData);

	    	    output.add(map);
	    	}

	    	Map<String, Object> response = new LinkedHashMap<>();
	    	if (isStillFetching) {
	    	    response.put("warning", "Score fetching is still in progress. More maps may be added soon.");
	    	}
	    	response.put("results", output);

	    	db.close();
	    	return mapper.writeValueAsString(response);
	    }
	}

	public void setStatus(String player, String message) {
		if (this.status.containsKey(player)) {
		    this.status.replace(player, message);
		} else {
		    this.status.put(player, message);
		}

	}
	
	private void log(String message) {
	    if (toggle) {
	        System.out.println("[" + java.time.LocalDateTime.now() + "][" + Thread.currentThread().getName() + "] " + message);
	    }
	}
	
	public String getStatus(String player) {
		return this.status.get(player);
	}
	
	public static void setDebugOutput(boolean debug) {
		toggle = debug;
	}

}
