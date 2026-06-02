package com.reciapp.api.controller;

import com.reciapp.api.service.ClipService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/clip")
public class ClipController {

    private final ClipService clipService;

    public ClipController(ClipService clipService) {
        this.clipService = clipService;
    }

    @PostMapping(value = "/match", produces = "application/json")
    public String match(@RequestBody Map<String, String> body) {
        return clipService.match(body.get("image"));
    }
}
