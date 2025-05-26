package main;

import java.io.IOException;

import constants.Config;
import database.Database;
import endpoint.HTTPEndpoint;
import scores.GetScores;

public class Main {
	
	public static void main(String[] args) throws IOException {
		
		Config conf = new Config();
		conf.setConstants();
		
		GetScores getscores = new GetScores();

		Thread ScoreThread = new Thread(() -> {
			try {
				getscores.checkForMostPlayedJob();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
		ScoreThread.start();

		Thread HTTPThread = new Thread(() -> {
			try {
				HTTPEndpoint http = new HTTPEndpoint(getscores);
				http.listen();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		HTTPThread.start();

	}
}
