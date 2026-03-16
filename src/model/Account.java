package model;

import java.util.ArrayList;
import java.util.List;

public class Account {

    private final String username;
    private final String password;

    private final List<String> sharedFiles = new ArrayList<>();

    private int downloadCount = 0;
    private int failureCount = 0;

    public Account(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public boolean passwordMatches(String candidate) {
        return password.equals(candidate);
    }


    public synchronized void setSharedFiles(List<String> files) {
        sharedFiles.clear();
        sharedFiles.addAll(files);
    }

    public synchronized List<String> getSharedFiles() {
        return new ArrayList<>(sharedFiles);
    }


    public synchronized void incrementDownloads() {
        downloadCount++;
    }

    public synchronized void incrementFailures() {
        failureCount++;
    }

    
    public synchronized int getDownloadCount() {
        return downloadCount;
    }

    public synchronized int getFailureCount() {
        return failureCount;
    }
}