package com.neueda.leap.team;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@SpringBootApplication()
public class TeamApplication {

	static String url = System.getenv("SPRING_DATASOURCE_URL");
	static String username = System.getenv("SPRING_DATASOURCE_USERNAME");
	static String password = System.getenv("SPRING_DATASOURCE_PASSWORD");

	public static void main(String[] args) {
		try {
			Connection conn = DriverManager.getConnection(url, username, password);
            System.out.println("Connected to database successfully ITS WORKING");
			conn.close();
		} catch (SQLException e) {
			throw new RuntimeException(e.getMessage());
		}

	}

}
