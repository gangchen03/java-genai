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

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.glassfish.tyrus.client.ClientManager;
import org.apache.http.HttpException;
import org.apache.commons.codec.binary.Base64;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.AudioFormat;

/**
 * AsyncLive class for establishing and managing asynchronous
 * live sessions with Gemini API.
 */
public class AsyncLive {

    private final Client client;
    private final boolean isVertexAI;
    private String uriStr;
    private String modelName;
    private Session session;
    private WebSocketEndpoint clientEndpoint;
    private Map<String, Object> liveConfig;
    private boolean isRunning = true;
    private MessageHandler customMessageHandler;

    public AsyncLive(Client client, String modelName, Map<String, Object> liveConfig) throws IOException {
        this.client = client;
        this.modelName = modelName;
        this.liveConfig = liveConfig;
        this.isVertexAI = System.getenv("GOOGLE_API_KEY") == null;
        setupUri();
    }

    public void setCustomMessageHandler(MessageHandler messageHandler) {
        this.customMessageHandler = messageHandler;
        clientEndpoint.setMessageHandler(messageHandler);
        clientEndpoint.setConfig(liveConfig); // set the config to WebSocketEndpoint
    }

    public boolean isRunning() {
        return isRunning;
    }

    private void setupUri() {
        if (isVertexAI) {
            uriStr = "wss://us-central1-aiplatform.googleapis.com/ws/google.cloud.aiplatform.v1beta1.LlmBidiService/BidiGenerateContent";
        } else {
            uriStr = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService/BidiGenerateContent";
        }
    }

    /**
     * Connect to the live server.
     *
     * @throws URISyntaxException
     * @throws DeploymentException
     * @throws IOException
     * @throws InterruptedException
     * @throws HttpException
     */
    public void connect()
            throws URISyntaxException, DeploymentException, IOException, InterruptedException, HttpException {
        clientEndpoint = new WebSocketEndpoint();

        Map<String, List<String>> headers = new HashMap<>();
        AccessToken token = null;
        if (isVertexAI) {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            credentials.refreshIfExpired();
            token = credentials.getAccessToken();
            headers.put("Authorization", ImmutableList.of("Bearer " + token.getTokenValue()));
            // append access token to the wss link
            // to-do: pass bearer as HTTP header
            StringBuilder sb = new StringBuilder(uriStr);
            sb.append("?access_token=");
            sb.append(token.getTokenValue());
            uriStr = sb.toString();

        }

        ClientManager clientManager = (ClientManager) ClientManager.createClient();
        WebSocketContainer container = clientManager;
        session = container.connectToServer(clientEndpoint, new URI(uriStr));

        // Send the session configuration as the first message.
        String sessionConfigMessage = createSessionConfigMessage(modelName, liveConfig);
        session.getBasicRemote().sendText(sessionConfigMessage);
        // Wait for the server response after sending the configuration
        clientEndpoint.await();
    }

    /**
     * Sends a message to the live server.
     *
     * @param message The message to send.
     * @throws IOException
     * @throws InterruptedException
     */
    public void send(String message) throws IOException, InterruptedException {
        send(message, null, null);
    }

    /**
     * Sends a message and image file to live server
     *
     * @param message   The message to send.
     * @param imageFile the image file path
     * @throws IOException
     * @throws InterruptedException
     */
    public void send(String message, File imageFile) throws IOException, InterruptedException {
        send(message, imageFile, null);
    }

