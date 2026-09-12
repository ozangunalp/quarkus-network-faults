package io.quarkiverse.networkfaults.spi;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.networkfaults.spi.AddressRewriter.Context;

/**
 * Parsing and rebuilding pieces for {@link AddressRewriter} implementations.
 */
public final class AddressUtil {

    /**
     * Every proxy listens here.
     */
    public static final String PROXY_HOST = "127.0.0.1";

    // Schemes whose upstream speaks plaintext HTTP/1.x, which the proxy can read; https and wss reach it encrypted.
    private static final Set<String> HTTP_SCHEMES = Set.of("http", "ws");

    /**
     * Rewrites one element of a comma-separated value.
     */
    @FunctionalInterface
    public interface ElementRewriter {
        String rewrite(String element, Context context);
    }

    public record HostPort(String userInfo, String host, int port) {
        public String userInfoPrefix() {
            return userInfo == null ? "" : userInfo + "@";
        }

        public Upstream upstream() {
            return Upstream.of(host, port);
        }
    }

    private AddressUtil() {
    }

    /**
     * Rewrites each element of a comma-separated list; the whole value is unproxied if any element is.
     */
    public static String rewriteList(String value, Context context, ElementRewriter element) {
        String[] parts = value.split(",");
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            String part = p.trim();
            if (part.isEmpty()) {
                continue;
            }
            String rewritten = element.rewrite(part, context);
            if (rewritten == null) {
                return null;
            }
            out.add(rewritten);
        }
        return out.isEmpty() ? null : String.join(",", out);
    }

    /**
     * Rewrites a {@code scheme://[user@]host[:port]/...} URL, taking the port from {@code defaultPorts} by scheme
     * when the URL has none; {@code null} for a URL without a host or with an unknown default. An {@code http} or
     * {@code ws} URL is an HTTP upstream; an {@code https} or {@code wss} one is not, since the proxy relays TLS
     * without reading it.
     */
    public static String rewriteUrl(String url, Context context, Map<String, Integer> defaultPorts) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return null;
        }
        if (uri.getHost() == null || uri.getScheme() == null) {
            return null;
        }
        int port = uri.getPort();
        if (port < 0) {
            port = defaultPorts.getOrDefault(uri.getScheme().toLowerCase(), -1);
            if (port < 0) {
                return null;
            }
        }
        boolean http = HTTP_SCHEMES.contains(uri.getScheme().toLowerCase());
        int proxyPort = context.proxyPort(new Upstream(uri.getHost(), port, http));
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), PROXY_HOST, proxyPort, uri.getPath(), uri.getQuery(),
                    uri.getFragment()).toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Splits {@code [user@]host[:port]}, IPv6 literals included; {@code null} when there is no host or port.
     */
    public static HostPort hostPort(String authority, int defaultPort) {
        String userInfo = null;
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            userInfo = authority.substring(0, at);
            authority = authority.substring(at + 1);
        }
        String host;
        int port;
        if (authority.startsWith("[")) {
            int end = authority.indexOf(']');
            if (end < 0) {
                return null;
            }
            host = authority.substring(0, end + 1);
            String p = authority.substring(end + 1);
            port = p.startsWith(":") ? parsePort(p.substring(1)) : defaultPort;
        } else {
            int colon = authority.lastIndexOf(':');
            if (colon >= 0) {
                host = authority.substring(0, colon);
                port = parsePort(authority.substring(colon + 1));
            } else {
                host = authority;
                port = defaultPort;
            }
        }
        if (host.isEmpty() || port <= 0) {
            return null;
        }
        return new HostPort(userInfo, host, port);
    }

    private static int parsePort(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
