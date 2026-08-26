package dev.shirwac.incidentdetective.rag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RunbookCorpus(
        @NotBlank String corpusVersion,
        @NotNull @Size(min = 10, max = 20)
        List<@NotNull @Valid RunbookCorpusEntry> entries
) {
    public RunbookCorpus {
        entries = entries == null ? null : List.copyOf(entries);
    }
}
