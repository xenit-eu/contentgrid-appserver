package com.contentgrid.appserver.rest.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.function.UnaryOperator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures that only a single HTTP {@code Range} request is present.
 * <p>
 * This is necessary because content access only supports using a single byte-range for access.
 * Allowing multiple Range values present here can give malicious clients a possibility to perform denial of service,
 * by requesting many very small ranges. This would cause either a lot of separate requests to the content store to fetch all those distinct ranges,
 * or require fetching a single potentially-large range to serve all the requested small ranges.
 * <p>
 * When multiple byte-ranges are not in a nice, increasing order, Spring itself will re-fetch the InputStream (and thus the content) for every out-of-order chunk,
 * Resulting in potentially many large requests to the content store.
 * <p>
 * Unfortunately, there is no way to easily escape this handling by Spring without giving up on all the niceties it gives us to handle resource responses.
 * <p>
 * We still want to be somewhat nice to proper clients that request multiple ranges, so we manipulate the request to appear to only have a single range request.
 * As per <a href="https://www.rfc-editor.org/rfc/rfc9110.html#status.206">RFC9110</a>, the server may only partially satisfy the range requests by the client.
 * Since there is no way to coalesce the byte-ranges to one range (because we don't know the content length, which is necessary to handle suffix ranges),
 * we choose to only serve the first range that the client requested.
 */
@Component
public class SingleRangeRequestServletFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getHeader(HttpHeaders.RANGE) != null) {
            filterChain.doFilter(new RangeModificationHttpServletRequestWrapper(request), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }


    private static class RangeModificationHttpServletRequestWrapper extends HttpServletRequestWrapper {

        public RangeModificationHttpServletRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            var header = super.getHeader(name);
            if (header == null) {
                return null;
            }
            if (!isRangeHeader(name)) {
                return header;
            }
            return modifyHeader(header);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            var headers = super.getHeaders(name);
            if (headers == null) {
                return null;
            }
            if (!isRangeHeader(name)) {
                return headers;
            }
            return new MappingEnumeration<>(headers, this::modifyHeader);
        }

        private boolean isRangeHeader(String headerName) {
            return HttpHeaders.RANGE.equalsIgnoreCase(headerName);
        }

        private String modifyHeader(String contents) {
            try {
                var ranges = HttpRange.parseRanges(contents);
                if (ranges.isEmpty()) {
                    return contents;
                }
                return HttpRange.toString(List.of(ranges.get(0)));
            } catch (IllegalArgumentException exception) {
                // When we can't parse the range here, make it a problem of whoever consumes it later.
                // This is fine, because Spring will parse again it before sending over byte-ranges,
                // and will have to handle invalid ranges there
                return contents;
            }
        }

    }

    @RequiredArgsConstructor
    private static class MappingEnumeration<T> implements Enumeration<T> {

        @NonNull
        private final Enumeration<T> source;
        @NonNull
        private final UnaryOperator<T> mapper;

        @Override
        public boolean hasMoreElements() {
            return source.hasMoreElements();
        }

        @Override
        public T nextElement() {
            return mapper.apply(source.nextElement());
        }
    }
}
