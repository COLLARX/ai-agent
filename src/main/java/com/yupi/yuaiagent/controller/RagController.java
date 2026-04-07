package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthContext;
import com.yupi.yuaiagent.auth.AuthenticatedUser;
import com.yupi.yuaiagent.rag.RagKnowledgeIngestService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/rag")
public class RagController {

    private final RagKnowledgeIngestService ragKnowledgeIngestService;

    public RagController(RagKnowledgeIngestService ragKnowledgeIngestService) {
        this.ragKnowledgeIngestService = ragKnowledgeIngestService;
    }

    @PostMapping(value = "/upload-md", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RagKnowledgeIngestService.UploadResult uploadMarkdown(@RequestParam("file") MultipartFile file) {
        AuthenticatedUser currentUser = AuthContext.requireCurrentUser();
        try {
            return ragKnowledgeIngestService.ingestMarkdown(file, currentUser.id());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
        }
    }
}