    /**
     * Sends a message and audio file to live server
     *
     * @param message   The message to send.
     * @param audioFile the audio file path
     * @throws IOException
     * @throws InterruptedException
     */
    public void send(String message, File imageFile, File audioFile) throws IOException, InterruptedException {
        String formattedMessage = "";

        if (imageFile == null && audioFile == null) {
            formattedMessage = String.format(
                    "{" +
                            "\"client_content\": {" +
                            "\"turns\": [" +
                            "{" +
                            "\"role\": \"user\"," +
                            "\"parts\": [{\"text\": \"%s\"}]" +
                            "}" +
                            "]," +
                            "\"turn_complete\": true" +
                            "}" +
                            "}",
                    message);
        } else if (imageFile != null) {
            System.out.println("Sending image: " + imageFile.getName());
            String base64Image = encodeImageFileToBase64(imageFile);
            String mime_type = "image/jpeg";

            formattedMessage = String.format(
                    "{" +
                            "\"realtime_input\":{" +
                            "\"media_chunks\": [" +
                            "{" +
                            "\"mime_type\": \"%s\"," +
                            "\"data\": \"%s\"" +
                            "}" +
                            "]" +
                            "}" +
                            "}",
                    mime_type, base64Image);

        } else if (audioFile != null) {
            System.out.println("Sending Audio input " + audioFile.getName());
            String base64Audio = "";
            try {
                base64Audio = encodeAudioFileToBase64PCM(audioFile);
            } catch (UnsupportedAudioFileException e) {
                e.printStackTrace();
            }

            String mime_type = "audio/pcm";
            formattedMessage = String.format(
                    "{" +
                            "\"realtime_input\":{" +
                            "\"media_chunks\": [" +
                            "{" +
                            "\"mime_type\": \"%s\"," +
                            "\"data\": \"%s\"" +
                            "}" +
                            "]" +
                            "}" +
                            "}",
                    mime_type, base64Audio);
        }

        session.getBasicRemote().sendText(formattedMessage);

        // reset the CountDownLatch
        // clientEndpoint.resetLatch();

        // Wait for a response (or timeout).
        clientEndpoint.await();
    }

    /**
     * Closes the connection to the live server.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        if (isRunning) {
            session.close();
            // Save the audio to file before exit
            clientEndpoint.saveAllAudioToFile();
            isRunning = false;
        }

    }

    private String encodeImageFileToBase64(File imageFile) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(imageFile)) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            byte[] imageBytes = byteArrayOutputStream.toByteArray();
            return Base64.encodeBase64String(imageBytes);
        }
    }

    private String encodeAudioFileToBase64PCM(File audioFile)
            throws IOException, InterruptedException, UnsupportedAudioFileException {
        // Read the audio file
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioFile);
        AudioFormat baseFormat = audioInputStream.getFormat();
        // Define the desired PCM format (linear PCM, 16-bit, mono, 24kHz,
        // Little-Endian)
        AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, // Encoding
                24000, // Sample rate (Hz)
                16, // Sample size in bits
                1, // Number of channels (mono)
                2, // Frame size (bytes)
                24000, // Frame rate (Hz)
                false // Little-Endian
        );

        // Convert audio to the desired PCM format
        AudioInputStream convertedAudioInputStream = AudioSystem.getAudioInputStream(targetFormat, audioInputStream);

        // Read the converted audio data into a byte array
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = convertedAudioInputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }
        byte[] pcmData = byteArrayOutputStream.toByteArray();

        // Encode the PCM data to Base64
        return Base64.encodeBase64String(pcmData);
    }

    private String getMimeType(File file) {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else {
            return "application/octet-stream"; // Default to binary data if type is unknown
        }
    }

    /**
     * Creates the session configuration message in JSON format.
     *
     * @param modelName The name of the model to use.
     * @param config    The live configuration
     * @return The JSON string representing the session configuration.
     */
    private String createSessionConfigMessage(String modelName, Map<String, Object> config) {
        // Customize the session configuration as needed.
        // This is a basic example, but you can add more options.
        Map<String, Object> message = new HashMap<>();
        Map<String, Object> setup = new HashMap<>();
        // message.put("model", modelName);
        if (isVertexAI) {
            setup.put("model", String.format("projects/%s/locations/%s/publishers/google/models/%s",
                    System.getenv("GOOGLE_CLOUD_PROJECT"),
                    System.getenv("GOOGLE_CLOUD_LOCATION"),
                    modelName));

        } else {
            setup.put("model", modelName);
        }

        Map<String, Object> generationConfig = new HashMap<>();
        if (config.containsKey("generation_config")) {
            generationConfig.putAll((Map<String, Object>) config.get("generation_config"));
        }

        // set default value for responseModalities
        if (!generationConfig.containsKey("responseModalities")) {
            generationConfig.put("responseModalities", ImmutableList.of("TEXT"));
        }

        // Add speech config if response modalities are AUDIO
        if (generationConfig.get("responseModalities").equals(ImmutableList.of("AUDIO"))
                || generationConfig.get("responseModalities").equals(ImmutableList.of("AUDIO", "TEXT"))
                || generationConfig.get("responseModalities").equals(ImmutableList.of("TEXT", "AUDIO"))) {
            Map<String, Object> voiceConfig = new HashMap<>();
            Map<String, Object> prebuiltVoiceConfig = new HashMap<>();
            prebuiltVoiceConfig.put("voiceName", "Aoede"); // or other voiceName
            voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig);

            Map<String, Object> speechConfig = new HashMap<>();
            speechConfig.put("voiceConfig", voiceConfig);
            // Add speech config to generation config
            generationConfig.put("speechConfig", speechConfig);
        }

