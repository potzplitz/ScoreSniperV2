package scores;

import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import database.Database;
import network.ApiRequester;

public class GetScores {
	
	private final Database db;
	private final ConcurrentLinkedQueue<String> queue;
	private ApiRequester api;
	
	private volatile boolean isProcessing = false;
	
	public static String ApiKey;
	
	public GetScores() {
		db = new Database();
		db.setDatabase("ScoreSniper");
		db.connect();
		
		queue = new ConcurrentLinkedQueue<>();
	}
	
	private void getUserMostPlayed() {
		// TODO
		while(true) {

			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void checkForMostPlayedJob() throws InterruptedException {
		while (true) {
			System.out.println(queue.isEmpty());
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
			}
			Thread.sleep(1000);
			System.out.println("check");
		}
	}
	
	public Map<String, Object> getScores(String player1, String player2) throws JsonProcessingException {
	    String queryExist = "SELECT count(1) as count FROM UserMostPlayed WHERE ((user_id = :player1) OR (user_id = :player2))";

	    Map<String, Object> bindsExist = new HashMap<>();
	    bindsExist.put("player1", player1);
	    bindsExist.put("player2", player2);
	    
	    Map<String, Object> result = new HashMap<>();
	    
	    if (Integer.parseInt(db.query2(queryExist, bindsExist).get(0).get("count").toString()) > 0) {
	    	result.put("response", getRandomMutualMap(player1, player2));
	    	
	    } else {
	    	queue.add(player1);
	    	queue.add(player2);
	    	
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
}
