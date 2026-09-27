package com.apiscope.core.api;

import com.apiscope.core.scanner.ApiEndpointMetadata;
import com.apiscope.core.scanner.EndpointRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/apiscope/api")
public class EndpointsController {

    private final EndpointRepository endpoints;

    public EndpointsController(EndpointRepository endpoints) {
        this.endpoints = endpoints;
    }

    @GetMapping("/endpoints")
    public ResponseEntity<List<ApiEndpointMetadata>> listEndpoints() {
        return ResponseEntity.ok(endpoints.getScannedEndpoints());
    }
}
