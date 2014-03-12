package download.management;

public class UnauthorizedException extends Exception {
    public UnauthorizedException(int code) {
        super("Unauthorized Exception. Http response code: " + code);
    }
}