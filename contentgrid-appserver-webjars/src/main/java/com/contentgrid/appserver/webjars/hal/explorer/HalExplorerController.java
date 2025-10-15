package com.contentgrid.appserver.webjars.hal.explorer;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The controller redirects the old hal-explorer uris from spring-data-rest
 * to the new hal-explorer uri.
 */
@RestController
public class HalExplorerController {

    @GetMapping(value = {"/", ""}, produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<Void> getRoot(HttpServletRequest request) {
        return getRedirectResponse(request);
    }

    @GetMapping("/explorer")
    ResponseEntity<Void> getExplorerRoot(HttpServletRequest request) {
        return getRedirectResponse(request);
    }

    @GetMapping("/explorer/index.html")
    ResponseEntity<Void> getExplorerIndexHtml(HttpServletRequest request) {
        return getRedirectResponse(request);
    }

    private ResponseEntity<Void> getRedirectResponse(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(getHalExplorerUri(request))
                .build();
    }

    private URI getHalExplorerUri(HttpServletRequest request) {
        var builder = ServletUriComponentsBuilder.fromRequest(request);

        builder.replacePath("/webjars/hal-explorer/index.html");
        builder.fragment("uri=%s".formatted("/"));

        return builder.build().toUri();
    }

}