        setup.put("generationConfig", generationConfig);
        message.put("setup", setup);

        // Convert the map to a JSON string
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(message);
    }

    /**
     * WebSocketEndpoint class for handling WebSocket connection and messages.
     */
    @ClientEndpoint
    public static class WebSocketEndpoint {
        private CountDownLatch messageLatch = new CountDownLatch(1);
        private ByteArrayOutputStream audioData = new ByteArrayOutputStream();
        // to-do: Audio handling should be moved out to the MessageHandler class
        private final String audioFilename = "combined_audio.wav";
        private MessageHandler messageHandler;
        private Map<String, Object> config;
        // Define the desired audio format (PCM, 16-bit, mono, 24kHz)
        private static final AudioFormat AUDIO_FORMAT = new AudioFormat(24000, 16, 1, true, false);

        public void setMessageHandler(MessageHandler messageHandler) {
            this.messageHandler = messageHandler;
        }

        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }

        @OnOpen
        public void onOpen(Session session) {
            System.out.println("WebSocket opened: " + session.getId());
        }

        @OnMessage
        public void onMessage(ByteBuffer message, Session session) {
            System.out.println("Received binary message of size: " + message.remaining());

            List<String> responseModalities = ImmutableList.of("TEXT"); // Default to "TEXT"
            if (config != null) {
                Map<String, Object> generationConfig = (Map<String, Object>) config.get("generation_config");
                if (generationConfig != null && generationConfig.containsKey("responseModalities")) {
                    responseModalities = (List<String>) generationConfig.get("responseModalities");
                }
            }

            // TO-DO: Move the audio handling logic outside of the AysnLive class
            if (responseModalities.contains("TEXT") && messageHandler != null) {
                messageHandler.onMessage(message);
            }

            // Try to extract data from serverContent
            try {
                // Convert ByteBuffer to String
                String receivedMessage = StandardCharsets.UTF_8.decode(message).toString();

                // try to parse the server content.
                JsonElement jsonElement = JsonParser.parseString(receivedMessage);
                JsonObject jsonObject = jsonElement.getAsJsonObject();

                if (responseModalities.contains("TEXT")) {
                    if (jsonObject.has("serverContent")) {
                        JsonObject serverContent = jsonObject.getAsJsonObject("serverContent");
                        if (serverContent.has("modelTurn")) {
                            JsonObject modelTurn = serverContent.getAsJsonObject("modelTurn");
                            if (modelTurn.has("parts")) {
                                com.google.gson.JsonArray parts = modelTurn.getAsJsonArray("parts");
                                for (JsonElement part : parts) {
                                    JsonObject partObject = part.getAsJsonObject();
                                    if (partObject.has("text")) {
                                        String text = partObject.get("text").getAsString();
                                        System.out.println("Received message: " + text);
                                    }
                                }
                            }
                        }
                    }
                // if it's a audio message, save the audio data to buffer.    
                } else if (responseModalities.contains("AUDIO")) {
                    System.out.println("Onmessage handle audio response data");
                    if (jsonObject.has("serverContent")) {
                        JsonObject serverContent = jsonObject.getAsJsonObject("serverContent");
                        if (serverContent.has("modelTurn")) {
                            JsonObject modelTurn = serverContent.getAsJsonObject("modelTurn");
                            if (modelTurn.has("parts")) {
                                com.google.gson.JsonArray parts = modelTurn.getAsJsonArray("parts");
                                for (JsonElement part : parts) {
                                    JsonObject partObject = part.getAsJsonObject();
                                    if (partObject.has("inlineData")) {
                                        JsonObject inlineData = partObject.getAsJsonObject("inlineData");
                                        if (inlineData.has("data")) {
                                            String base64Audio = inlineData.get("data").getAsString();
                                            byte[] audioBytes = Base64.decodeBase64(base64Audio);
                                            audioData.write(audioBytes);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {
                System.err.println("Error processing binary message as text: " + e.getMessage());
            }
            messageLatch.countDown();
        }

        public void saveAllAudioToFile() {
            // save the audio to file
            byte[] audioBytes = audioData.toByteArray();
            if (audioBytes.length == 0)
                return; // Exit early if no audio data

            try (ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
                    AudioInputStream audioInputStream = new AudioInputStream(bais, AUDIO_FORMAT,
                            audioBytes.length / AUDIO_FORMAT.getFrameSize());
                    FileOutputStream fos = new FileOutputStream(audioFilename)) {

                AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, fos);
                System.out.println("Saved audio data to: " + audioFilename);

            } catch (Exception e) {
                System.err.println("Error saving audio data: " + e.getMessage());
            }
        }

        @OnClose
        public void onClose(Session session, CloseReason closeReason) {
            System.out.println("WebSocket closed: " + closeReason);
        }

        public void await() throws InterruptedException {
            messageLatch.await(60, TimeUnit.SECONDS);
        }

        public void resetLatch() {
            messageLatch = new CountDownLatch(1);
        }
    }

    /**
     * A custom message handler class for receiving and processing messages.
     * This is just a reference implementation
     */
    public static class MessageHandler {
        private String latestResponse = "";
        private CountDownLatch responseLatch = new CountDownLatch(0);

        public void onMessage(String message) {
            // Try to extract text from serverContent
            try {
                JsonElement jsonElement = JsonParser.parseString(message);
                JsonObject jsonObject = jsonElement.getAsJsonObject();

                if (jsonObject.has("serverContent")) {
                    JsonObject serverContent = jsonObject.getAsJsonObject("serverContent");
                    if (serverContent.has("modelTurn")) {
                        JsonObject modelTurn = serverContent.getAsJsonObject("modelTurn");
                        if (modelTurn.has("parts")) {
                            com.google.gson.JsonArray parts = modelTurn.getAsJsonArray("parts");
                            for (JsonElement part : parts) {
                                JsonObject partObject = part.getAsJsonObject();
                                if (partObject.has("text")) {
                                    String text = partObject.get("text").getAsString();
                                    latestResponse = text;
                                    System.out.println("Received message: " + text);
                                    // count up when receive one text message from Gemini
                                    responseLatch.countDown();

                                }
                            }
                        }

                    }
                } else {
                    latestResponse = message;
                    System.out.println("Received text message: " + message);
                    // count up when receive one text message from Gemini
                    responseLatch.countDown();
                }

            } catch (Exception e) {
                latestResponse = message;
                System.out.println("Received text message: " + message);
                System.out.println("Error processing JSON: " + e.getMessage());
                // count up when receive one text message from Gemini
                responseLatch.countDown();
            }

        }

        // Handle the audio response
        public void onMessage(ByteBuffer message) {    
            System.out.println("Received Gemini message");
        }

        public String getLatestResponse() {
            return latestResponse;
        }

        public void await() throws InterruptedException {
            responseLatch.await(60, TimeUnit.SECONDS);
        }

        public void resetLatch() {
            // responseLatch = new CountDownLatch(0);
            responseLatch = new CountDownLatch(1);
            latestResponse = ""; // reset the lastestResponse
        }
    }

}
