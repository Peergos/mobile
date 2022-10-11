package org.peergos.sample;

import com.gluonhq.attach.storage.*;
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
import netscape.javascript.*;
import peergos.server.*;
import peergos.server.corenode.*;
import peergos.server.crypto.hash.*;
import peergos.server.login.*;
import peergos.server.mutable.*;
import peergos.server.sql.*;
import peergos.server.storage.*;
import peergos.server.storage.auth.*;
import peergos.server.util.*;
import peergos.shared.*;
import peergos.shared.corenode.*;
import peergos.shared.crypto.hash.*;
import peergos.shared.io.ipfs.cid.*;
import peergos.shared.io.ipfs.multihash.*;
import peergos.shared.mutable.*;
import peergos.shared.storage.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.function.*;

public class Main extends Application {

    private Scene scene;
    private Browser view;

    @Override
    public void start(Stage stage) {
        startServer();

        // create the scene
        stage.setTitle("Peergos");
        view = new Browser("http://localhost:8000/");
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

    public static NetworkAccess buildNetwork(String url, Hasher h) throws URISyntaxException, MalformedURLException {
        URL target = new URI(url).toURL();
        try {
            return Builder.buildJavaNetworkAccess(target, true).join();
        } catch (Exception e) {
            System.out.println("Using offline NetworkAccess");
            JavaPoster poster = new JavaPoster(target, true, Optional.empty());
            CoreNode directCore = NetworkAccess.buildDirectCorenode(poster);
            ContentAddressedStorage localDht = NetworkAccess.buildLocalDht(poster, true, h);
            Cid peergosdotnet = Cid.decode("QmcoDbhCiVXGrWs6rwBvB59Gm44veo7Qxn2zmRnPw7BaCH");
            return NetworkAccess.buildToPeergosServer(peergosdotnet, directCore, localDht, poster, poster, 7000, h, Collections.emptyList(), false);
        }
    }

    public static void startServer() {
        try {
            Crypto crypto = Builder.initCrypto();
            NetworkAccess net = buildNetwork("https://peergos.net", crypto.hasher);

            System.out.println("SQLITE library present: " + (null != peergos.server.Main.class.getResourceAsStream("/org/sqlite/native/Linux-Android/aarch64/libsqlitejdbc.so")));
            File privateStorage = StorageService.create()
                    .flatMap(StorageService::getPrivateStorage)
                    .orElseThrow(() -> new FileNotFoundException("Could not access private app storage."));
            Path peergosDir = Paths.get(privateStorage.getAbsolutePath());
            System.out.println("Peergos using private storage dir: " + peergosDir);
            if (com.gluonhq.attach.util.Platform.isAndroid()) {
                // make sure sqlite loads correct shared library on Android
                System.out.println("Initial runtime name: " + System.getProperty("java.runtime.name", ""));
                System.setProperty("java.runtime.name", "android");
                System.out.println("Updated runtime name: " + System.getProperty("java.runtime.name", ""));
            }

            Args a = Args.parse(new String[]{
                    "PEERGOS_PATH", peergosDir.toString(),
                    "-mutable-pointers-cache", "pointer-cache.sql",
                    "-account-cache-sql-file", "account-cache.sql",
                    "-pki-cache-sql-file", "pki-cache.sql",
                    "-bat-cache-sql-file", "bat-cache.sql"
            });
            FileBlockCache blockCache = new FileBlockCache(peergosDir.resolve(Paths.get("blocks", "cache")),
                    10*1024*1024*1024L);
            ContentAddressedStorage locallyCachedStorage = new UnauthedCachingStorage(net.dhtClient, blockCache);
            DirectOnlyStorage withoutS3 = new DirectOnlyStorage(locallyCachedStorage);

            Supplier<Connection> dbConnector = Builder.getDBConnector(a, "mutable-pointers-cache");
            JdbcIpnsAndSocial rawPointers = Builder.buildRawPointers(a, dbConnector);
            OfflinePointerCache pointerCache = new OfflinePointerCache(net.mutable, new JdbcPointerCache(rawPointers, locallyCachedStorage));

            SqlSupplier commands = Builder.getSqlCommands(a);
            OfflineCorenode offlineCorenode = new OfflineCorenode(net.coreNode, new JdbcPkiCache(Builder.getDBConnector(a, "pki-cache-sql-file", dbConnector), commands));

            JdbcAccount localAccount = new JdbcAccount(Builder.getDBConnector(a, "account-cache-sql-file", dbConnector), commands);
            OfflineAccountStore offlineAccounts = new OfflineAccountStore(net.account, localAccount);

            OfflineBatCache offlineBats = new OfflineBatCache(net.batCave, new JdbcBatCave(Builder.getDBConnector(a, "bat-cache-sql-file", dbConnector), commands));

            UserService server = new UserService(withoutS3, offlineBats, crypto, offlineCorenode, offlineAccounts,
                    net.social, pointerCache, net.instanceAdmin, net.spaceUsage, net.serverMessager, null);

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

    public static class JavaBridge {
        public void log(String text) {
            Logging.LOG().info(text);
        }
    }

    static class Browser extends Region {

        final WebView browser = new WebView();
        final WebEngine webEngine = browser.getEngine();
        private final JavaBridge bridge = new JavaBridge();

        public Browser(String url) {
            //apply the styles
            getStyleClass().add("Peergos");

            webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) ->
            {
                JSObject window = (JSObject) webEngine.executeScript("window");
                if (window == null)
                    return;
                window.setMember("java", bridge);
                webEngine.executeScript("console.log = function(message) {\n" +
                        "    java.log(message);\n" +
                        "};");
            });

            //add the web view to the scene
            getChildren().add(browser);
            webEngine.load(url);
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
