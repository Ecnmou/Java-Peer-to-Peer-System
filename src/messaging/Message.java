package messaging;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import model.ContactInfoDTO;


public class Message implements Serializable {

    public MessageType type;

    // tracker auth / session
    public String username = "";
    public String password = "";
    public String tokenId = "";

    // status
    public StatusCode statusCode = StatusCode.SUCCESS;
    public String statusDescription = "";

    // networking info
    public String clientIpAddress = "";
    public int clientPort = 0;

    // file-sharing info
    public String filename = "";
    public byte[] fileData;

    // inform/list
    public ArrayList<String> clientInformFiles = new ArrayList<>();
    public ArrayList<String> availableFileNames = new ArrayList<>();

    // details: active peers exposing a file
    public List<ContactInfoDTO> users;

    // notify: user we downloaded from / failed to download
    public String foreignUsername = "";
    
    public Message(MessageType type) {
        this.type = type;
    }
}