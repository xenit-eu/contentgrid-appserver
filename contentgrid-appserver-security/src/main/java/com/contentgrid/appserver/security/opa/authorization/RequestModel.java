package com.contentgrid.appserver.security.opa.authorization;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public record RequestModel(String method, List<String> path, Map<String, List<String>> headers) {

    public static RequestModel from(HttpServletRequest request) {
        var headers = new TreeMap<String, List<String>>();
        var headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            var name = headerNames.nextElement();
            headers.put(name.toLowerCase(Locale.ROOT), Collections.list(request.getHeaders(name)));
        }

        return new RequestModel(
                request.getMethod(),
                uriToPathList(URI.create(request.getRequestURI())),
                headers
        );
    }

    private static List<String> uriToPathList(URI uri) {
        uri = uri.normalize();

        var path = uri.getPath();
        if (path == null) {
            return List.of();
        }

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (path.isEmpty()) {
            return List.of();
        }

        return List.of(path.split("/"));
    }
}
