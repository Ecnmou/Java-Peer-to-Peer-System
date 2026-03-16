package peer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.nio.file.Files;

import messaging.*;


public class PeerListener {

    public void start() {
        // Each peer runs a small server so other peers (and the tracker) can:
        // - check if it's alive (CHECK_ACTIVE_REQUEST)
        // - download a file (SIMPLE_DOWNLOAD_REQUEST)
        try (ServerSocket serverSocket = new ServerSocket(PeerSettings.peerServerPort)) {

            System.out.println("Peer listener on port " + PeerSettings.peerServerPort);

            while (true) {
                System.out.println("\nWaiting for connection...");
                Socket connection = serverSocket.accept();

                // Handle each incoming connection concurrently.
                new Thread(() -> handleConnection(connection)).start();
            }
        } catch (IOException e) {
            System.err.println("Peer listener error:");
            e.printStackTrace();
        }
    }


    private Message handleCheckActive() {
        // Minimal response: if we can respond, we're alive.
        Message resp = new Message(MessageType.CHECK_ACTIVE_RESPONSE);
        resp.statusCode = StatusCode.SUCCESS;
        resp.statusDescription = "I am active.";
        return resp;
    }


    private Message handleDownload(Message req) {

        // Reads the requested file from this peer's shared folder and sends bytes back.
        // This is a "simple" download: the entire file is loaded into memory at once.
        Message resp = new Message(MessageType.SIMPLE_DOWNLOAD_RESPONSE);
        resp.filename = req.filename;

        File file = new File(PeerSettings.sharedDirectoryPath + File.separator + req.filename);
        try {
            resp.fileData = Files.readAllBytes(file.toPath());
            resp.statusCode = StatusCode.SUCCESS;
        } catch (IOException e) {
            // On failure we return ERROR and no fileData.
            // Caller should interpret missing fileData as a failed download.
            resp.statusCode = StatusCode.ERROR;
            resp.statusDescription = "Failed to read file.";
        }

        return resp;
    }

    
    private void handleConnection(Socket connection) {

        // try-with-resources ensures the socket and streams are closed even on exception.
        try (ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(connection.getInputStream())) {

            InetAddress remote = connection.getInetAddress();
            System.out.println("New connection from " + remote.getHostAddress());

            // Read exactly one request message and respond once, then close.
            Message req = (Message) in.readObject();

            // Trust the socket's remote address more than any client-provided value.
            req.clientIpAddress = remote.getHostAddress();

            // This port is the ephemeral port of the remote side of THIS connection.
            // It's NOT the peer's listening port; it's mostly useful for logging/debug.
            req.clientPort = connection.getPort();

            System.out.println("Peer request type: " + req.type);

            Message resp;

            // Only two peer-to-peer operations are supported here.
            switch (req.type) {
                case SIMPLE_DOWNLOAD_REQUEST -> resp = handleDownload(req);
                case CHECK_ACTIVE_REQUEST    -> resp = handleCheckActive();
                default -> {
                    // Protocol-level error: requester sent something this peer server doesn't support.
                    resp = new Message(MessageType.ERROR_RESPONSE);
                    resp.statusCode = StatusCode.ERROR;
                    resp.statusDescription = "Unsupported message type.";
                }
            }

            out.writeObject(resp);
            out.flush(); // push bytes out before the try-with-resources closes the stream/socket
            System.out.println("Peer request handled.\n");

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error in peer listener connection:");
            e.printStackTrace();
        }
    }
}