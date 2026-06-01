package com.apiscope.core.chat;

import com.apiscope.core.ingestor.ApiDocumentIngestor;
import com.apiscope.core.scanner.ApiEndpointMetadata;
import com.apiscope.core.scanner.EndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Handles endpoint listing and admin reindex. */
@RestController
@RequestMapping("/apiscope/api")
public class EndpointController {

    private static final Logger log = LoggerFactory.getLogger(EndpointController.class);

    private final EndpointRepository endpointRepository;
    private final ApiDocumentIngestor ingestor;

    public EndpointController(EndpointRepository endpointRepository, ApiDocumentIngestor ingestor) {
        this.endpointRepository = endpointRepository;
        this.ingestor           = ingestor;
    }

    @GetMapping("/endpoints")
    public ResponseEntity<List<ApiEndpointMetadata>> listEndpoints() {
        return ResponseEntity.ok(endpointRepository.getScannedEndpoints());
    }

    @PostMapping("/admin/reindex")
    public ResponseEntity<Void> reindex() {
        log.info("[APIScope] Manual reindex triggered.");
        ingestor.reindex(endpointRepository.getScannedEndpoints());
        return ResponseEntity.accepted().build();
    }
}
