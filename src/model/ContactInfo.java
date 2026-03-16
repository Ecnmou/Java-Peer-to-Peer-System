package model;

import java.util.List;

public class ContactInfo {

    private final Account account;
    private final String tokenId;
    private String ip;
    private int port;

    public ContactInfo(Account account, String tokenId, String ip, int port) {
        this.account = account;
        this.tokenId = tokenId;
        this.ip = ip;
        this.port = port;
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getIp() {
        return ip;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }

    // methods about the contained account instance
    public String getUsername() {
        return account.getUsername();
    }

    public List<String> getSharedFiles() {
        return account.getSharedFiles();
    }
    public void setSharedFiles(List<String> files) {
        account.setSharedFiles(files);
    }

    public int getDownloadCount() {
        return account.getDownloadCount();
    }
    public int getFailureCount() {
        return account.getFailureCount();
    }

    public void incrementDownloads() {
        account.incrementDownloads();
    }
    public void incrementFailures() {
        account.incrementFailures();
    }
    //-------------------------------------------------

    public double calculateMultiplier() {
        return Math.pow(0.75, getDownloadCount()) * Math.pow(1.25, getFailureCount());
    }

    // Convert to data transfer object
    public ContactInfoDTO toDTO() {
        ContactInfoDTO dto = new ContactInfoDTO();
        dto.setUsername(getUsername());
        dto.setIp(getIp());
        dto.setPort(getPort());
        dto.setScoreMultiplier(calculateMultiplier());
        return dto;
    }
}