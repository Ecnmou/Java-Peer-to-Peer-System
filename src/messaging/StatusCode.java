package messaging;

public enum StatusCode {
    
    SUCCESS(200),
    ERROR(400);

    public final int code;

    StatusCode(int code) {
        this.code = code;
    }
}