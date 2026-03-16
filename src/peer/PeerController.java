package peer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import messaging.*;
import model.ContactInfoDTO;


public class PeerController {

    // High-level orchestrator for user interactions; delegates network work to Peer.
    private final Peer peer;

    private final Scanner sc;

    public PeerController(Peer peer, Scanner sc) {
        this.peer = peer;
        this.sc = sc;
    }


    // Scan this peer's shared directory and collect file names.
    private List<String> findSharedFiles() {

        ArrayList<String> shared = new ArrayList<>();

        File folder = new File(PeerSettings.sharedDirectoryPath);
        File[] files = folder.listFiles();

        if (files == null) {
            // Directory missing or unreadable -> behave as no files.
            return shared;
        }

        for (File f : files) {
            if (f.isFile()) {
                shared.add(f.getName());
            }
        }
        return shared;
    }


    public boolean register() {

        // Allow up to 3 attempts for a successful registration.
        for (int i = 0; i < 3; i++) {
            System.out.print("Choose username: ");
            String username = sc.nextLine();
            System.out.print("Choose password: ");
            String password = sc.nextLine();

            Message resp = peer.register(username, password);
            if (resp != null && resp.statusCode == StatusCode.SUCCESS) {
                System.out.println(resp.statusDescription);
                return true;
            }
            System.out.println(resp != null ? resp.statusDescription : "Network error.");
        }

        System.out.println("Registration failed 3 times.");
        return false;
    }


    public boolean login() {

        // Up to 3 login attempts.
        for (int i = 0; i < 3; i++) {
            System.out.print("Username: ");
            String username = sc.nextLine();
            System.out.print("Password: ");
            String password = sc.nextLine();

            Message resp = peer.login(username, password);
            if (resp != null && resp.statusCode == StatusCode.SUCCESS) {
                System.out.println(resp.statusDescription);
                return true;
            }
            System.out.println(resp != null ? resp.statusDescription : "Network error.");
        }
        return false;
    }


    public boolean logout() {

        // tokenID is on the Peer

        Message resp = peer.logout();
        if (resp != null && resp.statusCode == StatusCode.SUCCESS) {
            System.out.println(resp.statusDescription);
            return true;
        }
        System.out.println(resp != null ? resp.statusDescription : "Network error.");
        return false;
    }


    public boolean informTracker() {
        // Tell tracker which files we currently share.
        List<String> files = findSharedFiles();
        Message resp = peer.inform(files);

        if (resp != null) {
            System.out.println(resp.statusDescription);
            return resp.statusCode == StatusCode.SUCCESS;
        }

        System.out.println("Network error while informing tracker.");
        return false;
    }


    public boolean listFiles() {

        Message resp = peer.listFiles();

        if (resp != null && resp.statusCode == StatusCode.SUCCESS) {
            if (resp.availableFileNames.isEmpty()) {
                System.out.println("No files available.");
            } else {
                for (String name : resp.availableFileNames) {
                    System.out.println(name);
                }
            }
            return true;
        }
        
        System.out.println(resp != null ? resp.statusDescription : "Network error.");
        return false;
    }


    public boolean showDetails() {

        System.out.print("Filename: ");
        String filename = sc.nextLine();

        Message resp = peer.details(filename);
        if (resp == null) {
            System.out.println("Network error.");
            return false;
        }

        System.out.println(resp.statusDescription);
        if (resp.users != null) {
            // Print each peer that exposes this file, with address info.
            for (ContactInfoDTO dto : resp.users) {
                System.out.println(dto.getUsername() + " @ " + dto.getIp() + ":" + dto.getPort());
            }
        }
        return resp.statusCode == StatusCode.SUCCESS;
    }


    public void simpleDownload() {
        
        System.out.print("Filename to download: ");
        String filename = sc.nextLine();

        // First ask tracker who exposes this file.
        Message resp = peer.details(filename);
        if (resp == null || resp.users == null || resp.users.isEmpty()) {
            System.out.println(resp != null ? resp.statusDescription : "Network error.");
            return;
        }

        // Rank exposers by best candidate (fastest & most reliable).
        List<ContactInfoDTO> ranked = peer.rankExposers(resp.users);
        Message fileReq = new Message(MessageType.SIMPLE_DOWNLOAD_REQUEST);
        fileReq.filename = filename;
        boolean success = false;

        // Try each exposer in ranked order until one succeeds.
        for (ContactInfoDTO exposer : ranked) {
            fileReq.clientIpAddress = exposer.getIp();
            fileReq.clientPort = exposer.getPort();
            fileReq.foreignUsername = exposer.getUsername();

            if (peer.downloadFromPeer(fileReq)) {
                System.out.println("File downloaded successfully.");
                success = true;
                break;
            }
        }
        
        if (!success) {
            System.out.println("Download failed from all peers.");
        }
    }
}