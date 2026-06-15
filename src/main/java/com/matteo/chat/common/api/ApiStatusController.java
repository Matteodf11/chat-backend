package com.matteo.chat.common.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
class ApiStatusController {

	@GetMapping
	ApiStatusResponse status() {
		return new ApiStatusResponse("ok", Instant.now());
	}

	record ApiStatusResponse(String status, Instant timestamp) {
	}
}
