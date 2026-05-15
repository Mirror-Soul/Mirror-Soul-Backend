package com.mirrorsoul.mirrorsoul_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Evolve", description = "Evolve 관련 API")
@RestController
@RequestMapping("/evolve")
@RequiredArgsConstructor
public class EvolveController {

    @Operation(summary = '', description = '')
    @GetMapping("/{cloneUserUuid}")
}
