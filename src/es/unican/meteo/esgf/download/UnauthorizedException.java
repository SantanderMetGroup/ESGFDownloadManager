package es.unican.meteo.esgf.download;

/**
 * Thrown when try to access a file in ESGF data node and user hasn't
 * permissions.
 * 
 * @author Karem Terry
 * 
 */
public class UnauthorizedException extends Exception {
    public UnauthorizedException(int code) {
        super("Unauthorized Exception. Http response code: " + code);
    }
}
