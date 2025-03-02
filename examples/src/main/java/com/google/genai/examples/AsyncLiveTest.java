/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Usage:
 *
 * <p>1a. If you are using Vertex AI, setup ADC to get credentials:
 * https://cloud.google.com/docs/authentication/provide-credentials-adc#google-idp
 *
 * <p>Then set Project, Location, and USE_VERTEXAI flag as environment variables:
 *
 * <p>export GOOGLE_CLOUD_PROJECT=YOUR_PROJECT
 *
 * <p>export GOOGLE_CLOUD_LOCATION=YOUR_LOCATION
 *
 * <p>1b. If you are using Gemini Developer AI, set an API key environment variable. You can find a
 * list of available API keys here: https://aistudio.google.com/app/apikey
 *
 * <p>export GOOGLE_API_KEY=YOUR_API_KEY
 *
 * <p>2. Compile the java package and run the sample code.
 *
 * <p>mvn clean compile
 *
 * <p>mvn exec:java -Dexec.mainClass="com.google.genai.examples.AsyncLiveTest"
 */
package com.google.genai.examples;

import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Test cases for AsyncLive
 */
public class AsyncLiveTest {
    public static void main(String[] args) throws Exception {
        // Example Usage
        Client client;
        if (System.getenv("GOOGLE_API_KEY") == null) {
            client = Client.builder().vertexAI(true).build();
        } else {
            client = new Client();
        }
        String modelName = "gemini-2.0-flash-001";
        // String modelName = "gemini-2.0-flash-exp"; // Change to the correct model
        // name
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("candidateCount", 1);
        generationConfig.put("maxOutputTokens", 2048);
        generationConfig.put("temperature", 0.9);
        generationConfig.put("topP", 1.0);
        generationConfig.put("topK", 40);
        generationConfig.put("presencePenalty", 0.0);
        generationConfig.put("frequencyPenalty", 0.0);
        // This is the default response modality
        // Gemini also support Audio response
        generationConfig.put("responseModalities", ImmutableList.of("TEXT"));
        config.put("generation_config", generationConfig);

        // Test case 1: Base text message test
        testTextMessage(client, modelName, config);

        // Test case 2: Send image message test
        // testImageMessage(client, modelName, config);

        // Test case 3: send audio message test
        // testAudioMessage(client, modelName, config);

    }

    /**
     * test text message
     *
     * @param client
     * @param modelName
     * @param config
     * @throws Exception
     */
    public static void testTextMessage(Client client, String modelName, Map<String, Object> config) throws Exception {
        // Create a new message handler
        AsyncLive.MessageHandler messageHandler = new AsyncLive.MessageHandler();
        AsyncLive asyncLive = new AsyncLive(client, modelName, config);
        asyncLive.connect();
        // Set the message handler for this asyncLive instance
        asyncLive.setCustomMessageHandler(messageHandler);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        System.out.println("Enter text input (type 'exit' to quit):");

        while (true) { // Keep the loop running indefinitely

            line = reader.readLine();

            if ("exit".equalsIgnoreCase(line)) {
                asyncLive.close();
                break;
            }
            if (asyncLive.isRunning()) {
                if (line == null || line.trim().isEmpty()) {
                    System.out.println("Please input something!");
                    continue;
                }
                asyncLive.send(line);

                // Wait for a response.
                messageHandler.await();
                // Print the response.
                System.out.println("Gemini Response: " + messageHandler.getLatestResponse());
                // reset the latch
                messageHandler.resetLatch();
            }
            System.out.println("Enter text input (type 'exit' to quit):");
        }
    }

