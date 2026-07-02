package com.contentgrid.appserver.domain.uritemplate;


import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StringParser {

    private final String data;

    private int prevPosition = 0;
    private int position = 0;

    public boolean hasMore() {
        return position < data.length();
    }

    public char peek() {
        if (hasMore()) {
            return data.charAt(position);
        }
        return '\0';
    }

    private void updatePosition(int newPos) {
        prevPosition = position;
        position = newPos;
    }

    public boolean peekMatching(String matching) {
        return data.startsWith(matching, position);
    }

    public boolean consumeMatching(String matching) {
        if (peekMatching(matching)) {
            updatePosition(position + matching.length());
            return true;
        }
        return false;
    }

    public Optional<MatchResult> consumeMatching(NamedPattern pattern) {
        var matcher = pattern.matcher(data);
        if (matcher.find(position)) {
            updatePosition(matcher.end());
            return Optional.of(matcher.toMatchResult());
        }
        return Optional.empty();
    }

    public String consumeUntilBefore(String matching) throws InvalidUriTemplateException {
        var matchStart = data.indexOf(matching, position);
        if (matchStart < 0) {
            throw error("Expected '%s', but got EOF".formatted(matching));
        }
        var extracted = data.substring(position, matchStart);
        updatePosition(matchStart);
        return extracted;
    }

    public String consumeUntilBefore(NamedPattern pattern) throws InvalidUriTemplateException {
        var matcher = pattern.matcher(data);
        if (!matcher.find(position)) {
            throw error("Scanned for %s, but got EOF".formatted(pattern.name()));
        }
        var extracted = data.substring(position, matcher.start());
        updatePosition(matcher.start());
        return extracted;
    }

    public void swallow(String matching) throws InvalidUriTemplateException {
        if (!consumeMatching(matching)) {
            if (hasMore()) {
                throw error("Expected '%s', but got '%s'".formatted(matching, peek()));
            } else {
                throw error("Expected '%s', but got EOF".formatted(matching));
            }
        }
    }

    public InvalidUriTemplateException error(String message) {
        return new InvalidUriTemplateException(data, position, message);
    }

    public InvalidUriTemplateException errorPrevious(String message) {
        return new InvalidUriTemplateException(data, prevPosition, message);
    }

    public record NamedPattern(
            Pattern pattern,
            String name
    ) {

        public Matcher matcher(CharSequence input) {
            return pattern.matcher(input);
        }

        public static NamedPattern compile(String name, String pattern) {
            return new NamedPattern(Pattern.compile(pattern), name);
        }

        public static NamedPattern compile(String name, String pattern, int flags) {
            return new NamedPattern(Pattern.compile(pattern, flags), name);
        }

    }
}
