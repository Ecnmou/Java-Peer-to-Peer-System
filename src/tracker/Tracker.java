package tracker;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import model.*;
import messaging.*;
import peer.PeerSettings;


public class Tracker {

    // All shared state lives in thread-safe maps, because each client connection runs on its own thread.
    private final Map<String, Account> registeredAccounts = new ConcurrentHashMap<>();
    private final Map<String, ContactInfo> activePeers = new ConcurrentHashMap<>();
    private final Map<String, List<ContactInfo>> fileOwners = new ConcurrentHashMap<>();

    // Flag used by the server loop to know when to stop accepting new connections.
    // volatile: ensure visibility of changes to variables across multiple threads.
    private volatile boolean exiting = false;

    // Start the blocking server loop on a background thread so main can remain responsive.
    public void startAsync() {
        new Thread(this::start).start();
    }

    public void stop() {
        exiting = true;
        System.out.println("Tracker stopping...");
    }

    private void start() {
        // try-with-resources ensures the ServerSocket is closed when this method exits.
        try (ServerSocket serverSocket = new ServerSocket(PeerSettings.trackerPort)) {
            System.out.println("Tracker listening on port " + PeerSettings.trackerPort);

            // Main server loop: accept connections until "exiting" is set to true.
            while (!exiting) {
                System.out.println("\nWaiting for connection...");
                Socket connection = serverSocket.accept();
                // Handle each client on its own thread so multiple clients can talk to the tracker in parallel.
                new Thread(() -> handleConnection(connection)).start();
            }
        } catch (IOException e) {
            System.err.println("Tracker error:");
            e.printStackTrace();
        }
    }


    private void handleConnection(Socket connection) {
        // Ensure streams and socket are closed even if an exception happens.
        try (ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(connection.getInputStream())) {

            InetAddress remoteAddress = connection.getInetAddress();                    // IP address as an object
            System.out.println("New connection from " + remoteAddress.getHostAddress());// IP address as a string

            // Receive one Message from the client.
            Message request = (Message) in.readObject();
            
            // Trust the socket's remote address as the client IP.
            // More reliable than what client might send itself(value under clients control, might be wrong)
            // Socket's remote address is what the OS sees as the peer’s IP.
            request.clientIpAddress = remoteAddress.getHostAddress();

            System.out.println("Received: " + request.type);

            // Route to the appropriate handler.
            Message response = process(request);

            // Send back the response and close the connection.
            out.writeObject(response);
            out.flush();

            System.out.println("Request processed. Closing connection.\n");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error handling tracker connection:");
            e.printStackTrace();
        }
    }


    private Message process(Message request) {

        return switch (request.type) {

            case REGISTRATION_REQUEST -> handleRegistration(request);
            case LOGIN_REQUEST        -> handleLogin(request);
            case LOGOUT_REQUEST       -> handleLogout(request);
            case INFORM_REQUEST       -> handleInform(request);
            case LIST_REQUEST         -> handleList();
            case DETAILS_REQUEST      -> handleDetails(request);
            case NOTIFY_REQUEST       -> handleNotify(request);

            // In this simple design, all other types are considered unsupported at the tracker.
            default -> {
                Message m = new Message(MessageType.ERROR_RESPONSE);
                m.statusCode = StatusCode.ERROR;
                m.statusDescription = "Unsupported message type: " + request.type;
                yield m;
            }
        };
    }


    private Message handleRegistration(Message req) {

        Message resp = new Message(MessageType.REGISTRATION_RESPONSE);

        // Usernames are case-insensitive: store in a normalized (lowercase) form.
        String usernameKey = req.username.toLowerCase(Locale.ROOT); // locale‑independent way, avoid language behavior
        if (registeredAccounts.containsKey(usernameKey)) {
            resp.statusCode = StatusCode.ERROR;
            resp.statusDescription = "Username already exists. Please pick another.";
            return resp;
        }

        Account account = new Account(req.username, req.password);
        registeredAccounts.put(usernameKey, account);

        resp.statusCode = StatusCode.SUCCESS;
        resp.statusDescription = "Registration completed successfully.";
        return resp;
    }


    private Message handleLogin(Message req) {

        Message resp = new Message(MessageType.LOGIN_RESPONSE);

        String usernameKey = req.username.toLowerCase(Locale.ROOT);
        Account account = registeredAccounts.get(usernameKey);
        // Validate both username and password before creating a session.
        if (account == null || !account.passwordMatches(req.password)) {
            resp.statusCode = StatusCode.ERROR;
            resp.statusDescription = "Invalid username or password.";
            return resp;
        }

        // Generate a random session token and ensure it is unique among active peers.
        String tokenId;
        Random random = new Random();
        do {
            tokenId = String.valueOf(random.nextInt(1_000_000_000));
        } while (activePeers.containsKey(tokenId));

        // Remember where this peer is reachable (IP + port) and which account it belongs to.
        ContactInfo info = new ContactInfo(account, tokenId, req.clientIpAddress, req.clientPort);
        activePeers.put(tokenId, info);

        resp.tokenId = tokenId;
        resp.statusCode = StatusCode.SUCCESS;
        resp.statusDescription = "Login successful. Session token: " + tokenId;
        return resp;
    }


