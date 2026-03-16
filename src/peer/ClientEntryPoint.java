package peer;

import java.io.File;
import java.util.Scanner;

public class ClientEntryPoint {
    public static void main(String[] args) {

        // parse args: tracker-ip, tracker-port, my-port, shared-dir
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "tracker-ip"   -> PeerSettings.trackerIP = args[i + 1];
                case "tracker-port" -> PeerSettings.trackerPort = Integer.parseInt(args[i + 1]);
                case "my-port"      -> PeerSettings.peerServerPort = Integer.parseInt(args[i + 1]);
                case "shared-dir"   -> PeerSettings.sharedDirectoryPath = args[i + 1];
                default -> {}
            }
        }

        // ensure shared directory exists for this peer
        File sharedDir = new File(PeerSettings.sharedDirectoryPath);
        if (!sharedDir.exists()) {
            sharedDir.mkdirs();
        }

        Scanner sc = new Scanner(System.in);
        Peer peer = new Peer();
        PeerListener listener = new PeerListener();
        PeerController controller = new PeerController(peer, sc);

        new Thread(listener::start).start();

        System.out.println("Hello.");
        int choice = 0;

        while (choice != 1 && choice != 2) {
            System.out.println("Type 1 to Register");
            System.out.println("Type 2 to Login");
            while (!sc.hasNextInt()) {
                System.out.println("Please enter 1 or 2.");
                sc.next();
            }
            choice = sc.nextInt();
            sc.nextLine();
        }

        if (choice == 1) {
            controller.register();
            System.out.println("\nYou can now login.");
        }

        if (!controller.login()) {
            System.out.println("Login failed. Exiting.");
            return;
        }

        System.out.println("Welcome.");
        controller.informTracker();
        while (true) {
            System.out.println("""
                    
                    Type "list" to see available files
                    Type "details" to see who exposes a file
                    Type "download" to download a file
                    Type "logout" to leave the system
                    
                    """);
            String cmd = sc.nextLine().trim();
            while (!cmd.equals("list") && !cmd.equals("details")
                    && !cmd.equals("download") && !cmd.equals("logout")) {
                System.out.println("Please type the word correctly.");
                cmd = sc.nextLine().trim();
            }
            
            switch (cmd) {
                case "list" -> controller.listFiles();
                case "details" -> controller.showDetails();
                case "download" -> controller.simpleDownload();
                case "logout" -> {
                    controller.logout();
                    System.out.println("Goodbye.");
                    return;
                }
            }
        }
    }
}