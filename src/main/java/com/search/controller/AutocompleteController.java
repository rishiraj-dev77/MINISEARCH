package com.search.controller;

import com.search.model.Suggestion;
import com.search.service.AutocompleteService;

import java.util.List;

/**
 * Controller for autocomplete requests. Delegates to {@link AutocompleteService};
 * the controller layer holds no data-structure references.
 */
public class AutocompleteController {

    private final AutocompleteService autocompleteService;

    public AutocompleteController(AutocompleteService autocompleteService) {
        this.autocompleteService = autocompleteService;
    }

    public List<Suggestion> suggest(String prefix, int k) {
        return autocompleteService.suggest(prefix, k);
    }

    public List<Suggestion> suggest(String prefix) {
        return autocompleteService.suggest(prefix);
    }
}
