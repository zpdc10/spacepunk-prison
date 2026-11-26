package dev.spacepunk.prison.resourcepack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Tiny HTTP server that hands out the current pack zip. Any GET path ending in ".zip" gets the pack, so the
 * hash in the URL only busts client caches. Runs on virtual threads, never touches Bukkit.
 */
final class PackHttpServer {
    private final Logger log;
    private final HttpServer server;
    private final ExecutorService executor;
    private volatile PackFile pack;

    PackHttpServer(Logger log, String bind, int port, PackFile pack) throws IOException {
        this.log = log;
        this.pack = pack;
        InetSocketAddress addr = bind == null || bind.isBlank() ? new InetSocketAddress(port) : new InetSocketAddress(bind, port);
        this.server = HttpServer.create(addr, 16);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    void start() { server.start(); }

    void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    void setPack(PackFile pack) { this.pack = pack; }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            if (!path.endsWith(".zip")) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            PackFile p = pack;
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.getResponseHeaders().set("ETag", "\"" + p.sha1() + "\"");
            if (method.equals("HEAD")) {
                ex.getResponseHeaders().set("Content-Length", Integer.toString(p.bytes().length));
                ex.sendResponseHeaders(200, -1);
                return;
            }
            ex.sendResponseHeaders(200, p.bytes().length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(p.bytes());
            }
        } catch (IOException e) {
            log.fine("Pack download aborted: " + e.getMessage());
        } finally {
            ex.close();
        }
    }
}
