package main;

import java.io.IOException;

import constants.Config;
import endpoint.HTTPEndpoint;
import scores.GetScores;

public class Main {
	
	public static void main(String[] args) throws IOException {
		
		Config conf = new Config();
		conf.setConstants();
		
		GetScores getscores = new GetScores();

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
