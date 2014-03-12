package es.unican.meteo.esgf.petition;

public class HTTPStatusCodeException extends Exception {
    public HTTPStatusCodeException(int statusCode) {
        super("HTTP request isn't successful. Status code :" + statusCode);
    }
}
