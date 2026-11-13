package com.example.reporting.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** Body of {@code POST /api/v1/report-runs}. */
public final class StartRunRequest {

    @NotBlank
    @Pattern(regexp = "[A-Z]{2}", message = "must be a two-letter upper-case state code")
    private final String stateCode;

    @Size(max = 500)
    private final List<@Pattern(regexp = "\\d{5}", message = "must have five digits") String> fipsCodes;

    /**
     * Creates the request; called by Jackson.
     *
     * @param stateCode state code, e.g. VA
     * @param fipsCodes optional FIPS filter
     */
    @JsonCreator
    public StartRunRequest(@JsonProperty("stateCode") String stateCode,
            @JsonProperty("fipsCodes") List<String> fipsCodes) {
        this.stateCode = stateCode;
        this.fipsCodes = fipsCodes == null ? List.of() : List.copyOf(fipsCodes);
    }

    public String getStateCode() {
        return stateCode;
    }

    public List<String> getFipsCodes() {
        return fipsCodes;
    }
}
