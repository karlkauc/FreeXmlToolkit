package org.fxt.freexmltoolkit.service.ai.provider.anthropic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;

import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpClient;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpRequestBody;
import com.anthropic.core.http.HttpResponse;

/**
 * The Anthropic SDK's transport on the application's {@code ConnectionService} (decisions D6, D13): every request is
 * an {@link HttpURLConnection} from {@code openConnection}, so proxy, NTLM, timeouts and certificate settings apply,
 * and no OkHttp is involved. The read timeout is the provider's own (a model may think for minutes); a
 * {@link CancelToken} disconnects the running request from another thread.
 */
final class ConnectionServiceHttpClient implements HttpClient {

    /** Opens connections; {@code ConnectionService} in the application, a plain opener in tests. */
    @FunctionalInterface
    interface Opener {
        HttpURLConnection open(URI uri) throws IOException;
    }

    private final Opener opener;
    private final int readTimeoutMillis;
    private volatile CancelToken cancel = CancelToken.none();

    ConnectionServiceHttpClient(Opener opener, int readTimeoutSeconds) {
        this.opener = opener;
        this.readTimeoutMillis = readTimeoutSeconds * 1000;
    }

    /** The token of the request that runs next on this client. */
    void cancelWith(CancelToken token) {
        this.cancel = token == null ? CancelToken.none() : token;
    }

    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions options) {
        HttpURLConnection connection;
        try {
            connection = opener.open(URI.create(request.url()));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Runnable unregister = cancel.onCancel(connection::disconnect);
        try {
            connection.setReadTimeout(readTimeoutMillis);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(request.method().name());
            Headers headers = request.headers();
            for (String name : headers.names()) {
                List<String> values = headers.values(name);
                if (!values.isEmpty()) {
                    connection.setRequestProperty(name, String.join(", ", values));
                }
            }
            if (request.body().isPresent()) {
                HttpRequestBody body = request.body().get(); // NOPMD - owned by the request, closed by the SDK
                connection.setDoOutput(true);
                if (body.contentType() != null) {
                    connection.setRequestProperty("Content-Type", body.contentType());
                }
                if (body.contentLength() >= 0) {
                    connection.setFixedLengthStreamingMode(body.contentLength());
                }
                try (OutputStream out = connection.getOutputStream()) {
                    body.writeTo(out);
                }
            }
            int status = connection.getResponseCode();
            return new Response(connection, status, unregister);
        } catch (IOException e) {
            unregister.run();
            connection.disconnect();
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options) {
        return CompletableFuture.supplyAsync(() -> execute(request, options));
    }

    @Override
    public void close() {
        // connections are closed per response
    }

    private static final class Response implements HttpResponse {
        private final HttpURLConnection connection;
        private final int status;
        private final Runnable unregister;
        private final Headers headers;

        Response(HttpURLConnection connection, int status, Runnable unregister) {
            this.connection = connection;
            this.status = status;
            this.unregister = unregister;
            Headers.Builder builder = Headers.builder();
            for (Map.Entry<String, List<String>> e : connection.getHeaderFields().entrySet()) {
                if (e.getKey() != null) {
                    for (String value : e.getValue()) {
                        builder.put(e.getKey(), value);
                    }
                }
            }
            this.headers = builder.build();
        }

        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public Headers headers() {
            return headers;
        }

        @Override
        public InputStream body() {
            try {
                InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                return in != null ? in : InputStream.nullInputStream();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        @Override
        public void close() {
            unregister.run();
            connection.disconnect();
        }
    }
}
