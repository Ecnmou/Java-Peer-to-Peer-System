package peer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.io.File;
import java.util.Comparator;
import java.util.List;

import messaging.*;
import model.ContactInfoDTO;


public class Peer {

    // Token for the current login session; assigned after a successful login.
    private String tokenId;


    private Message sendToTracker(Message request) {
        // The tracker endpoint is always PeerSettings.trackerIP/Port.
        return send(request, PeerSettings.trackerIP, PeerSettings.trackerPort);
    }

    // Core helper: open a TCP connection, send a Message, wait for a Message response.
    private Message send(Message request, String host, int port) {
        // Let the other side know what port THIS peer listens on.
        // The tracker uses this when it wants to contact the peer (active checks),
        // and other peers use it to download files.
        request.clientPort = PeerSettings.peerServerPort;

        // try-with-resources closes socket and streams automatically.
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject(request);
            out.flush(); // ensure bytes are pushed out before we wait for the response

            return (Message) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            // Any network failure returns null; callers must treat null as an error.
            System.err.println("Network error:");
            e.printStackTrace();
            return null;
        }
    }


    public Message register(String username, String password) {
        Message req = new Message(MessageType.REGISTRATION_REQUEST);
        req.username = username;
        req.password = password;
        return sendToTracker(req);
    }


    public Message login(String username, String password) {
        Message req = new Message(MessageType.LOGIN_REQUEST);
        req.username = username;
        req.password = password;
        req.clientPort = PeerSettings.peerServerPort;

        Message resp = sendToTracker(req);

        // Save the session token locally so we can use it for inform/logout/notify later.
        if (resp != null && resp.statusCode == StatusCode.SUCCESS) {
            tokenId = resp.tokenId;
        }
        return resp;
    }


    public Message logout() {
        // Uses the stored tokenId; this assumes login succeeded earlier.
        Message req = new Message(MessageType.LOGOUT_REQUEST);
        req.tokenId = tokenId;
        return sendToTracker(req);
    }


    public Message inform(List<String> files) {
        // Inform the tracker which files this peer shares.
        Message req = new Message(MessageType.INFORM_REQUEST);
        req.tokenId = tokenId;
        req.clientInformFiles.addAll(files);
        req.clientPort = PeerSettings.peerServerPort;
        return sendToTracker(req);
    }


    public Message listFiles() {
        Message req = new Message(MessageType.LIST_REQUEST);
        return sendToTracker(req);
    }


    public Message details(String filename) {
        // Ask tracker for peers exposing this filename.
        Message req = new Message(MessageType.DETAILS_REQUEST);
        req.filename = filename;
        return sendToTracker(req);
    }


    private Message checkActive(String ip, int port) {
        // Ping another peer (directly) to see if it responds.
        // IMPORTANT: peers may listen on different ports, so use the peer's actual port.
        Message req = new Message(MessageType.CHECK_ACTIVE_REQUEST);
        req.clientIpAddress = ip;
        return send(req, ip, port);
    }


    public List<ContactInfoDTO> rankExposers(List<ContactInfoDTO> users) {

        // Score each peer by: (measured response time) * (reliability multiplier).
        // Lower score = better candidate for downloading.
        for (ContactInfoDTO user : users) {
            long start = System.currentTimeMillis();
            checkActive(user.getIp(), user.getPort());
            long responseTime = System.currentTimeMillis() - start;
            long score = (long) (responseTime * user.getScoreMultiplier());
            user.setScore(score);
        }

        users.sort(Comparator.comparingLong(ContactInfoDTO::getScore));
        return users;
    }


    public Message notifyDownload(String exposerUsername, String filename, boolean failed) {

        // Tell the tracker whether a download from exposerUsername succeeded or failed.
        // Tracker uses this to update the exposer's success/failure counters and, on success,
        // may add the downloader as a new exposer (because it now has a copy).
        Message req = new Message(MessageType.NOTIFY_REQUEST);
        req.foreignUsername = exposerUsername;
        req.tokenId = tokenId;
        req.filename = filename;
        req.statusCode = failed ? StatusCode.ERROR : StatusCode.SUCCESS;

        return sendToTracker(req);
    }


    public boolean downloadFromPeer(Message fileRequest) {

        // Save the downloaded file inside this peer's shared directory.
        String targetPath = PeerSettings.sharedDirectoryPath + File.separator + fileRequest.filename;
        File targetFile = new File(targetPath);

        // Send request directly to the selected peer (IP+port from ContactInfoDTO).
        Message resp = send(fileRequest, fileRequest.clientIpAddress, fileRequest.clientPort);

        // If the request failed or no file bytes were returned, treat it as a failed download.
        if (resp == null || resp.fileData == null) {
            notifyDownload(fileRequest.foreignUsername, fileRequest.filename, true);
            return false;
        }
        
        try {
            Files.write(targetFile.toPath(), resp.fileData);
            notifyDownload(fileRequest.foreignUsername, fileRequest.filename, false);
            return true;
        } catch (IOException e) {
            // Writing can fail even if the download succeeded (e.g., permission or disk error).
            notifyDownload(fileRequest.foreignUsername, fileRequest.filename, true);
            System.err.println("Failed to save downloaded file:");
            e.printStackTrace();
            return false;
        }
    }
}