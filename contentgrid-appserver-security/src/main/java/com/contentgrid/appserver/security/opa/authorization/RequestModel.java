package com.contentgrid.appserver.security.opa.authorization;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import lombok.Value;

@Value
public class RequestModel {

    String method;
    List<String> path;
    Map<String, List<String>> query;
    Map<String, List<String>> headers;

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
                // Parsed from the raw query string rather than getParameterMap(), which would
                // consume the body for application/x-www-form-urlencoded requests
                queryParams(request.getQueryString()),
                headers
        );
    }

    private static Map<String, List<String>> queryParams(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return Map.of();
        }

        var result = new LinkedHashMap<String, List<String>>();
        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            var separatorIndex = pair.indexOf('=');
            var key = separatorIndex >= 0 ? pair.substring(0, separatorIndex) : pair;
            var value = separatorIndex >= 0 ? pair.substring(separatorIndex + 1) : "";
            key = URLDecoder.decode(key, StandardCharsets.UTF_8);
            value = URLDecoder.decode(value, StandardCharsets.UTF_8);
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return result;
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
