package com.neueda.leap.team;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@SpringBootApplication()
public class TeamApplication {

	private static String url = "jdbc:postgresql://10.18.65.245:5432/app";
	private static String username = "postgres";
	private static String password = "postgres";


	public static void main(String[] args) {
		try {
			Connection conn = DriverManager.getConnection(url, username, password);
            System.out.println("Connected to database successfully");
			conn.close();
		} catch (SQLException e) {
			throw new RuntimeException(e.getMessage());
		}

	}

}
