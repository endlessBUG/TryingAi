package com.ai.trainer.controller;

import com.ai.trainer.model.ComfyuiWorkflow;
import com.ai.trainer.service.ComfyuiService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comfyui")
@RequiredArgsConstructor
public class ComfyuiController {

    private final ComfyuiService comfyuiService;

    @GetMapping("/workflows")
    public List<ComfyuiWorkflow> listWorkflows() {
        return comfyuiService.getAllWorkflows();
    }

    @GetMapping("/workflows/{id}")
    public ComfyuiWorkflow getWorkflow(@PathVariable String id) {
        return comfyuiService.getWorkflow(id);
    }

    @PostMapping("/workflows")
    public ComfyuiWorkflow createWorkflow(@RequestBody ComfyuiWorkflow workflow) {
        return comfyuiService.saveWorkflow(workflow);
    }

    @PutMapping("/workflows/{id}")
    public ComfyuiWorkflow updateWorkflow(@PathVariable String id, @RequestBody ComfyuiWorkflow workflow) {
        workflow.setId(id);
        return comfyuiService.saveWorkflow(workflow);
    }

    @DeleteMapping("/workflows/{id}")
    public void deleteWorkflow(@PathVariable String id) {
        comfyuiService.deleteWorkflow(id);
    }

    @PostMapping("/workflows/{id}/execute")
    public JsonNode executeWorkflow(@PathVariable String id, @RequestBody(required = false) Map<String, Object> params) throws Exception {
        return comfyuiService.executeWorkflow(id, params);
    }

    @GetMapping("/node-info")
    public JsonNode getNodeInfo() throws Exception {
        return comfyuiService.getNodeInfo();
    }

    @GetMapping("/history/{promptId}")
    public JsonNode getHistory(@PathVariable String promptId) throws Exception {
        return comfyuiService.getHistory(promptId);
    }

    @GetMapping(value = "/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getImage(
            @RequestParam String filename,
            @RequestParam(required = false) String subfolder,
            @RequestParam(required = false) String type) throws Exception {
        byte[] image = comfyuiService.getImage(filename, subfolder, type);
        return ResponseEntity.ok(image);
    }
}