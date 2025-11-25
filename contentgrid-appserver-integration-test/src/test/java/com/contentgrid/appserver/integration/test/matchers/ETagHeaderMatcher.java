package com.contentgrid.appserver.integration.test.matchers;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.contentgrid.appserver.domain.values.version.ExactlyVersion;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultMatcher;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ETagHeaderMatcher {

    public record ETag(@NonNull String value) {

        @Override
        public String toString() {
            return "\"%s\"".formatted(value);
        }
    }

    public static ETag toETag(ExactlyVersion version) {
        return new ETag(version.getVersion());
    }

    public ResultMatcher exists() {
        return header().exists(HttpHeaders.ETAG);
    }

    public ResultMatcher isEqualTo(ETag expected) {
        return header().string(HttpHeaders.ETAG, expected.toString());
    }

    public ResultMatcher isNotEqualTo(ETag notExpected) {
        var matcher = not(notExpected.toString());
        return header().string(HttpHeaders.ETAG, matcher);
    }
}
