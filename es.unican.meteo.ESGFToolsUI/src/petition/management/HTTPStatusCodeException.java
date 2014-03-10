package petition.management;

public class HTTPStatusCodeException extends Exception {
    public HTTPStatusCodeException(int statusCode) {
        super("HTTP request isn't successful. Status code :" + statusCode);
    }
}
