package es.unican.meteo.esgf.download;

public class UnauthorizedException extends Exception {
    public UnauthorizedException(int code) {
        super("Unauthorized Exception. Http response code: " + code);
    }
}
