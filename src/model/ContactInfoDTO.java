package model;

import java.io.Serializable;

// DTO = Data Transfer Object
public class ContactInfoDTO implements Serializable {

    private String username;
    private String ip;
    private int port;
    private double scoreMultiplier;
    private long score;


    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
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


    public double getScoreMultiplier() {
        return scoreMultiplier;
    }
    public void setScoreMultiplier(double scoreMultiplier) {
        this.scoreMultiplier = scoreMultiplier;
    }
    

    public long getScore() {
        return score;
    }
    public void setScore(long score) {
        this.score = score;
    }
}