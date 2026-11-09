package com.ts;

import com.ts.common.constants.Constants;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Command-line client to trigger graceful shutdown of the running Main process.
 * Usage: java com.ts.ShutdownClient
 */
public class ShutdownClient {
    public static void main(String[] args) {
        System.out.println("Sending shutdown signal to localhost:" + Constants.SHUTDOWN_PORT + " ...");
        try (Socket socket = new Socket("127.0.0.1", Constants.SHUTDOWN_PORT);
             OutputStream os = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {

            writer.println(Constants.SHUTDOWN_SECRET);
            writer.flush();
            System.out.println("Shutdown signal sent successfully.");

        } catch (ConnectException e) {
            System.err.println("Error: Could not connect to shutdown port. Is the application running?");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error sending shutdown signal: " + e.getMessage());
            System.exit(1);
        }
    }
}