    /**
     * test image message
     *
     * @param client
     * @param modelName
     * @param config
     * @throws Exception
     */
    public static void testImageMessage(Client client, String modelName, Map<String, Object> config) throws Exception {
        // Create a new message handler
        AsyncLive.MessageHandler messageHandler = new AsyncLive.MessageHandler();

        AsyncLive asyncLive = new AsyncLive(client, modelName, config);
        asyncLive.connect();
        // Set the message handler for this asyncLive instance
        asyncLive.setCustomMessageHandler(messageHandler);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        System.out.println(
                "Enter text input (type 'exit' to quit, type 'send_image [file_path]' to send image, then type question about the image):");

        while (true) { // Keep the loop running indefinitely

            line = reader.readLine();

            if ("exit".equalsIgnoreCase(line)) {
                asyncLive.close();
                break;
            }
            if (asyncLive.isRunning()) {
                if (line == null || line.trim().isEmpty()) {
                    System.out.println("Please input something!");
                    continue;
                }
                if (line.startsWith("send_image")) {
                    String[] parts = line.split(" ", 2);
                    if (parts.length == 2) {
                        File imageFile = new File(parts[1]);
                        if (imageFile.exists() && imageFile.isFile()) {
                            asyncLive.send("Please describe the image:", imageFile);
                            // Wait for a response.
                            messageHandler.await();
                            // Print the response.
                            System.out.println("Gemini Response: " + messageHandler.getLatestResponse());
                            // reset the latch
                            messageHandler.resetLatch();
                        } else {
                            System.out.println("Invalid image file path.");
                        }
                    } else {
                        System.out.println("Invalid command. Usage: send_image [file_path]");
                    }
                } else {
                    asyncLive.send(line);
                    // Wait for a response.
                    messageHandler.await();
                    // Print the response.
                    System.out.println("Gemini Response: " + messageHandler.getLatestResponse());
                    // reset the latch
                    messageHandler.resetLatch();
                }
            }
            System.out.println("Enter text input (type 'exit' to quit, type 'send_image [file_path]' to send image):");
        }
    }

    /**
     * test audio message
     *
     * @param client
     * @param modelName
     * @param config
     * @throws Exception
     */
    public static void testAudioMessage(Client client, String modelName, Map<String, Object> config) throws Exception {
        // Create a new message handler
        AsyncLive.MessageHandler messageHandler = new AsyncLive.MessageHandler();

        // set audio response modalitie
        Map<String, Object> configForAudio = new HashMap<>(config);
        Map<String, Object> generationConfigForAudio = new HashMap<>(
                (Map<String, Object>) configForAudio.get("generation_config"));

        // Audio response only
        generationConfigForAudio.put("responseModalities", ImmutableList.of("AUDIO"));
        // To get both Text and Audio as response:
        // generationConfigForAudio.put("responseModalities", ImmutableList.of("AUDIO",
        // "TEXT"));
        configForAudio.put("generation_config", generationConfigForAudio);

        AsyncLive asyncLive = new AsyncLive(client, modelName, configForAudio);
        asyncLive.connect();
        // Set the message handler for this asyncLive instance
        asyncLive.setCustomMessageHandler(messageHandler);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        System.out.println("Enter text input (type 'exit' to quit, type 'send_audio [file_path]' to send audio):");

        while (true) { // Keep the loop running indefinitely
            line = reader.readLine();

            if ("exit".equalsIgnoreCase(line)) {
                asyncLive.close();
                break;
            }
            if (asyncLive.isRunning()) {
                if (line == null || line.trim().isEmpty()) {
                    System.out.println("Please input something!");
                    continue;
                }
                if (line.startsWith("send_audio")) {
                    String[] parts = line.split(" ", 2);
                    if (parts.length == 2) {
                        File audioFile = new File(parts[1]);
                        if (audioFile.exists() && audioFile.isFile()) {
                            asyncLive.send("Please transcript the audio:", null, audioFile);
                            // Wait for a response.
                            messageHandler.await();
                            // Print the response.
                            System.out.println("Gemini Response: " + messageHandler.getLatestResponse());
                            // reset the latch
                            messageHandler.resetLatch();
                        } else {
                            System.out.println("Invalid audio file path.");
                        }
                    } else {
                        System.out.println("Invalid command. Usage: send_audio [file_path]");
                    }
                } else {
                    asyncLive.send(line);
                    // Wait for a response.
                    messageHandler.await();
                    // Print the response.
                    System.out.println("Gemini Response: " + messageHandler.getLatestResponse());
                    // reset the latch
                    messageHandler.resetLatch();
                }
            }
            System.out.println("Enter text input (type 'exit' to quit, type 'send_audio [file_path]' to send audio):");
        }
    }

}
