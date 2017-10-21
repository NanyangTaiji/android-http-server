/**************************************************
 * Android Web Server
 * Based on JavaLittleWebServer (2008)
 * <p/>
 * Copyright (c) Piotr Polak 2008-2016
 **************************************************/

package ro.polak.http;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

import ro.polak.http.utilities.IOUtilities;
import ro.polak.http.utilities.Utilities;

/**
 * Web server main class.
 *
 * @author Piotr Polak piotr [at] polak [dot] ro
 * @since 200802
 */
public class WebServer extends Thread {

    private static final Logger LOGGER = Logger.getLogger(WebServer.class.getName());

    public static final String NAME = "AndroidHTTPServer";
    public static final String VERSION = "0.1.5-dev";
    public static final String SIGNATURE = NAME + "/" + VERSION;

    private final ServerSocket serverSocket;
    private final ServerConfig serverConfig;
    private ServiceContainer serviceContainer;

    private boolean listen;

    /**
     * @param serverSocket
     * @param serverConfig
     */
    public WebServer(final ServerSocket serverSocket, final ServerConfig serverConfig) {
        this.serverSocket = serverSocket;
        this.serverConfig = serverConfig;
    }

    @Override
    public void run() {
        serviceContainer = new ServiceContainer(serverConfig);

        try {
            while (listen) {
                try {
                    serviceContainer.getThreadPoolExecutor().execute(
                            new ServerRunnable(serverSocket.accept(),
                                    serverConfig,
                                    serviceContainer.getRequestWrapperFactory(),
                                    serviceContainer.getResponseFactory(),
                                    serviceContainer.getHttpErrorHandlerResolver()));
                } catch (IOException e) {
                    if (listen) {
                        LOGGER.log(Level.SEVERE, "Communication error", e);
                    }
                }
            }
        } finally {
            IOUtilities.closeSilently(serverSocket);
            serviceContainer.getThreadPoolExecutor().shutdown();
        }
    }

    /**
     * Starts the web server
     */
    public boolean startServer() {
        listen = true;

        if (!(new File(serverConfig.
                getDocumentRootPath())
                .isDirectory())) {
            LOGGER.log(Level.WARNING, "DocumentRoot does not exist! PATH {0}", new Object[]{
                    serverConfig.getDocumentRootPath()
            });
        }

        if (!isNumberOfThreadsSufficient() || !isTempPathWritable() || !bindSocket()) {
            listen = false;
            return false;
        }

        LOGGER.log(Level.INFO, "Server has been started. Listening on port {0}", new Object[]{
                serverConfig.getListenPort()
        });

        Utilities.clearDirectory(serverConfig.getTempPath());

        start();
        return true;
    }

    private boolean isNumberOfThreadsSufficient() {
        if (serverConfig.getMaxServerThreads() < 1) {
            LOGGER.log(Level.SEVERE, "MaxThreads should be greater or equal to 1! {0} is given.",
                    new Object[]{serverConfig.getMaxServerThreads()});
            return false;
        }
        return true;
    }

    private boolean bindSocket() {
        try {
            serverSocket.bind(new InetSocketAddress(serverConfig.getListenPort()));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to start server: unable to listen on port " +
                    serverConfig.getListenPort(), e);
            return false;
        }
        return true;
    }

    private boolean isTempPathWritable() {
        File tempPath = new File(serverConfig.getTempPath());

        if (!tempPath.exists()) {
            boolean successCreatedMissingDirectory = tempPath.mkdirs();
            if (!successCreatedMissingDirectory) {
                LOGGER.log(Level.SEVERE, "TempPath does not exist and can not be created! PATH {0}", new Object[]{
                        serverConfig.getTempPath()
                });
                return false;
            }
        }

        if (!tempPath.canWrite()) {
            LOGGER.log(Level.SEVERE, "TempPath is not writable! PATH {0}", new Object[]{
                    serverConfig.getTempPath()
            });
            return false;
        }

        return true;
    }

    /**
     * Stops the web server
     */
    public void stopServer() {
        listen = false;
        if (serverSocket != null) {
            IOUtilities.closeSilently(serverSocket);
        }
        LOGGER.info("Server has been stopped.");
    }

    /**
     * Tells whether the server is running or not
     *
     * @return
     */
    public boolean isRunning() {
        return listen;
    }

    /**
     * Returns server config
     *
     * @return
     */
    public ServerConfig getServerConfig() {
        return serverConfig;
    }
}
