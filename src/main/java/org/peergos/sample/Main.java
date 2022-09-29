package org.peergos.sample;

import javafx.application.*;
import javafx.collections.*;
import javafx.scene.Scene;
import javafx.scene.input.*;
import javafx.scene.web.*;
import javafx.stage.Stage;

import javafx.application.Application;
import javafx.geometry.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import peergos.server.*;
import peergos.server.messages.*;
import peergos.server.storage.*;
import peergos.shared.*;
import peergos.shared.io.ipfs.cid.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Main extends Application {

    private Scene scene;
    private Browser view;

    @Override
    public void start(Stage stage) {
        startServer();

        // create the scene
        stage.setTitle("Peergos");
        view = new Browser();
        scene = new Scene(view, 900, 700, Color.web("#666970"));
        scene.addEventHandler(KeyEvent.KEY_RELEASED, e -> {
            if (KeyCode.ESCAPE.equals(e.getCode())) {
                if (com.gluonhq.attach.util.Platform.isAndroid()) {
                    view.goBack();
                    e.consume();
                }
            }});
        stage.setScene(scene);
        stage.show();
    }

    public void startServer() {
        try {
            Crypto crypto = Builder.initCrypto();
            NetworkAccess net = Builder.buildJavaNetworkAccess(new URI("https://peergos.net").toURL(), true).join();
            ServerMessageStore serverMessages = null; // TODO
            UserService server = new UserService(net.dhtClient, net.batCave, crypto, net.coreNode, net.account,
                    net.social, net.mutable, net.instanceAdmin, net.spaceUsage, serverMessages, null);

            InetSocketAddress localAPIAddress = new InetSocketAddress("localhost", 8000);
            List<String> appSubdomains = Arrays.asList("markdown-viewer,email,calendar,todo-board,code-editor,pdf".split(","));
            Cid nodeId = net.dhtClient.id().join();
            int connectionBacklog = 50;
            int handlerPoolSize = 4;
            server.initAndStart(localAPIAddress, nodeId, Optional.empty(), Optional.empty(),
                    Collections.emptyList(), Collections.emptyList(), appSubdomains, true,
                    Optional.empty(), Optional.empty(), Optional.empty(), true, false,
                    connectionBacklog, handlerPoolSize);
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    static class Browser extends Region {

        final WebView browser = new WebView();
        final WebEngine webEngine = browser.getEngine();

        public Browser() {
            //apply the styles
            getStyleClass().add("Peergos");
            // load the web page
            webEngine.load("http://localhost:8000/");
            //add the web view to the scene
            getChildren().add(browser);
        }

        public void goBack() {
            final WebHistory history = webEngine.getHistory();
            ObservableList<WebHistory.Entry> entryList = history.getEntries();
            int currentIndex = history.getCurrentIndex();

            if (entryList.size() > 1 && currentIndex > 0)
            Platform.runLater(() -> history.go(-1));
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double h = getHeight();
            layoutInArea(browser, 0, 0, w, h, 0, HPos.CENTER, VPos.CENTER);
        }

        @Override
        protected double computePrefWidth(double height) {
            return 900;
        }

        @Override
        protected double computePrefHeight(double width) {
            return 600;
        }
    }
}
