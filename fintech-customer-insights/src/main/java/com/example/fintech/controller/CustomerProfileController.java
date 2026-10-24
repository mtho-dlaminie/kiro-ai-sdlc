package com.example.fintech.controller;

import com.example.fintech.dto.CustomerProfileResponse;
import com.example.fintech.dto.DtoMapper;
import com.example.fintech.service.CustomerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/{customerId}")
@Validated
public class CustomerProfileController {

    private final CustomerProfileService profileService;

    public CustomerProfileController(CustomerProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "Get customer profile",
            description = "Returns the synthetic profile for a customer. Data is synthetic and does not "
                    + "represent a real person.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile found"),
            @ApiResponse(responseCode = "400", description = "Malformed customer id",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Unknown customer",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Dependency unavailable",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
    })
    @GetMapping(value = "/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public CustomerProfileResponse getProfile(
            @PathVariable @NotBlank @Pattern(regexp = "^CUST-\\d{6}$",
                    message = "customerId must match ^CUST-\\d{6}$") String customerId) {
        return DtoMapper.toProfile(profileService.getProfile(customerId));
    }
}
