package peer;

public class PeerSettings {
    
    // Tracker (central server) settings
    public static String trackerIP = "localhost"; // replace with given Tracker IP *not local IP*
    public static int trackerPort = 9090;

    // Per-peer server port (each peer listens here)
    public static int peerServerPort = 9091;

    // Folder where this peer reads/writes shared files
    public static String sharedDirectoryPath = "peer_shared_files\\shared_files_peer_1";
}