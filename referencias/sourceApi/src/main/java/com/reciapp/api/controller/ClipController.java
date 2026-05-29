package com.reciapp.api.controller;

import com.reciapp.api.service.ClipService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clip")
public class ClipController {

    private final ClipService clipService;

    public ClipController(ClipService clipService) {
        this.clipService = clipService;
    }

    @PostMapping("/match")
    public List<Map<String, Object>> match(@RequestBody Map<String, String> body) {
        return clipService.match(body.get("image"));
    }
}