    private Message handleLogout(Message req) {

        Message resp = new Message(MessageType.LOGOUT_RESPONSE);

        // Remove session; if token is unknown, report an error.
        ContactInfo info = activePeers.remove(req.tokenId);
        if (info == null) {
            resp.statusCode = StatusCode.ERROR;
            resp.statusDescription = "Invalid token.";
            return resp;
        }

        // Also remove this peer from all file owner lists it belonged to.
        for (String filename : info.getSharedFiles()) {
            List<ContactInfo> owners = fileOwners.get(filename);
            if (owners != null) {
                owners.remove(info);
            }
        }

        resp.statusCode = StatusCode.SUCCESS;
        resp.statusDescription = "Logout successful.";
        return resp;
    }


    private Message handleInform(Message req) {

        Message resp = new Message(MessageType.INFORM_RESPONSE);

        ContactInfo info = activePeers.get(req.tokenId);
        if (info == null) {
            resp.statusCode = StatusCode.ERROR;
            resp.statusDescription = "Invalid token.";
            return resp;
        }

        // Update the peer's current listening port and the set of files it is sharing.
        info.setPort(req.clientPort);
        info.setSharedFiles(req.clientInformFiles);

        // For each shared file, add this peer to the list of owners for that filename.
        for (String filename : req.clientInformFiles) {
            // computeIfAbsent + synchronizedList ensures multiple threads can safely add owners.
            fileOwners
                // if there is no list, create one(lambda expression)
                .computeIfAbsent(filename, k -> Collections.synchronizedList(new ArrayList<>())) 
                .add(info);
        }

        resp.statusCode = StatusCode.SUCCESS;
        resp.statusDescription = "Tracker updated with your shared files.";
        return resp;
    }


    private Message handleList() {

        Message resp = new Message(MessageType.LIST_RESPONSE);

        // Return only filenames that currently have at least one owner.
        for (Map.Entry<String, List<ContactInfo>> entry : fileOwners.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                resp.availableFileNames.add(entry.getKey());
            }
        }

        resp.statusCode = StatusCode.SUCCESS;
        return resp;
    }


    // Ask a peer directly if it is still alive; treat any failure as "not active".
    private boolean isPeerActive(ContactInfo info) {
        Message check = new Message(MessageType.CHECK_ACTIVE_REQUEST);
        Message response = sendToPeer(info.getIp(), info.getPort(), check);
        return response != null && response.statusCode == StatusCode.SUCCESS;
    }


    private Message handleDetails(Message req) {

        Message resp = new Message(MessageType.DETAILS_RESPONSE);
        resp.statusCode = StatusCode.SUCCESS;
        resp.statusDescription = "Users exposing " + req.filename + ":";

        List<ContactInfo> owners = fileOwners.get(req.filename);
        if (owners == null || owners.isEmpty()) {
            resp.statusCode = StatusCode.ERROR;
            resp.statusDescription = "No peers currently expose " + req.filename;
            return resp;
        }

        List<ContactInfoDTO> active = new ArrayList<>();
        // Use an iterator so we can safely remove dead peers while walking the list.
        Iterator<ContactInfo> it = owners.iterator();
        while (it.hasNext()) {
            ContactInfo info = it.next();
            if (isPeerActive(info)) {
                // Convert live peers into DTOs that can be serialized back to the client.
                active.add(info.toDTO());
            } else {
                // If the peer no longer responds, clean it from both fileOwners and activePeers.
                it.remove();
                activePeers.remove(info.getTokenId());
            }
        }

        if (active.isEmpty()) {
            resp.statusCode = StatusCode.ERROR;
            resp.statusDescription = "All exposers of " + req.filename + " seem to be offline.";
        }

        resp.users = active;
        return resp;
    }


    private Message handleNotify(Message req) {

        Message resp = new Message(MessageType.NOTIFY_RESPONSE);
        
        // Owner's/Exposer's username
        String foreignUserKey = req.foreignUsername.toLowerCase(Locale.ROOT);
        Account account = registeredAccounts.get(foreignUserKey);
        if (account == null) {
            resp.statusCode = StatusCode.ERROR;
            resp.statusDescription = "Unknown user " + req.foreignUsername;
            return resp;
        }

        // If the download succeeded, increase the exposer's "good" count and
        // also add the downloader as an exposer of that file (they now have a copy).
        if (req.statusCode == StatusCode.SUCCESS) {
            account.incrementDownloads();
            ContactInfo downloader = activePeers.get(req.tokenId);
            if (downloader != null) {
                fileOwners
                    .computeIfAbsent(req.filename, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(downloader);
            }
        } else {
            // Failed download counts as a "bad" event for the exposer.
            account.incrementFailures();
        }

        resp.statusCode = StatusCode.SUCCESS;
        resp.statusDescription = "Notification received.";
        return resp;
    }


    // Small helper: send a Message to a peer and wait for its response.
    private Message sendToPeer(String host, int port, Message request) {

        try (Socket socket = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject(request);
            out.flush();
            
            return (Message) in.readObject();

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error sending to peer " + host + ":" + port);
            // Returning null lets callers treat this as a generic "peer not active" case.
            return null;
        }
    }
}