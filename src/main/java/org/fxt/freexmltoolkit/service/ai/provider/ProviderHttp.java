package org.fxt.freexmltoolkit.service.ai.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.fxt.freexmltoolkit.service.ConnectionServiceImpl;

/**
 * HTTP plumbing shared by the providers. Every connection comes from {@code ConnectionService.openConnection}
 * (proxy, NTLM, certificates, decision D6); the provider's own read timeout replaces the general one, because a
 * local model may take minutes before its first token. A {@link CancelToken} disconnects from another thread.
 */
final class ProviderHttp {

    /** Opens connections; the application's {@code ConnectionService} unless a test supplies another. */
    @FunctionalInterface
    interface Opener {
        HttpURLConnection open(URI uri) throws IOException;
    }

    static Opener defaultOpener() {
        return uri -> ConnectionServiceImpl.getInstance().openConnection(uri);
    }

    private static final int MAX_ERROR_BODY = 16 * 1024;

    private ProviderHttp() {
    }

    /** Sends a request (POST with a JSON body, or GET when {@code json} is null) and returns the open connection. */
    static HttpURLConnection send(Opener opener, URI uri, String json, Map<String, String> headers,
                                  int readTimeoutSeconds, CancelToken cancel) throws ProviderException {
        cancel.throwIfCancelled();
        HttpURLConnection connection;
        try {
            connection = opener.open(uri);
        } catch (IOException e) {
            throw unreachable(e);
        }
        Runnable unregister = cancel.onCancel(connection::disconnect);
        try {
            connection.setReadTimeout(readTimeoutSeconds * 1000);
            connection.setInstanceFollowRedirects(false);
            headers.forEach(connection::setRequestProperty);
            if (json != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }
            } else {
                connection.setRequestMethod("GET");
            }
            connection.getResponseCode();
            return connection;
        } catch (IOException e) {
            unregister.run();
            connection.disconnect();
            throw translate(e, cancel);
        }
    }

    /** The error body of a failed response (bounded), or "". */
    static String errorBody(HttpURLConnection connection) {
        try (InputStream in = connection.getErrorStream()) {
            if (in == null) {
                return "";
            }
            return new String(in.readNBytes(MAX_ERROR_BODY), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** {@code Retry-After} in seconds, or -1. */
    static int retryAfter(HttpURLConnection connection) {
        String value = connection.getHeaderField("Retry-After");
        if (value == null) {
            return -1;
        }
        try {
            return (int) Math.ceil(Double.parseDouble(value.strip()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** An I/O failure as a provider failure. */
    static ProviderException translate(IOException e, CancelToken cancel) {
        if (cancel.isCancelled()) {
            return new ProviderException(ProviderException.Kind.CANCELLED,
                    ProviderException.describe(ProviderException.Kind.CANCELLED), -1, -1, null, e);
        }
        if (e instanceof SocketTimeoutException) {
            return new ProviderException(ProviderException.Kind.TIMEOUT,
                    ProviderException.describe(ProviderException.Kind.TIMEOUT), -1, -1, null, e);
        }
        if (e instanceof ConnectException || e instanceof UnknownHostException || e instanceof NoRouteToHostException) {
            return unreachable(e);
        }
        return new ProviderException(ProviderException.Kind.PROTOCOL, "The connection to the provider broke ("
                + e.getClass().getSimpleName() + ").", -1, -1, null, e);
    }

    private static ProviderException unreachable(IOException e) {
        return new ProviderException(ProviderException.Kind.UNREACHABLE,
                ProviderException.describe(ProviderException.Kind.UNREACHABLE) + " (" + e.getClass().getSimpleName()
                        + ")", -1, -1, null, e);
    }

    /** The base URL joined with a path ({@code /chat/completions}). */
    static URI endpoint(String baseUrl, String path) throws ProviderException {
        try {
            return URI.create(baseUrl.replaceAll("/+$", "") + path);
        } catch (IllegalArgumentException e) {
            throw new ProviderException(ProviderException.Kind.BAD_REQUEST, "The base URL is not valid."); // NOPMD - the cause quotes the host; never in logs (AI rules)
        }
    }
}
